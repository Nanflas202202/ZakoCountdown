// file: app/src/main/java/com/errorsiayusulif/zakocountdown/services/FocusGuardEngine.kt
package com.errorsiayusulif.zakocountdown.services

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.errorsiayusulif.zakocountdown.utils.FocusGuardLog
import com.errorsiayusulif.zakocountdown.data.FocusAppRule
import com.errorsiayusulif.zakocountdown.data.FocusGuardStore

/**
 * 防沉迷判定引擎。
 *
 * ## 为什么不做成独立的 AccessibilityService
 *
 * `BIND_ACCESSIBILITY_SERVICE` 在系统里**每个应用只能有一个**无障碍服务入口。
 * 本工程已有 [AppOpenDetectorService]（负责开屏弹窗提醒），所以防沉迷不能另起服务，
 * 必须以「被它调用」的形式接入 —— 这就是本类存在的理由：
 * 把判定逻辑与事件源解耦，服务只负责喂事件进来。
 *
 * ## 判定流程（移植自 anti-addiction 的 AppStateManager）
 *
 * ```
 * 前台包名变化
 *     ↓
 * 总开关是否打开 / 该包名是否在规则表内
 *     ↓
 * 是否正处于「已放开」时段（免答题或答过题）
 *     ↓
 * 整应用遮挡？→ 直接遮挡
 * 否则递归扫节点树找关键词 → 命中则遮挡
 * ```
 *
 * ## 与原型的三处差异
 *
 * 1. **无网络**。原版还有 TextFetcher / DeviceInfoReporter / AppConfigManager
 *    三处联网，这里一概没有，判定完全本地。
 * 2. **不需要 2 秒轮询兜底**。原版靠 `postDelayed` 轮询补事件丢失；
 *    本工程的无障碍服务已经订阅了 `TYPE_WINDOW_CONTENT_CHANGED`，
 *    页面内容变化本身就会触发重新判定，轮询属于重复保险。
 * 3. **关键词匹配复用同一套方向**：判断「关键词是否被节点文本包含」
 *    （`keyword in nodeText`），而不是反过来。这是原版的关键设计 ——
 *    关键词是「推荐」这样的短词，页面节点文本往往更长（如「推荐」按钮的
 *    完整描述），方向搞反会完全失效。
 */
class FocusGuardEngine(private val context: Context) {

    private companion object {
        const val TAG = "FocusGuardEngine"

        /**
         * 节点树扫描的最大深度。
         * 无障碍树在信息流类应用里可能非常深，设个上限避免极端情况下的长耗时遍历
         * 把主线程卡住（遍历发生在无障碍回调线程，但仍是同步调用）。
         */
        const val MAX_SCAN_DEPTH = 40
    }

    /** 上一次判定命中的包名，避免同一页面上重复下发遮挡指令。 */
    private var lastBlockedPackage: String? = null

    /**
     * 处理一次前台应用变化。
     *
     * 由 [AppOpenDetectorService] 在确认「真正跨应用切换」之后调用。
     */
    fun onForegroundPackageChanged(packageName: String) {
        FocusGuardLog.d("ENGINE", "① 前台应用变化 → $packageName")

        // 离开上一个受管应用时清掉状态，下次进来能重新判定
        if (packageName != lastBlockedPackage) {
            lastBlockedPackage = null
        }

        val config = FocusGuardStore.load(context)
        if (!config.enabled) {
            FocusGuardLog.d("ENGINE", "  ② 中止：总开关未开启")
            return
        }

        val rule = config.ruleFor(packageName)
        if (rule == null) {
            // 最常见的一条，只在 VERBOSE 下打印以免刷屏
            FocusGuardLog.v("ENGINE", "  ② 中止：该应用不在规则表内")
            return
        }

        FocusGuardLog.d(
            "ENGINE",
            "  ② 命中规则「${rule.appName}」usable=${rule.isUsable} globalBlock=${rule.globalBlock}"
        )
        if (!rule.isUsable) {
            FocusGuardLog.d("ENGINE", "  ③ 中止：规则不可用（未配关键词且未开整应用遮挡）")
            return
        }

        // ⚠️ 必须先判放开时段。
        // 少了这一步会出现「刚答完题 / 刚用掉一次免答题，切出去再切回来又被挡住」。
        if (FocusGuardStore.isLeisureActive(context, packageName)) {
            val until = config.leisureUntil[packageName] ?: 0L
            val remainSec = ((until - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            FocusGuardLog.d("ENGINE", "  ③ 中止：处于放开时段，剩余约 ${remainSec}s")
            lastBlockedPackage = null
            return
        }

        // 整应用遮挡不需要看内容，直接挡住
        if (rule.globalBlock) {
            FocusGuardLog.d("ENGINE", "  ③ 整应用遮挡 → 直接下发浮层")
            block(rule)
            return
        }

        // 关键词匹配需要节点树，交给内容变化事件去扫
        // （窗口状态变化时 rootInActiveWindow 往往还没就绪）
        FocusGuardLog.d("ENGINE", "  ③ 需关键词匹配 → 等内容变化事件来扫节点树")
    }

    /**
     * 处理一次页面内容变化。
     *
     * 这是关键词匹配的真正入口 —— 信息流应用的内容是异步加载的，
     * 「窗口状态变化」时节点树往往还是空的，必须靠内容变化事件来扫。
     */
    fun onContentChanged(packageName: String, root: AccessibilityNodeInfo?) {
        val config = FocusGuardStore.load(context)
        if (!config.enabled) return

        val rule = config.ruleFor(packageName)
        if (rule == null) {
            // 内容变化事件量很大，这里只在 VERBOSE 下打印，避免刷屏
            FocusGuardLog.v("CONTENT", "内容变化：$packageName 不在规则表内")
            return
        }
        if (rule.globalBlock) return
        if (!rule.isUsable) {
            FocusGuardLog.v("CONTENT", "内容变化：${rule.appName} 规则不可用（无关键词）")
            return
        }

        // 已处于放开时段 → 不遮挡
        if (FocusGuardStore.isLeisureActive(context, packageName)) {
            FocusGuardLog.v("CONTENT", "内容变化：${rule.appName} 处于放开时段，跳过")
            if (lastBlockedPackage == packageName) lastBlockedPackage = null
            return
        }

        // 已经挡着了就不必重复扫
        if (lastBlockedPackage == packageName && FocusOverlayService.isShowing) return

        // 节点树拿不到是最容易「静默失效」的一种情况：无障碍服务没有
        // canRetrieveWindowContent、或目标应用禁止了节点读取，都会走到这里。
        // 必须明确打出来，否则现象就是「什么都没发生」。
        if (root == null) {
            FocusGuardLog.w("CONTENT", "内容变化：${rule.appName} 但 rootInActiveWindow 为 null → 无法扫描关键词")
            return
        }

        val hit = rule.keywords.any { keyword -> containsKeyword(root, keyword, 0) }
        if (hit) {
            FocusGuardLog.d("CONTENT", "${rule.appName} 命中关键词 ${rule.keywords} → 下发浮层")
            block(rule)
        } else {
            // 没命中也要能看到，才能判断是「扫了但没匹配」还是「根本没扫」
            FocusGuardLog.v(
                "CONTENT",
                "${rule.appName} 扫过节点树（子节点数=${root.childCount}），未命中 ${rule.keywords}"
            )
        }
    }

    /**
     * 递归查找节点树里是否出现关键词。
     *
     * 判定方向：**关键词包含节点文本**，而不是节点文本包含关键词。
     * 例：关键词「推荐」应当命中页面上一个文本为「推荐」的入口；
     * 也要能命中文本更短的片段。这个方向与原版 `FloatHelper.findTargetText` 一致。
     *
     * Kotlin 下不需要像 Java 那样手工 `recycle()` 节点：API 33+ 起
     * `AccessibilityNodeInfo` 的回收已交给 GC，重复 recycle 反而会抛异常。
     */
    private fun containsKeyword(node: AccessibilityNodeInfo?, keyword: String, depth: Int): Boolean {
        if (node == null || depth > MAX_SCAN_DEPTH) return false
        if (keyword.isBlank()) return false

        val text = node.text?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.takeIf { it.isNotBlank() }

        if (text != null && keyword.contains(text.toString())) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (containsKeyword(node.getChild(i), keyword, depth + 1)) return true
        }
        return false
    }

    /** 下发遮挡指令给浮层服务。 */
    private fun block(rule: FocusAppRule) {
        lastBlockedPackage = rule.packageName

        val intent = Intent(context, FocusOverlayService::class.java).apply {
            putExtra(FocusOverlayService.EXTRA_PACKAGE, rule.packageName)
            putExtra(FocusOverlayService.EXTRA_APP_NAME, rule.appName)
            putStringArrayListExtra(FocusOverlayService.EXTRA_KEYWORDS, ArrayList(rule.keywords))
            putExtra(FocusOverlayService.EXTRA_GLOBAL_BLOCK, rule.globalBlock)
            putExtra(FocusOverlayService.EXTRA_DAILY_LIMIT, rule.dailyPassLimit)
            putExtra(FocusOverlayService.EXTRA_LEISURE_MINUTES, rule.leisureMinutes)
            putExtra(FocusOverlayService.EXTRA_MASK_TOP_PERCENT, rule.maskTopPercent)
            putExtra(FocusOverlayService.EXTRA_MASK_BOTTOM_PERCENT, rule.maskBottomPercent)
        }
        try {
            context.startService(intent)
            FocusGuardLog.d("ENGINE", "  ④ 已 startService → FocusOverlayService（${rule.packageName}）")
        } catch (t: Throwable) {
            // 后台启动服务在部分 ROM 上会被拦；不能让无障碍服务因此崩掉
            FocusGuardLog.e("ENGINE", "  ④ 下发遮挡指令失败（startService 被拒）", t)
        }
    }

    /**
     * 前台应用离开受管列表时撤下浮层。
     *
     * 浮层是 `TYPE_ACCESSIBILITY_OVERLAY`，会一直盖在最上层，
     * 不主动撤下的话用户切到别的应用还会看到它。
     */
    fun onLeavingPackage(packageName: String) {
        if (lastBlockedPackage == packageName || FocusOverlayService.currentPackage == packageName) {
            lastBlockedPackage = null
            if (FocusOverlayService.isShowing) {
                FocusOverlayService.dismiss(context)
            }
        }
    }

    /** 配置变更（开关/规则改动）后清掉内部状态，下次事件重新判定。 */
    fun reset() {
        lastBlockedPackage = null
        if (FocusOverlayService.isShowing) {
            FocusOverlayService.dismiss(context)
        }
    }
}
