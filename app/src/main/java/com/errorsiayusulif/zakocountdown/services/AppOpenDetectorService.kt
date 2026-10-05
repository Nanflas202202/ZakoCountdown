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
import androidx.core.app.NotificationCompat
import com.errorsiayusulif.zakocountdown.MainActivity
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.ui.popup.PopupReminderActivity
import com.errorsiayusulif.zakocountdown.utils.LocaleHelper
import com.errorsiayusulif.zakocountdown.utils.TimeCalculator
import kotlinx.coroutines.*
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AppOpenDetectorService : AccessibilityService() {

    companion object {
        const val TAG = "ZakoDetector" // 缩短 Tag 方便查看
        private const val COOLDOWN_PERIOD_MS = 10000L

        /** 启动 Activity 后等待多久去确认界面是否真的可见。 */
        private const val ACTIVITY_LAUNCH_PROBE_MS = 600L

        /**
         * 这些包名的窗口出现时不能算作「打开了某个应用」，
         * 否则应用内的 Activity 切换、系统弹窗或输入法会误触发提醒。
         */
        private val SYSTEM_PACKAGE_PREFIXES = listOf(
            "com.android.systemui",
            "com.android.settings",
            "android",
            "com.android.inputmethod",
            "com.google.android.inputmethod",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appLaunchTimestamps = mutableMapOf<String, Long>()

    /**
     * 当前处于前台的「外部」应用包名。
     * 只在真正跨应用切换时才更新，应用内部 Activity 切换不会改变它，
     * 因此不会触发弹窗。
     */
    @Volatile
    private var lastVisiblePackageName: String = ""

    /** 本次前台会话（同一应用停留期间）是否已经弹过一次，避免来回切换时重复弹出。 */
    private val hasShownForCurrentSession = AtomicBoolean(false)

    /**
     * 检测代数。每次前台应用变化都会 +1。
     * 只允许「最新一代」的检测任务派发弹窗，避免慢速检测迟到后误弹。
     */
    private val foregroundGeneration = AtomicInteger(0)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service Connected and Ready.")
    }

    /** 让 Service 里的 getString / 通知文案跟随用户选择的语言 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val currentPackageName = event.packageName?.toString() ?: return

        // --- 过滤 1：本应用自己的界面（Activity 切换、弹窗对话框）永不触发提示 ---
        if (currentPackageName == applicationContext.packageName) {
            // 我们回到了自己应用：等用户下次切到别的应用时，允许重新提示一次
            hasShownForCurrentSession.set(false)
            return
        }

        // --- 过滤 2：系统界面不属于"打开了一个应用" ---
        // 保留最后记录的第三方包名，避免下拉通知栏/输入法/系统弹窗导致状态机错乱
        if (currentPackageName in SYSTEM_PACKAGE_PREFIXES) return

        // --- 过滤 3：只有真正跨应用切换才算一次"打开应用" ---
        if (currentPackageName == lastVisiblePackageName) return

        // 新的前台应用 → 开启新的会话，允许弹一次
        val generation = foregroundGeneration.incrementAndGet()
        lastVisiblePackageName = currentPackageName
        hasShownForCurrentSession.set(false)

        // 检查白名单与冷却
        val currentTime = System.currentTimeMillis()
        val lastLaunchTime = appLaunchTimestamps[currentPackageName] ?: 0L

        if ((currentTime - lastLaunchTime) <= COOLDOWN_PERIOD_MS) {
            Log.d(TAG, "Cooldown active for $currentPackageName")
            return
        }

        serviceScope.launch {
            try {
                checkAndLaunchPopup(currentPackageName, currentTime, generation)
            } catch (e: Exception) {
                Log.e(TAG, "Error in checkAndLaunchPopup", e)
            }
        }
    }

    /**
     * 检测过程中用户可能已经切走了，只有一个「仍然是最新前台应用」的任务才允许弹窗，
     * 否则应用内的 Activity 切换/快速来回切换都会造成误弹。
     */
    private fun isStillForeground(packageName: String, generation: Int): Boolean {
        return generation == foregroundGeneration.get() && lastVisiblePackageName == packageName
    }

    private suspend fun checkAndLaunchPopup(packageName: String, launchTime: Long, generation: Int) {
        val prefs = PreferenceManager(this)
        if (!prefs.isPopupReminderEnabled()) {
            Log.d(TAG, "Popup disabled in settings -> skip.")
            return
        }

        val monitoredApps = prefs.getImportantApps()
        if (!monitoredApps.contains(packageName)) {
            Log.d(TAG, "$packageName is not in the popup target list -> skip.")
            return
        }

        Log.i(TAG, ">>> MATCHED TARGET APP: $packageName <<<")

        // 同一个前台会话只提示一次。注意：这个标记要等「确认有内容可弹」之后再占用，
        // 否则一次空弹（没有重点日程）会把这个会话的提示机会白白吃掉。
        if (hasShownForCurrentSession.get()) {
            Log.d(TAG, "Already reminded for this session of $packageName -> skip.")
            return
        }

        val repository = (application as ZakoCountdownApplication).repository
        val importantEvents = repository.getImportantEvents()

        if (importantEvents.isEmpty()) {
            Log.d(TAG, "No important events in database -> skip.")
            return
        }

        val eventIds = importantEvents
            .filter { it.targetDate.time - Date().time >= 0 }
            .map { it.id }
            .toLongArray()

        if (eventIds.isEmpty()) {
            Log.d(TAG, "Important events exist but none is in the future -> skip.")
            return
        }

        // 真正要弹了，先占用本会话的机会
        hasShownForCurrentSession.set(true)
        appLaunchTimestamps[packageName] = launchTime

        // 查询期间用户可能已经切走（或切回了本应用）：这种情况下不要骑脸打扰
        if (!isStillForeground(packageName, generation)) {
            hasShownForCurrentSession.set(false) // 没弹成，机会还回去
            Log.d(TAG, "Foreground app changed during the DB query -> skip this time.")
            return
        }

        val popupMode = prefs.getPopupMode()
        Log.i(TAG, "Showing popup. mode=$popupMode events=${eventIds.size}")

        if (popupMode == PreferenceManager.POPUP_MODE_WINDOW) {
            launchWindowPopup(eventIds)
        } else {
            launchActivityPopup(eventIds)
        }
    }

    /**
     * Activity 模式。
     *
     * 关键点：从 Service 后台启动 Activity 可能被系统的「后台启动 Activity 限制」静默丢弃
     * （startActivity 不抛异常，但界面不会出现）。因此这里不依赖异常判断，而是：
     *   1. 先尝试 Activity；
     *   2. 极短延迟后检查是否有界面真的可见，没有则退回悬浮窗，再不行退回通知。
     * 另外必须去掉 FLAG_ACTIVITY_NO_USER_ACTION —— 它会把这次启动标记为「非用户发起」，
     * 反而让部分 ROM 直接拒绝把界面带到前台。
     */
    private suspend fun launchActivityPopup(eventIds: LongArray) {
        withContext(Dispatchers.Main) {
            try {
                val intent = Intent(this@AppOpenDetectorService, PopupReminderActivity::class.java).apply {
                    putExtra(PopupReminderActivity.EXTRA_EVENT_IDS, eventIds)
                    // 从 Service 启动 Activity 必须带 NEW_TASK
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                Log.i(TAG, "startActivity(PopupReminderActivity) ...")
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start PopupReminderActivity.", e)
                fallbackToNotification(eventIds)
                return@withContext
            }

            // 后台启动限制不会抛异常，只会「什么都没发生」，所以要自己确认一下
            delay(ACTIVITY_LAUNCH_PROBE_MS)
            if (!isAppUiVisible()) {
                Log.w(TAG, "PopupReminderActivity did not become visible (background-start blocked?) -> falling back.")
                launchWindowPopup(eventIds, allowNotificationFallback = true)
            }
        }
    }

    /** 通过「本进程是否有处于 resumed 状态的 Activity」判断界面到底有没有起来。 */
    private fun isAppUiVisible(): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.appTasks.any { task ->
                task.taskInfo?.topActivity?.packageName == applicationContext.packageName
            }
        } catch (e: Exception) {
            // 拿不到任务栈时保守认为「已可见」，避免误触发降级
            Log.w(TAG, "Could not inspect app tasks, assuming visible.", e)
            true
        }
    }

    private suspend fun launchWindowPopup(eventIds: LongArray, allowNotificationFallback: Boolean = false) {
        // 严格检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission DENIED (SYSTEM_ALERT_WINDOW).")
            if (allowNotificationFallback) {
                fallbackToNotification(eventIds)
            } else {
                postPermissionRequestNotification()
            }
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
                Log.e(TAG, "Failed to start PopupViewService.", e)
                if (allowNotificationFallback) {
                    fallbackToNotification(eventIds)
                }
            }
            Unit
        }
    }

    /**
     * 最后兜底：既起不了 Activity 也没有悬浮窗权限时，用一条高优先级通知替代，
     * 保证「弹窗提醒」在受限 ROM 上也不会完全失效。
     */
    private fun fallbackToNotification(eventIds: LongArray) {
        serviceScope.launch {
            try {
                val repository = (application as ZakoCountdownApplication).repository
                val events = repository.getEventsByIds(eventIds.toList())
                val text = events.take(3).joinToString("\n") { event ->
                    val diff = TimeCalculator.calculateDifference(event.targetDate)
                    if (diff.isPast) {
                        getString(R.string.popup_past_event, event.title, diff.totalDays.toInt())
                    } else {
                        getString(R.string.popup_future_event, event.title, diff.totalDays.toInt())
                    }
                }.ifBlank { getString(R.string.service_no_event_info) }

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "ZakoPopupFallbackChannel"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            channelId,
                            getString(R.string.service_channel_reminder_title),
                            NotificationManager.IMPORTANCE_HIGH
                        )
                    )
                }

                val tapIntent = Intent(this@AppOpenDetectorService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pendingIntent = PendingIntent.getActivity(
                    this@AppOpenDetectorService, 124, tapIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val notification = NotificationCompat.Builder(this@AppOpenDetectorService, channelId)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(getString(R.string.service_friendly_title))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                manager.notify(1240, notification)
                Log.i(TAG, "Fallback notification posted.")
            } catch (e: Exception) {
                Log.e(TAG, "Fallback notification failed.", e)
            }
        }
    }

    private fun postPermissionRequestNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ZakoPermissionChannel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notify_permission_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        val pendingIntent = PendingIntent.getActivity(this, 123, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notify_overlay_missing_title))
            .setContentText(getString(R.string.notify_overlay_missing_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1234, notification)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service Interrupted")
        resetDetectionState()
        serviceScope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Service Destroyed")
        resetDetectionState()
        serviceScope.cancel()
    }

    private fun resetDetectionState() {
        lastVisiblePackageName = ""
        hasShownForCurrentSession.set(false)
        foregroundGeneration.incrementAndGet()
    }
}