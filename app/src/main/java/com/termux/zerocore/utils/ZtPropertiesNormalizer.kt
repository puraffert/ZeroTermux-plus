package com.termux.zerocore.utils

import android.util.Log
import com.termux.shared.termux.TermuxConstants
import java.io.File

/**
 * Normalizes ~/.termux/termux.properties at startup:
 * - drops duplicate keys (last occurrence wins, same as java.util.Properties.load)
 * - repairs the malformed single-line default ("extra-keys = [...]" wrapped in literal quotes)
 * Rewrites the file only when something actually changed.
 */
object ZtPropertiesNormalizer {
    private const val TAG = "ZtPropNormalizer"

    @JvmStatic
    fun normalize() {
        try {
            val f = File(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH)
            if (!f.isFile) return
            val lines = f.readLines()
            val seen = HashMap<String, Int>()
            val parsed = ArrayList<Triple<String, String, Int>>() // key, rawValue, lineIndex
            var changed = false
            for ((i, raw) in lines.withIndex()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
                val m = Regex("^\"?([A-Za-z0-9_.-]+)\"?\\s*=\\s*(.*)$").find(line) ?: continue
                val key = m.groupValues[1]
                var value = m.groupValues[2].trim()
                if (raw.trimStart().startsWith("\"")) {
                    value = value.removeSuffix("\"").trim()
                    changed = true
                }
                seen[key] = seen.getOrDefault(key, 0) + 1
                parsed.add(Triple(key, value, i))
            }
            for (c in seen.values) if (c > 1) changed = true
            if (!changed) return
            val out = LinkedHashMap<String, String>()
            for ((k, v, _) in parsed) out[k] = v
            val sb = StringBuilder()
            sb.append("# Normalized by ZeroTermux+ (duplicates removed)\n")
            for ((k, v) in out) sb.append(k).append(" = ").append(v).append('\n')
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(sb.toString())
            if (tmp.renameTo(f)) {
                Log.i(TAG, "normalized: ${seen.size} keys, ${lines.size} lines -> ${out.size}")
            } else {
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "normalize failed", e)
        }
    }
}
