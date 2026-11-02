package com.termux.zerocore.api

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.termux.R

/**
 * Fires the alarms set through `cortex-plus-alarmclock` (app-level AlarmManager, not Ubuntu cron):
 * a high-priority notification with the alarm sound, insistent until dismissed.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Cortex+ alarm"
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Cortex+ alarms", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        )
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
        nm.createNotificationChannel(channel)

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Cortex+ alarm")
            .setContentText(label)
            .setStyle(Notification.BigTextStyle().bigText(label))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "cortex_alarm"
        private const val NOTIFICATION_ID = 3001
        const val EXTRA_LABEL = "label"
    }
}
