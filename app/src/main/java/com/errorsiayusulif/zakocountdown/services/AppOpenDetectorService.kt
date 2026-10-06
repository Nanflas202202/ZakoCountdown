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
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import com.errorsiayusulif.zakocountdown.utils.FocusGuardLog
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

        /**
         * 无障碍服务自身的 Context，供浮层添加窗口使用。
         *
         * 为什么必须有这个：防沉迷浮层用 `TYPE_ACCESSIBILITY_OVERLAY`，
         * 系统要求这类窗口挂在**无障碍服务的 window token** 上。
         * 之前浮层在普通 Service 里 `addView`，必然抛
         * `BadTokenException: token null is not valid` ——
         * 而链路前面的判定全都正常，所以看起来像「防沉迷完全不工作」。
         *
         * 置空时机放在 [onDestroy]，避免服务已销毁后浮层仍拿到失效 Context。
         */
        @Volatile
        var windowContext: android.content.Context? = null
            private set

        /**
         * 让防沉迷引擎忘掉内部状态，并撤下正在显示的遮挡浮层。
         *
         * 设置页在「改动规则 / 关闭总开关」后调用它。
         * 之所以做成静态方法：引擎实例活在无障碍服务里，设置页拿不到它，
         * 而 SharedPreferences 是跨进程可见的，所以只需要通知「状态失效」即可 ——
         * 引擎下次读配置时会自然看到新值。
         */
        fun resetFocusGuard(context: Context) {
            try {
                FocusOverlayService.dismiss(context)
            } catch (t: Throwable) {
                Log.w(TAG, "撤下防沉迷浮层失败", t)
            }
        }
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

    /**
     * 防沉迷判定引擎。
     *
     * 在 [onServiceConnected] 里显式初始化，而不是用 `by lazy`：
     * Kotlin 的 lazy 会把初始化异常**缓存并每次重抛**，那是「一访问就崩」的语义；
     * 这里要的是「初始化失败就本次会话禁用该功能」，所以必须显式 try/catch 成可空属性。
     */
    @Volatile
    private var focusGuardEngine: FocusGuardEngine? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 浮层加窗口需要这个 Context（TYPE_ACCESSIBILITY_OVERLAY 的 token 来自它）
        windowContext = this
        focusGuardEngine = try {
            FocusGuardEngine(applicationContext)
        } catch (t: Throwable) {
            Log.e(TAG, "防沉迷引擎初始化失败，本次会话禁用该功能", t)
            null
        }
        Log.i(TAG, "Accessibility Service Connected and Ready.")
        FocusGuardLog.d("SERVICE", "无障碍服务已连接，引擎=${if (focusGuardEngine != null) "就绪" else "初始化失败"}")

        // 自检：把系统**实际生效**的事件订阅打出来。
        //
        // 这一条很关键：accessibilityEventTypes 是服务被启用时由系统读取的。
        // 如果用户在旧版本（只订阅 typeWindowStateChanged）时就开过无障碍，
        // 升级后系统可能仍按旧配置派发 —— 内容变化事件收不到，
        // 关键词匹配这条链就永远不触发，现象正是「完全没反应」。
        // 把实际值打出来，一眼能判断是不是这个原因。
        reportServiceInfo()

        // 服务连接时打一次配置快照 —— 「配了规则却没反应」时，
        // 第一件要确认的就是服务读到的配置和设置页写下的是否一致
        FocusGuardLog.dumpConfig(applicationContext, "onServiceConnected")
    }

    /** 打印系统当前生效的无障碍服务配置，并对缺失的订阅给出可执行结论。 */
    private fun reportServiceInfo() {
        try {
            val info = serviceInfo
            if (info == null) {
                FocusGuardLog.w("SERVICE", "serviceInfo 为 null，无法自检事件订阅")
                return
            }
            FocusGuardLog.d(
                "SERVICE",
                "系统生效的事件订阅 eventTypes=${AccessibilityEvent.eventTypeToString(info.eventTypes)}"
            )
            FocusGuardLog.d("SERVICE", "  flags=${info.flags}")

            // 缺少内容变化事件 → 关键词匹配必然失效
            val hasContentChanged =
                (info.eventTypes and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) != 0
            if (!hasContentChanged) {
                FocusGuardLog.w(
                    "SERVICE",
                    "⚠️ 当前未订阅 TYPE_WINDOW_CONTENT_CHANGED。" +
                        "关键词匹配依赖该事件，目前只有「整应用遮挡」能生效。" +
                        "解决办法：到系统设置里把本应用的无障碍服务关闭再重新打开，让新配置生效。"
                )
            }
        } catch (t: Throwable) {
            FocusGuardLog.e("SERVICE", "自检事件订阅失败", t)
        }
    }

    /** 让 Service 里的 getString / 通知文案跟随用户选择的语言 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    /**
     * 该事件是否来自输入法窗口。
     *
     * `AccessibilityWindowInfo.getType()` 是系统给出的**权威分类**，
     * 比按包名猜可靠得多：各厂商会预置自家定制的输入法
     * （努比亚是 `com.sohu.inputmethod.sogou.nubia`），
     * 任何硬编码白名单都补不全。
     *
     * 需要 `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` 才能拿到 windows 列表，
     * 由 `accessibility_service_config.xml` 声明；拿不到时返回 false，
     * 退回到 [isInputMethodPackage] 的包名判断。
     */
    private fun isInputMethodWindow(windowId: Int): Boolean {
        if (windowId < 0) return false
        return try {
            windows.orEmpty().any {
                it.id == windowId &&
                    it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
        } catch (t: Throwable) {
            // windows 在部分 ROM 上会抛异常；不让它影响主流程
            false
        }
    }

    /**
     * 包名兜底：当前前台包是否是已启用的输入法之一。
     *
     * 用 `InputMethodManager` 查**系统实际启用的输入法列表**，
     * 而不是维护一张写死的包名表 —— 后者注定漏掉厂商定制版。
     */
    private fun isInputMethodPackage(packageName: String): Boolean {
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        } catch (t: Throwable) {
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        val currentPackageName = event.packageName?.toString() ?: return

        // 事件统计：必须在所有过滤之前计数。
        // 这样即使某个包后面被过滤掉，统计表里也看得到「它确实来过事件」，
        // 是区分「用户没打开受管制应用」与「打开时事件没进来」的唯一手段。
        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                FocusGuardLog.countEvent(currentPackageName, isStateChanged = true)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                FocusGuardLog.countEvent(currentPackageName, isStateChanged = false)
        }

        // 事件级日志：排查「完全没反应」时，这一行决定性地说明
        // 「事件到底有没有打进来」。只在 VERBOSE 下打印，否则内容变化事件会刷屏。
        FocusGuardLog.v(
            "SERVICE",
            "事件 type=${AccessibilityEvent.eventTypeToString(eventType)} pkg=$currentPackageName"
        )

        // --- 过滤 0：本应用自己的界面，两种事件都不处理 ---
        if (currentPackageName == applicationContext.packageName) {
            // 我们回到了自己应用：等用户下次切到别的应用时，允许重新提示一次
            hasShownForCurrentSession.set(false)
            focusGuardEngine?.onLeavingPackage(currentPackageName)
            return
        }

        // --- 过滤 0.5：输入法窗口不是「切换了应用」 ---
        //
        // 这条过滤是必需的，否则会出非常明显的 bug：
        // 防沉迷浮层里点一下输入框 → 软键盘弹出 → 键盘是**独立窗口**、
        // 有自己的包名 → 被判定成「离开了受管制应用」→ 浮层被撤下；
        // 收起键盘回到原应用 → 又被判定为重新进入 → 浮层再弹一次。
        // 用户看到的就是「点输入框浮层闪一下、关掉又重开」。
        //
        // 注意：判断必须走 `isInputMethodWindow()`，不能靠硬编码包名 ——
        // 各厂商会换成自己的定制输入法（例如努比亚的
        // `com.sohu.inputmethod.sogou.nubia`），白名单永远补不全。
        if (isInputMethodWindow(event.windowId) || isInputMethodPackage(currentPackageName)) {
            FocusGuardLog.v("SERVICE", "  跳过：输入法窗口 $currentPackageName（不属于应用切换）")
            return
        }

        // --- 过滤 1：系统界面不属于「打开了一个应用」 ---
        // 保留最后记录的第三方包名，避免下拉通知栏/输入法/系统弹窗导致状态机错乱
        if (currentPackageName in SYSTEM_PACKAGE_PREFIXES) {
            FocusGuardLog.v("SERVICE", "  跳过：系统包 $currentPackageName")
            return
        }

        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                handleWindowStateChanged(currentPackageName)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                // 防沉迷的关键词匹配走这条：页面内容变化时节点树才是可读的
                focusGuardEngine?.onContentChanged(currentPackageName, rootInActiveWindow)
        }
    }

    /**
     * 处理跨应用切换。
     *
     * 这里是「开屏弹窗提醒」与「防沉迷判定」共同的前台应用变化入口 ——
     * 两者都只在**真正跨应用**切换时才该动作，所以过滤逻辑放在一起，
     * 避免各自维护一套状态机而互相打架。
     */
    private fun handleWindowStateChanged(currentPackageName: String) {
        // --- 过滤 2：只有真正跨应用切换才算一次「打开应用」 ---
        if (currentPackageName == lastVisiblePackageName) {
            FocusGuardLog.v("SERVICE", "  跳过：与上一个前台应用相同（应用内 Activity 切换）")
            return
        }

        FocusGuardLog.d(
            "SERVICE",
            "跨应用切换：$lastVisiblePackageName → $currentPackageName"
        )

        // 上一个前台应用如果是受管应用，先撤下它的遮挡浮层
        focusGuardEngine?.onLeavingPackage(lastVisiblePackageName)

        // 新的前台应用 → 开启新的会话，允许弹一次
        val generation = foregroundGeneration.incrementAndGet()
        lastVisiblePackageName = currentPackageName
        hasShownForCurrentSession.set(false)

        // 防沉迷：整应用遮挡在这一步就能判定（不需要等内容加载）
        focusGuardEngine?.onForegroundPackageChanged(currentPackageName)

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
        // 先撤下浮层再清 Context：浮层移除同样需要这个 Context
        FocusGuardLog.d("SERVICE", "无障碍服务已断开，撤下防沉迷浮层并清空 windowContext")
        try {
            FocusOverlayService.dismiss(this)
        } catch (t: Throwable) {
            FocusGuardLog.w("SERVICE", "断开时撤下浮层失败", t)
        }
        windowContext = null
        resetDetectionState()
        serviceScope.cancel()
    }

    private fun resetDetectionState() {
        lastVisiblePackageName = ""
        hasShownForCurrentSession.set(false)
        foregroundGeneration.incrementAndGet()
    }
}