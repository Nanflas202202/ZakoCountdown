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
import com.errorsiayusulif.zakocountdown.ui.popup.PopupDataHolder // 导入
import com.errorsiayusulif.zakocountdown.utils.SystemUtils
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.*
import java.util.Date
import java.util.concurrent.TimeUnit

class AppOpenDetectorService : AccessibilityService() {

    companion object {
        const val TAG = "ZakoDetectorService"
        private const val COOLDOWN_PERIOD_MS = 10000L // 延长冷却时间到10秒
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appLaunchTimestamps = mutableMapOf<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.v(TAG, ">>> [EVENT RECEIVED] Type: ${AccessibilityEvent.eventTypeToString(event?.eventType ?: -1)}, Pkg: ${event?.packageName}")

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // 过滤掉不关心的包
            if (packageName == "com.android.systemui" || packageName == applicationContext.packageName) {
                return
            }
            // 过滤掉启动器和输入法等
            if (event.className?.contains("Launcher") == true || event.className?.contains("InputMethod") == true) {
                return
            }

            // --- 【核心修复】只检查，不更新时间戳 ---
            val currentTime = System.currentTimeMillis()
            val lastLaunchTimeForThisApp = appLaunchTimestamps[packageName] ?: 0L

            if ((currentTime - lastLaunchTimeForThisApp) > COOLDOWN_PERIOD_MS) {
                // 冷却检查通过，启动检查流程
                serviceScope.launch {
                    checkAndLaunchPopup(packageName, currentTime) // 把当前时间戳传递过去
                }
            } else {
                Log.v(TAG, "[IGNORE] Cooldown active for $packageName.")
            }
        }
    }

    private suspend fun checkAndLaunchPopup(packageName: String, launchTime: Long) {
        Log.d(TAG, "==> [CHECK START] for $packageName")
        val prefs = PreferenceManager(this)

        if (!prefs.isPopupReminderEnabled()) {
            Log.w(TAG, "[CHECK FAILED] Popup feature disabled.")
            return
        }

        val monitoredApps = prefs.getImportantApps()
        if (monitoredApps.contains(packageName)) {
            Log.i(TAG, "[CHECK PASSED] '$packageName' IS IN monitored list.")
            val repository = (application as ZakoCountdownApplication).repository
            val importantEvents = repository.getImportantEvents()

            if (importantEvents.isNotEmpty()) {
                val eventIds = importantEvents
                    .filter { it.targetDate.time - Date().time >= 0 }
                    .map { it.id }
                    .toLongArray()

                if (eventIds.isEmpty()) {
                    Log.d(TAG, "[CHECK FAILED] All important events have passed.")
                    return
                }

                // --- 【核心修复】在这里，我们100%确定要弹窗了，才更新时间戳！ ---
                appLaunchTimestamps[packageName] = launchTime
                Log.i(TAG, "[SUCCESS] All checks passed. Updating timestamp for $packageName.")

                val popupMode = prefs.getPopupMode()
                val useWindowMode = when (popupMode) {
                    PreferenceManager.POPUP_MODE_WINDOW -> true
                    else -> false
                }

                if (useWindowMode) {
                    launchWindowPopup(eventIds)
                } else {
                    launchActivityPopup(eventIds)
                }
            } else {
                Log.w(TAG, "[CHECK FAILED] Important events list is empty.")
            }
        } else {
            Log.w(TAG, "[CHECK FAILED] '$packageName' IS NOT in monitored list.")
        }
        Log.d(TAG, "<== [CHECK END] for $packageName")
    }
    private suspend fun launchActivityPopup(eventIds: LongArray) {
        withContext(Dispatchers.Main) {
            Log.d(TAG, "Launching Activity mode with IDs: ${eventIds.joinToString()}")
            val intent = Intent(this@AppOpenDetectorService, PopupReminderActivity::class.java).apply {
                // --- 【核心修复】使用正确的 Key 和 Value ---
                putExtra(PopupReminderActivity.EXTRA_EVENT_IDS, eventIds)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
    private suspend fun launchWindowPopup(eventIds: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "[CHECK FAILED] Popup window requires SYSTEM_ALERT_WINDOW permission. Posting a notification.")
            postPermissionRequestNotification()
            return
        }

        // --- 【在这里植入窃听器#1】 ---
        Log.i(TAG, "[ACTION] Preparing to launch Window mode...")
        Log.d(TAG, "      -> Sending Event IDs: ${eventIds.joinToString()}")

        withContext(Dispatchers.Main) {
            try {
                val intent = Intent(this@AppOpenDetectorService, PopupViewService::class.java).apply {
                    putExtra("extra_event_ids", eventIds)
                }
                startService(intent)
                Log.d(TAG, "[SUCCESS] startService for PopupViewService called with Intent: $intent")
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