// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/ServiceGuardian.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.services.CountdownService
import com.errorsiayusulif.zakocountdown.widget.UpdateWidgetWorker
import com.errorsiayusulif.zakocountdown.widget.ZakoWidgetProvider
import java.util.concurrent.TimeUnit

object ServiceGuardian {
    private const val TAG = "ServiceGuardian"
    fun ensureServicesAreRunning(context: Context) {
        Log.d(TAG, "Ensuring all services are running...")
        val prefs = PreferenceManager(context)

        // --- 【植入窃听器#2】 ---
        val isEnabled = prefs.isPermanentNotificationEnabled()
        Log.d(TAG, "Checking permanent notification status. Is enabled? -> $isEnabled")

        if (isEnabled) {
            Log.d(TAG, "Permanent notification is enabled, attempting to start service...")
            val serviceIntent = Intent(context, CountdownService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CountdownService from guardian", e)
            }
        }
        rescheduleWidgetWorker(context)
    }
    private fun rescheduleWidgetWorker(context: Context) {
        val periodicWorkRequest = PeriodicWorkRequestBuilder<UpdateWidgetWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ZakoWidgetProvider.WIDGET_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
        Log.d(TAG, "Widget worker re-enqueued by guardian.")
    }
}