// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/AlarmScheduler.kt
package com.errorsiayusulif.zakocountdown.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.receiver.ReminderReceiver
import java.util.Calendar

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun scheduleReminder(context: Context, event: CountdownEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = PreferenceManager(context)
        val reminderTime = prefs.getReminderTime()

        // 如果用户设置为“不提醒”，则取消闹钟并直接返回
        if (reminderTime == PreferenceManager.REMINDER_TIME_NONE) {
            cancelReminder(context, event)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms. Skipping for event: ${event.title}")
            return
        }

        val calendar = Calendar.getInstance().apply {
            time = event.targetDate
            // 根据用户设置，计算提醒时间
            when (reminderTime) {
                PreferenceManager.REMINDER_TIME_1_DAY -> add(Calendar.DAY_OF_YEAR, -1)
                PreferenceManager.REMINDER_TIME_3_DAYS -> add(Calendar.DAY_OF_YEAR, -3)
                PreferenceManager.REMINDER_TIME_1_WEEK -> add(Calendar.WEEK_OF_YEAR, -1)
            }
            // 统一在早上9点提醒
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis < System.currentTimeMillis()) {
            Log.d(TAG, "Reminder time for '${event.title}' is in the past. Skipping.")
            return
        }

        val timeDescription = when (reminderTime) {
            PreferenceManager.REMINDER_TIME_1_DAY -> "1天"
            PreferenceManager.REMINDER_TIME_3_DAYS -> "3天"
            PreferenceManager.REMINDER_TIME_1_WEEK -> "1周"
            else -> ""
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(ReminderReceiver.EXTRA_EVENT_TITLE, event.title)
            putExtra(ReminderReceiver.EXTRA_TIME_DESCRIPTION, timeDescription)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, event.id.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d(TAG, "Scheduled reminder for '${event.title}' at ${calendar.time}")
    }


    fun cancelReminder(context: Context, event: CountdownEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE // 只查找，不创建
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}