// file: app/src/main/java/com/errorsiayusulif/zakocountdown/receiver/ReminderReceiver.kt
package com.errorsiayusulif.zakocountdown.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.errorsiayusulif.zakocountdown.MainActivity
import com.errorsiayusulif.zakocountdown.R

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_TIME_DESCRIPTION = "extra_time_description"
        const val CHANNEL_ID = "ZakoCountdownReminderChannel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1)
        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: context.getString(R.string.service_reminder_default_event)
        val timeDescription = intent.getStringExtra(EXTRA_TIME_DESCRIPTION) ?: context.getString(R.string.service_reminder_default_time)

        if (eventId == -1L) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.service_channel_reminder_title),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.service_channel_reminder_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationSettingsIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("errorsiayusulif://zakocountdown/notifications")
        )

        // --- 【核心修复】使用正确的 context ---
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.toInt(),
            notificationSettingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.service_friendly_title))
            .setContentText(context.getString(R.string.service_friendly_text, eventTitle, timeDescription))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(eventId.toInt(), notification)
    }
}