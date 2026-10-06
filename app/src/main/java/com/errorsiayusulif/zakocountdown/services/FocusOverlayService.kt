// file: app/src/main/java/com/errorsiayusulif/zakocountdown/services/FocusOverlayService.kt
package com.errorsiayusulif.zakocountdown.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.FocusAppRule
import com.errorsiayusulif.zakocountdown.data.FocusGuardStore
import com.errorsiayusulif.zakocountdown.databinding.OverlayFocusBlockBinding
import com.errorsiayusulif.zakocountdown.utils.FocusGuardLog
import com.errorsiayusulif.zakocountdown.utils.LocaleHelper
import com.errorsiayusulif.zakocountdown.utils.ZakoThemeApplier
import kotlin.random.Random

/**
 * 防沉迷遮挡浮层。
 *
 * ## ⚠️ 关键点：窗口必须由**无障碍服务自己**的 Context 添加
 *
 * 浮层用的是 `TYPE_ACCESSIBILITY_OVERLAY`。这种窗口类型和普通应用窗口不同 ——
 * 系统要求它必须挂在**无障碍服务的 window token** 上。
 *
 * 曾经这里直接用本 Service 的 Context 调 `addView`，结果是必现失败：
 * ```
 * BadTokenException: Unable to add window -- token null is not valid
 * ```
 * 而这条链路前面的判定全都正常，所以现象看起来像「防沉迷完全不工作」，
 * 实际上只差最后一步。（这个坑真机踩过。）
 *
 * 现在通过 [AppOpenDetectorService.windowContext] 取无障碍服务的 Context，
 * 从它那里拿 WindowManager 并加窗口，token 才合法。
 *
 * ## 为什么答题界面是内联的
 *
 * 浮层里的「做一道题」原本弹 `AlertDialog`。但在无障碍浮层这种窗口类型下，
 * 从它里面再开 Dialog 会再撞一次 token / 焦点问题。
 * 改成同一棵视图树里的两页（两个容器切换可见性），
 * 少一次窗口事务，也不受焦点语义影响。
 */
class FocusOverlayService : Service() {

    companion object {
        private const val TAG = "FocusOverlay"

        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_KEYWORDS = "extra_keywords"
        const val EXTRA_GLOBAL_BLOCK = "extra_global_block"
        const val EXTRA_DAILY_LIMIT = "extra_daily_limit"
        const val EXTRA_LEISURE_MINUTES = "extra_leisure_minutes"
        const val EXTRA_MASK_TOP_PERCENT = "extra_mask_top_percent"
        const val EXTRA_MASK_BOTTOM_PERCENT = "extra_mask_bottom_percent"

        private const val ACTION_DISMISS = "com.errorsiayusulif.zakocountdown.FOCUS_DISMISS"

        /** 浮层当前是否正在显示（供引擎判断，避免重复下发）。 */
        @Volatile
        var isShowing: Boolean = false
            private set

        /** 当前被遮挡的包名；引擎用它判断浮层是否还对应着前台应用。 */
        @Volatile
        var currentPackage: String? = null
            private set

        /** 外部（引擎）要求撤下浮层。 */
        fun dismiss(context: Context) {
            try {
                context.startService(
                    Intent(context, FocusOverlayService::class.java).setAction(ACTION_DISMISS)
                )
            } catch (t: Throwable) {
                FocusGuardLog.w("OVERLAY", "撤下浮层指令下发失败", t)
                // 服务可能已停：退而直接清状态，避免 isShowing 卡在 true
                isShowing = false
                currentPackage = null
            }
        }
    }

    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentRule: FocusAppRule? = null

    /** 答题状态：正确答案与当前题目。 */
    private var challengeAnswer: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        FocusGuardLog.d("OVERLAY", "onStartCommand action=${intent?.action}")

        if (intent?.action == ACTION_DISMISS) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        val pkg = intent?.getStringExtra(EXTRA_PACKAGE)
        if (pkg.isNullOrBlank()) {
            FocusGuardLog.w("OVERLAY", "收到没有包名的启动请求，忽略")
            return START_NOT_STICKY
        }

        if (isShowing && currentPackage == pkg) {
            FocusGuardLog.v("OVERLAY", "已在显示 $pkg，跳过重建")
            return START_NOT_STICKY
        }

        val rule = FocusAppRule(
            packageName = pkg,
            appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty(),
            keywords = intent.getStringArrayListExtra(EXTRA_KEYWORDS).orEmpty(),
            globalBlock = intent.getBooleanExtra(EXTRA_GLOBAL_BLOCK, false),
            dailyPassLimit = intent.getIntExtra(EXTRA_DAILY_LIMIT, 1),
            leisureMinutes = intent.getIntExtra(EXTRA_LEISURE_MINUTES, 2),
            maskTopPercent = intent.getIntExtra(EXTRA_MASK_TOP_PERCENT, 0),
            maskBottomPercent = intent.getIntExtra(EXTRA_MASK_BOTTOM_PERCENT, 0)
        )
        currentRule = rule
        showOverlay(rule)
        return START_NOT_STICKY
    }

    // ========================================================================
    // 浮层
    // ========================================================================

    private fun showOverlay(rule: FocusAppRule) {
        removeOverlayView()

        // 取无障碍服务的 Context —— 这是 TYPE_ACCESSIBILITY_OVERLAY 能加成功的前提
        val host = AppOpenDetectorService.windowContext
        if (host == null) {
            FocusGuardLog.e(
                "OVERLAY",
                "  ⑤ 无法添加浮层：无障碍服务未运行（TYPE_ACCESSIBILITY_OVERLAY 的窗口 token 只能来自它）"
            )
            stopSelf()
            return
        }
        val hostWindowManager = host.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (hostWindowManager == null) {
            FocusGuardLog.e("OVERLAY", "  ⑤ 无法添加浮层：拿不到 WindowManager")
            stopSelf()
            return
        }

        // 主题解析仍用本 Service 的 Context，保证与主界面配色一致
        val themeResId = ZakoThemeApplier.resolveThemeResId(this)
        val themedContext = ContextThemeWrapper(host, themeResId)
        val inflater = LayoutInflater.from(themedContext)
        val binding = OverlayFocusBlockBinding.inflate(inflater)

        binding.tvFocusTitle.text = getString(R.string.focus_overlay_title)
        binding.tvFocusMessage.text = if (rule.globalBlock) {
            getString(R.string.focus_overlay_message_global, rule.appName)
        } else {
            getString(R.string.focus_overlay_message, rule.appName)
        }

        refreshQuota(binding, rule)
        wireChallenge(binding, rule)

        binding.btnFocusPass.setOnClickListener { usePass(binding, rule) }
        binding.btnFocusLeave.setOnClickListener { leaveApp() }

        val overlayHeight = computeOverlayHeight(host, rule)
        val density = resources.displayMetrics.density
        binding.focusOverlayRoot.setPadding(
            0,
            (overlayHeight * 0.06f).toInt().coerceIn((16 * density).toInt(), (96 * density).toInt()),
            0,
            0
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // ⚠️ 这两个 flag 缺一不可，原因不同：
            //
            // · FLAG_NOT_TOUCH_MODAL —— **修「搜索框看得见却点不动」**
            //   本窗口是**可聚焦**的（因为要输答案，不能加 FLAG_NOT_FOCUSABLE）。
            //   而一个可聚焦、又没声明这个 flag 的窗口，系统会按**模态**处理：
            //   落在它边界之外的触摸不会传给下层应用。
            //   于上方让出来的那条区域虽然露出来了，点它却没有任何反应。
            //   加上这个 flag，边界外的触摸才会正常穿透到下面的应用。
            //
            // · FLAG_LAYOUT_IN_SCREEN —— 让窗口坐标系以**整屏**为基准。
            //   下面的 y 偏移是用「真实屏幕高度」（含状态栏）算的，
            //   两者必须一致，否则窗口会整体上移，把本该让出的顶部又盖住。
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 顶部对齐 + y 偏移：让出顶部才能真正保住搜索栏/扫码入口
            gravity = Gravity.TOP
            y = topOffsetPx(host, rule)
        }

        try {
            hostWindowManager.addView(binding.root, params)
            overlayView = binding.root
            isShowing = true
            currentPackage = rule.packageName
            FocusGuardLog.d(
                "OVERLAY",
                "  ⑤ 浮层已添加：${rule.appName}（${rule.packageName}）" +
                    " 高度=${params.height}px 顶部偏移=${params.y}px 覆盖率=${rule.maskCoverageRatio}"
            )
        } catch (t: Throwable) {
            FocusGuardLog.e("OVERLAY", "  ⑤ 添加遮挡浮层失败（addView 被拒）", t)
            isShowing = false
            currentPackage = null
            stopSelf()
        }
    }

    // ========================================================================
    // 答题解锁（内联页面）
    // ========================================================================

    /** 生成题目并接好「提交 / 返回」两个按钮。 */
    private fun wireChallenge(binding: OverlayFocusBlockBinding, rule: FocusAppRule) {
        binding.btnFocusChallenge.setOnClickListener {
            FocusGuardLog.d("OVERLAY", "点击「做一道题」→ 切到答题页")
            try {
                newQuestion(binding)
                showChallengePage(binding, show = true)

                FocusGuardLog.d(
                    "OVERLAY",
                    "  已切到答题页 block可见=${binding.pageBlock.visibility} " +
                        "challenge可见=${binding.pageChallenge.visibility} " +
                        "根视图attached=${binding.root.isAttachedToWindow}"
                )
            } catch (t: Throwable) {
                FocusGuardLog.e("OVERLAY", "切换到答题页失败", t)
            }
        }

        binding.btnChallengeBack.setOnClickListener {
            FocusGuardLog.d("OVERLAY", "答题页 → 返回遮挡页")
            try {
                showChallengePage(binding, show = false)
            } catch (t: Throwable) {
                FocusGuardLog.e("OVERLAY", "返回遮挡页失败", t)
            }
        }

        binding.btnChallengeSubmit.setOnClickListener {
            val typed = binding.etChallengeAnswer.text?.toString()?.trim()?.toIntOrNull()
            FocusGuardLog.d("OVERLAY", "提交答案 typed=$typed 期望=$challengeAnswer")
            if (typed == challengeAnswer) {
                grantAndDismiss(rule)
            } else {
                binding.tilChallengeAnswer.error = getString(R.string.focus_challenge_wrong)
                newQuestion(binding)
            }
        }
    }

    /**
     * 切换「遮挡提示」与「答题」两页。
     *
     * 直接控制两个容器的 visibility，而不是用 ViewFlipper：
     * ViewFlipper 会连带重建子视图的可见性与布局，在独立浮层窗口里
     * 出现过「点按钮后整个浮层消失」，改成显式可见性后行为确定。
     *
     * 另外**不**主动拉起软键盘。浮层本身是可聚焦窗口，
     * 用户点一下输入框系统就会正常弹键盘；由我们强制 showSoftInput
     * 会引起额外的焦点变化，同样属于不必要的风险。
     */
    private fun showChallengePage(binding: OverlayFocusBlockBinding, show: Boolean) {
        binding.pageBlock.visibility = if (show) View.GONE else View.VISIBLE
        binding.pageChallenge.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun newQuestion(binding: OverlayFocusBlockBinding) {
        val a = Random.nextInt(11, 99)
        val b = Random.nextInt(11, 99)
        val isAdd = Random.nextBoolean()
        challengeAnswer = if (isAdd) a + b else a - b
        val opSymbol = if (isAdd) "+" else "−"

        binding.tilChallengeAnswer.error = null
        binding.etChallengeAnswer.setText("")
        binding.tvChallengeQuestion.text =
            getString(R.string.focus_challenge_question, a, opSymbol, b)
    }

    /** 刷新「今天还剩几次免答题」，并在配额为 0 时禁用免答题按钮。 */
    private fun refreshQuota(binding: OverlayFocusBlockBinding, rule: FocusAppRule) {
        val remaining = FocusGuardStore.remainingPasses(this, rule)
        if (remaining > 0) {
            binding.tvFocusQuota.text = getString(R.string.focus_overlay_quota, remaining)
            binding.btnFocusPass.isEnabled = true
        } else {
            binding.tvFocusQuota.text = getString(R.string.focus_overlay_quota_none)
            binding.btnFocusPass.isEnabled = false
        }
    }

    // ========================================================================
    // 三条出口
    // ========================================================================

    /** 用一次免答题配额解锁。 */
    private fun usePass(binding: OverlayFocusBlockBinding, rule: FocusAppRule) {
        if (FocusGuardStore.consumePass(this, rule)) {
            Toast.makeText(
                this,
                getString(R.string.focus_pass_used, rule.leisureMinutes),
                Toast.LENGTH_SHORT
            ).show()
            removeOverlay()
            stopSelf()
        } else {
            Toast.makeText(this, getString(R.string.focus_pass_exhausted), Toast.LENGTH_SHORT).show()
            refreshQuota(binding, rule)
        }
    }

    /** 答题通过：不消耗免答题配额，只放开一段时间。 */
    private fun grantAndDismiss(rule: FocusAppRule) {
        FocusGuardStore.grantByChallenge(this, rule)
        Toast.makeText(this, getString(R.string.focus_unlocked, rule.leisureMinutes), Toast.LENGTH_SHORT).show()
        removeOverlay()
        stopSelf()
    }

    /**
     * 「离开这个应用」：回到桌面。
     *
     * 刻意不直接 `kill` 目标应用（那属于越权行为），只把用户送回桌面并撤下遮挡。
     */
    private fun leaveApp() {
        removeOverlay()
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(home)
        } catch (t: Throwable) {
            FocusGuardLog.w("OVERLAY", "回到桌面失败", t)
        }
        stopSelf()
    }

    // ========================================================================
    // 窗口几何
    // ========================================================================

    /**
     * 计算遮挡窗口的高度。
     *
     * 用真实屏幕高度（含状态栏/导航栏区域）而不是「可用窗口高度」：
     * 用户设置的百分比是照着「他看到的屏幕」来的，用可用高度会算出偏小的遮罩，
     * 底部会漏出内容。
     */
    private fun computeOverlayHeight(host: Context, rule: FocusAppRule): Int {
        val screenHeight = realScreenHeight(host)
        return (screenHeight * rule.maskCoverageRatio).toInt().coerceAtLeast(1)
    }

    /** 遮罩顶部让出的像素偏移。 */
    private fun topOffsetPx(host: Context, rule: FocusAppRule): Int {
        val screenHeight = realScreenHeight(host)
        return (screenHeight * rule.maskTopPercent.coerceIn(0, 80) / 100f).toInt()
    }

    /** 真实屏幕高度（像素）。取不到时退回 displayMetrics，避免算出 0 高的窗口。 */
    @Suppress("DEPRECATION")
    private fun realScreenHeight(host: Context): Int {
        return try {
            val wm = host.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm.currentWindowMetrics.bounds.height()
            } else {
                val metrics = android.util.DisplayMetrics()
                wm.defaultDisplay.getRealMetrics(metrics)
                metrics.heightPixels
            }
        } catch (t: Throwable) {
            FocusGuardLog.w("OVERLAY", "读取屏幕高度失败，退回 displayMetrics", t)
            resources.displayMetrics.heightPixels
        }
    }

    // ========================================================================
    // 清理
    // ========================================================================

    private fun removeOverlayView() {
        overlayView?.let { view ->
            try {
                // 必须用当初添加它的那个 WindowManager，否则移除同样会失败
                val host = AppOpenDetectorService.windowContext ?: this
                val wm = host.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeView(view)
            } catch (t: Throwable) {
                FocusGuardLog.w("OVERLAY", "移除遮挡浮层失败（可能已被系统移除）", t)
            }
        }
        overlayView = null
        isShowing = false
        currentPackage = null
    }

    private fun removeOverlay() {
        handler.removeCallbacksAndMessages(null)
        removeOverlayView()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlayView()
        super.onDestroy()
    }
}
