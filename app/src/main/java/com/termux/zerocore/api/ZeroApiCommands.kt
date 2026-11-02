package com.termux.zerocore.api

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.termux.BuildConfig
import com.termux.shared.shell.ShellUtils
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The zero-api command set: alarmclock, wifi-status, copy-terminal, check-update.
 * sessionProvider supplies the terminal session the copy command reads from.
 */
class ApiResult(val status: Int, val output: String)

object ZeroApiCommands {
    private const val TAG = "ZeroApi"
    const val REPO = "puraffert/ZeroTermux-plus"

    @Volatile
    var sessionProvider: () -> TermuxSession? = { null }

    fun run(context: Context, req: ApiRequest): ApiResult = try {
        when (req.command) {
            "alarmclock" -> alarmclock(context, req)
            "wifi-status" -> wifiStatus(context)
            "copy-terminal" -> copyTerminal(context)
            "check-update" -> checkUpdate()
            else -> ApiResult(1, "zero-api: unknown command '${req.command}'\n")
        }
    } catch (e: IllegalArgumentException) {
        ApiResult(1, "zero-api: ${e.message}\n")
    } catch (e: Exception) {
        Log.w(TAG, "${req.command} failed", e)
        ApiResult(1, "zero-api: ${req.command} failed: ${e.message ?: e.javaClass.simpleName}\n")
    }

    private fun alarmclock(context: Context, req: ApiRequest): ApiResult {
        val args = ApiArgs.parse(req.args, withValue = setOf("-t", "--time", "-l", "--label"))
        val time = args.value("-t", "--time")
            ?: throw IllegalArgumentException("usage: zero-api alarmclock -t yyyy-MM-dd HH:mm:ss [-l label]")
        val label = args.value("-l", "--label") ?: "Alarm set for $time"
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val millis = try {
            format.parse(time)?.time ?: throw IllegalArgumentException("bad time '$time'")
        } catch (e: java.text.ParseException) {
            throw IllegalArgumentException("bad time '$time', expected yyyy-MM-dd HH:mm:ss")
        }
        if (millis <= System.currentTimeMillis()) throw IllegalArgumentException("time $time is in the past")
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        val intent = android.content.Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_LABEL, label)
        val pi = android.app.PendingIntent.getBroadcast(
            context, (millis and 0x7fffffff).toInt(), intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, millis, pi)
        return ApiResult(0, "alarm set: $time\n")
    }

    private fun wifiStatus(context: Context): ApiResult {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return ApiResult(1, "zero-api: wifi is not available on this device\n")
        val json = StringBuilder("{")
        json.append("\"enabled\":").append(wm.isWifiEnabled)
        val info = try { wm.connectionInfo } catch (_: SecurityException) { null }
        if (wm.isWifiEnabled && info != null) {
            val ssid = info.ssid?.removeSurrounding("\"") ?: "unknown"
            json.append(",\"ssid\":\"").append(ssid.replace("\"", "\\\"")).append('"')
            json.append(",\"bssid\":\"").append(info.bssid ?: "unknown").append('"')
            val ip = info.ipAddress
            if (ip != 0) {
                json.append(",\"ip\":\"").append(
                    "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
                ).append('"')
            }
            json.append(",\"linkSpeedMbps\":").append(info.linkSpeed)
            json.append(",\"frequencyMHz\":").append(info.frequency)
            json.append(",\"rssi\":").append(info.rssi)
        }
        json.append('}')
        return ApiResult(0, "$json\n")
    }

    private fun copyTerminal(context: Context): ApiResult {
        val session = sessionProvider()?.getTerminalSession()
            ?: return ApiResult(1, "zero-api: no running terminal session\n")
        val text = ShellUtils.getTerminalSessionTranscriptText(session, true, false)
        if (text.isBlank()) return ApiResult(1, "zero-api: terminal is empty\n")
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("ZeroTermux+", text))
        val lines = text.count { it == '\n' } + 1
        return ApiResult(0, "copied $lines lines\n")
    }

    private fun checkUpdate(): ApiResult {
        val url = URL("https://api.github.com/repos/$REPO/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val apkUrl = json.optJSONArray("assets")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
            }
            val local = BuildConfig.VERSION_NAME.removePrefix("v")
            val remote = tag.removePrefix("v")
            val newer = compareVersions(remote, local) > 0
            val out = JSONObject()
            out.put("update_available", newer)
            out.put("remote_version", remote)
            out.put("local_version", local)
            out.put("apk_url", apkUrl ?: "")
            out.put("release_url", json.optString("html_url", ""))
            return ApiResult(0, out.toString() + "\n")
        } finally {
            conn.disconnect()
        }
    }

    fun checkUpdatePublic(): ApiResult = checkUpdate()

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
