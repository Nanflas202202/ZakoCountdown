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
        const val TAG = "ZakoDetector" // 缩短 Tag 方便查看
        private const val COOLDOWN_PERIOD_MS = 10000L
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appLaunchTimestamps = mutableMapOf<String, Long>()
    private var lastVisiblePackageName: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service Connected and Ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPackageName = event.packageName?.toString() ?: return

            // 过滤逻辑
            if (currentPackageName == applicationContext.packageName ||
                currentPackageName == "com.android.systemui") return

            // 状态机：防止应用内跳转触发
            if (currentPackageName == lastVisiblePackageName) return
            lastVisiblePackageName = currentPackageName

            // 检查白名单与冷却
            val currentTime = System.currentTimeMillis()
            val lastLaunchTime = appLaunchTimestamps[currentPackageName] ?: 0L

            if ((currentTime - lastLaunchTime) > COOLDOWN_PERIOD_MS) {
                serviceScope.launch {
                    try {
                        checkAndLaunchPopup(currentPackageName, currentTime)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in checkAndLaunchPopup", e)
                    }
                }
            } else {
                Log.d(TAG, "Cooldown active for $currentPackageName")
            }
        }
    }

    private suspend fun checkAndLaunchPopup(packageName: String, launchTime: Long) {
        val prefs = PreferenceManager(this)
        if (!prefs.isPopupReminderEnabled()) {
            Log.d(TAG, "Popup feature disabled in settings.")
            return
        }

        val monitoredApps = prefs.getImportantApps()
        if (monitoredApps.contains(packageName)) {
            Log.i(TAG, ">>> MATCHED TARGET APP: $packageName <<<")

            val repository = (application as ZakoCountdownApplication).repository
            val importantEvents = repository.getImportantEvents()

            if (importantEvents.isNotEmpty()) {
                val eventIds = importantEvents
                    .filter { it.targetDate.time - Date().time >= 0 }
                    .map { it.id }
                    .toLongArray()

                if (eventIds.isEmpty()) {
                    Log.d(TAG, "No future important events found.")
                    return
                }

                // 更新时间戳，防止连击
                appLaunchTimestamps[packageName] = launchTime

                val popupMode = prefs.getPopupMode()
                Log.d(TAG, "Preparing to launch. Mode: $popupMode, Event Count: ${eventIds.size}")

                if (popupMode == PreferenceManager.POPUP_MODE_WINDOW) {
                    launchWindowPopup(eventIds)
                } else {
                    launchActivityPopup(eventIds)
                }
            } else {
                Log.d(TAG, "No important events configured in database.")
            }
        }
    }

    private suspend fun launchActivityPopup(eventIds: LongArray) {
        withContext(Dispatchers.Main) {
            try {
                Log.i(TAG, "Launching PopupReminderActivity...")
                val intent = Intent(this@AppOpenDetectorService, PopupReminderActivity::class.java).apply {
                    putExtra(PopupReminderActivity.EXTRA_EVENT_IDS, eventIds)
                    // 关键 Flags：从 Service 启动 Activity 必须加
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    // 尝试规避部分后台启动限制
                    addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                }
                startActivity(intent)
                Log.i(TAG, "startActivity called successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "FATAL: Failed to start Activity.", e)
                Toast.makeText(applicationContext, "Zako: 无法弹出提示，请检查后台弹出权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun launchWindowPopup(eventIds: LongArray) {
        // 严格检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Permission DENIED: SYSTEM_ALERT_WINDOW")
            postPermissionRequestNotification()
            return
        }

        withContext(Dispatchers.Main) {
            try {
                Log.i(TAG, "Starting PopupViewService...")
                val intent = Intent(this@AppOpenDetectorService, PopupViewService::class.java).apply {
                    putExtra("extra_event_ids", eventIds)
                }
                startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "FATAL: Failed to start Service.", e)
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("权限缺失")
            .setContentText("请授予悬浮窗权限以显示倒数日提醒")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1234, notification)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service Interrupted")
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service Destroyed")
        serviceScope.cancel()
    }
}