// file: app/src/main/java/com/errorsiayusulif/zakocountdown/receiver/BootCompletedReceiver.kt
package com.errorsiayusulif.zakocountdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.errorsiayusulif.zakocountdown.utils.ServiceGuardian

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed event received. Summoning ServiceGuardian.")
            // 收到广播后，全权委托给守护者
            ServiceGuardian.ensureServicesAreRunning(context)
        }
    }
}
/**
    private fun rescheduleWidgetWorker(context: Context) {
        val periodicWorkRequest = PeriodicWorkRequestBuilder<UpdateWidgetWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ZakoWidgetProvider.WIDGET_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
        Log.d(TAG, "Widget worker re-enqueued.")
    }

    private fun restartPermanentNotification(context: Context) {
        val prefs = PreferenceManager(context)
        // 检查用户是否在重启前开启了常驻通知
        if (prefs.isPermanentNotificationEnabled()) { // 需要在PreferenceManager添加此方法
            val serviceIntent = Intent(context, CountdownService::class.java)
            context.startService(serviceIntent)
            Log.d(TAG, "Permanent notification service restarted.")
        }
    }

    private suspend fun rescheduleAllAlarms(context: Context) {
        val repository = (context.applicationContext as ZakoCountdownApplication).repository
        val allEvents = repository.getAllEventsSuspend()

        for (event in allEvents) {
            AlarmScheduler.scheduleReminder(context, event)
        }
        Log.d(TAG, "${allEvents.size} event reminders have been rescheduled.")
    }
}**/