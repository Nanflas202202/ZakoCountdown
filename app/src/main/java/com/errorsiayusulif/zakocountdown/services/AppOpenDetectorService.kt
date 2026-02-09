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
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.ui.popup.PopupReminderActivity
import kotlinx.coroutines.*
import java.util.Date

class AppOpenDetectorService : AccessibilityService() {

    companion object {
        const val TAG = "ZakoDetectorService"
        private const val COOLDOWN_PERIOD_MS = 10000L // 10秒冷却
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appLaunchTimestamps = mutableMapOf<String, Long>()

    // 核心变量：记录上一个可见的前台包名
    private var lastVisiblePackageName: String = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Log.v(TAG, ">>> [EVENT] Type: ${event?.eventType}, Pkg: ${event?.packageName}")

        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPackageName = event.packageName?.toString() ?: return

            // 1. 过滤名单：忽略自身、系统UI（通知栏/锁屏）、输入法、Launcher
            if (currentPackageName == applicationContext.packageName ||
                currentPackageName == "com.android.systemui" ||
                event.className?.contains("InputMethod") == true ||
                event.className?.contains("Launcher") == true) {
                return
            }

            // 2. 状态机核心：如果当前包名与上一次记录的包名一致，说明是应用内部跳转
            if (currentPackageName == lastVisiblePackageName) {
                // 用户仍在同一个应用内操作，忽略
                return
            }

            // 3. 确实发生了应用切换，更新状态
            // Log.i(TAG, "App Switch Detected: $lastVisiblePackageName -> $currentPackageName")
            lastVisiblePackageName = currentPackageName

            // 4. 执行业务逻辑检查（冷却时间 + 白名单）
            val currentTime = System.currentTimeMillis()
            val lastLaunchTime = appLaunchTimestamps[currentPackageName] ?: 0L

            if ((currentTime - lastLaunchTime) > COOLDOWN_PERIOD_MS) {
                serviceScope.launch {
                    checkAndLaunchPopup(currentPackageName, currentTime)
                }
            } else {
                Log.v(TAG, "Cooldown active for $currentPackageName")
            }
        }
    }

    private suspend fun checkAndLaunchPopup(packageName: String, launchTime: Long) {
        val prefs = PreferenceManager(this)

        if (!prefs.isPopupReminderEnabled()) return

        val monitoredApps = prefs.getImportantApps()
        if (monitoredApps.contains(packageName)) {
            Log.i(TAG, "Target App Detected: $packageName")

            val repository = (application as ZakoCountdownApplication).repository
            val importantEvents = repository.getImportantEvents()

            if (importantEvents.isNotEmpty()) {
                val eventIds = importantEvents
                    .filter { it.targetDate.time - Date().time >= 0 }
                    .map { it.id }
                    .toLongArray()

                if (eventIds.isEmpty()) return

                // 确认弹窗，更新时间戳
                appLaunchTimestamps[packageName] = launchTime

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
            }
        }
    }

    private suspend fun launchActivityPopup(eventIds: LongArray) {
        withContext(Dispatchers.Main) {
            val intent = Intent(this@AppOpenDetectorService, PopupReminderActivity::class.java).apply {
                putExtra(PopupReminderActivity.EXTRA_EVENT_IDS, eventIds)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    private suspend fun launchWindowPopup(eventIds: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            postPermissionRequestNotification()
            return
        }

        withContext(Dispatchers.Main) {
            try {
                val intent = Intent(this@AppOpenDetectorService, PopupViewService::class.java).apply {
                    putExtra("extra_event_ids", eventIds)
                }
                startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start PopupViewService", e)
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
            .setSmallIcon(R.drawable.ic_settings) // 确保你有这个图标，或者换成 ic_launcher_foreground
            .setContentTitle("ZakoCountdown 需要权限")
            .setContentText("为了显示悬浮窗提醒，请授予“悬浮窗”权限。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1234, notification)
    }

    override fun onInterrupt() {
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}