// file: app/src/main/java/com/errorsiayusulif/zakocountdown/services/AppOpenDetectorService.kt
package com.errorsiayusulif.zakocountdown.services

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.ui.popup.PopupReminderActivity
import com.errorsiayusulif.zakocountdown.utils.SystemUtils
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.*
import java.util.Date
import java.util.concurrent.TimeUnit

class AppOpenDetectorService : AccessibilityService() {

    companion object {
        const val TAG = "ZakoDetectorService" // 使用一个独特的TAG，方便过滤
        private const val COOLDOWN_PERIOD_MS = 5000L
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appLaunchTimestamps = mutableMapOf<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 【日志 #1】确认服务是否接收到任何事件
        Log.i(TAG, ">>> [EVENT RECEIVED] Type: ${AccessibilityEvent.eventTypeToString(event?.eventType ?: -1)}, Pkg: ${event?.packageName}")

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (packageName == "com.android.systemui" || packageName == applicationContext.packageName) {
                Log.v(TAG, "[IGNORE] System UI or self package.")
                return
            }

            val currentTime = System.currentTimeMillis()
            val lastLaunchTimeForThisApp = appLaunchTimestamps[packageName] ?: 0L

            if ((currentTime - lastLaunchTimeForThisApp) > COOLDOWN_PERIOD_MS) {
                Log.d(TAG, "[COOL-DOWN PASSED] for $packageName. Firing check.")
                appLaunchTimestamps[packageName] = currentTime
                serviceScope.launch {
                    checkAndLaunchPopup(packageName)
                }
            } else {
                Log.v(TAG, "[IGNORE] Cooldown active for $packageName.")
            }
        }
    }

    private suspend fun checkAndLaunchPopup(packageName: String) {
        Log.d(TAG, "==> [CHECK START] for $packageName")
        val prefs = PreferenceManager(this)

        if (!prefs.isPopupReminderEnabled()) {
            Log.w(TAG, "[CHECK FAILED] Popup reminder feature is disabled by user.")
            return
        }
        Log.i(TAG, "[CHECK PASSED] Popup feature is enabled.")

        val monitoredApps = prefs.getImportantApps()
        Log.d(TAG, "Monitored apps list: $monitoredApps")

        if (monitoredApps.contains(packageName)) {
            Log.i(TAG, "[CHECK PASSED] '$packageName' IS IN the monitored list.")

            val repository = (application as ZakoCountdownApplication).repository
            val importantEvents = repository.getImportantEvents()
            Log.d(TAG, "Found ${importantEvents.size} important events in database.")

            if (importantEvents.isNotEmpty()) {
                Log.i(TAG, "[CHECK PASSED] Important events list is not empty.")
                val eventToShow = importantEvents.first()
                val now = Date()
                val diffInMillis = eventToShow.targetDate.time - now.time

                if (diffInMillis < 0) {
                    Log.w(TAG, "[CHECK FAILED] Event '${eventToShow.title}' has already passed.")
                    return
                }
                Log.i(TAG, "[CHECK PASSED] Event '${eventToShow.title}' has not passed.")

                val daysLeft = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                val finalDays = if (daysLeft == 0L && diffInMillis > 0) 1 else daysLeft

                val popupMode = prefs.getPopupMode()
                val useWindowMode = when (popupMode) {
                    PreferenceManager.POPUP_MODE_WINDOW -> true
                    PreferenceManager.POPUP_MODE_ACTIVITY -> false
                    else -> false // 自动模式默认使用 Activity
                }
                Log.d(TAG, "[DECISION] PopupMode='$popupMode', UseWindowMode=$useWindowMode")
                val detailsString = importantEvents
                    .take(3) // 最多显示3个，防止内容太多
                    .joinToString("\n") { event ->
                        val diff = TimeCalculator.calculateDifference(event.targetDate)
                        if (diff.isPast) {
                            "「${event.title}」已过去 ${diff.totalDays} 天"
                        } else {
                            "距离「${event.title}」还有 ${diff.totalDays} 天"
                        }
                    }
                if (useWindowMode) {
                    launchWindowPopup(detailsString)
                } else {
                    launchActivityPopup(detailsString)
                }
            } else {
                Log.w(TAG, "[CHECK FAILED] Important events list is empty.")
            }
        } else {
            Log.w(TAG, "[CHECK FAILED] '$packageName' IS NOT in the monitored list.")
        }
        Log.d(TAG, "<== [CHECK END] for $packageName")
    }

    private suspend fun launchActivityPopup(details: String) {
        withContext(Dispatchers.Main) {
            //Log.i(TAG, "[ACTION] Launching in Activity mode for '${event.title}'...")
            try {
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@AppOpenDetectorService, PopupReminderActivity::class.java).apply {
                        putExtra(PopupReminderActivity.EXTRA_DETAILS_STRING, details) // 传递新的字符串
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                Log.d(TAG, "[SUCCESS] startActivity called.")
            } catch (e: Exception) {
                Log.e(TAG, "[FAILURE] Failed to start Activity", e)
            }
        }
    }

    private suspend fun launchWindowPopup(details: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "[CHECK FAILED] Popup window requires SYSTEM_ALERT_WINDOW permission. Posting a notification.")
            postPermissionRequestNotification()
            return
        }
        withContext(Dispatchers.Main) {
            //Log.i(TAG, "[ACTION] Launching in Window mode for '${event.title}'...")
            try {
                val intent = Intent(this@AppOpenDetectorService, PopupViewService::class.java).apply {
                    putExtra("extra_details_string", details) // 传递新的字符串
                }
                startService(intent)
                Log.d(TAG, "[SUCCESS] startService for PopupViewService called.")
            } catch (e: Exception) {
                Log.e(TAG, "[FAILURE] Failed to start PopupViewService", e)
            }
        }
    }

    private fun postPermissionRequestNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ZakoPermissionChannel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "权限请求", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        val pendingIntent = PendingIntent.getActivity(this, 123, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle("ZakoCountdown 需要权限")
            .setContentText("为了在其他应用上显示提醒，请授予“悬浮窗”权限。点击此处设置。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1234, notification)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted.")
        serviceScope.cancel() // <-- 【修复】
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected.")
        Toast.makeText(this, "ZakoCountdown 监控服务已开启", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed.")
        serviceScope.cancel() // <-- 【修复】
    }
}