// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/FocusDiagnostics.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.FocusGuardStore
import com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper
import com.errorsiayusulif.zakocountdown.utils.FocusGuardLog

/**
 * 防沉迷诊断报告的组装与展示。
 *
 * 抽成独立对象而不是留在某个 Fragment 里，是因为入口**搬过家**：
 * 原先挂在「高级设置 → 防沉迷 → 诊断」，后来移到「调试台」。
 * 逻辑与入口分离后，以后入口再变也不用动这份报告代码。
 */
object FocusDiagnostics {

    /**
     * 展示判定轨迹，并提供一键复制。
     *
     * 复制是主要出口：部分设备/ROM 限制第三方应用读 logcat，
     * 应用内的内存缓冲就成了唯一可获取的轨迹来源。
     */
    fun showLogDialog(fragment: Fragment) {
        val context = fragment.requireContext()
        val report = buildReport(context)
        val lines = FocusGuardLog.snapshot()

        val body = when {
            lines.isEmpty() -> context.getString(R.string.focus_log_empty)

            // 只有设置页自己产生的 [CONFIG] 记录，没有任何服务/引擎记录
            // → 说明无障碍事件从未进来过。这是「完全没反应」的最常见形态，
            //   直接给出判读结论，省得用户对着日志猜。
            lines.none { it.contains("[SERVICE]") || it.contains("[ENGINE]") || it.contains("[CONTENT]") } ->
                context.getString(R.string.focus_log_no_events) + "\n\n" + lines.joinToString("\n")

            else -> lines.joinToString("\n")
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.focus_view_log)
            .setMessage(body)
            .setPositiveButton(R.string.focus_log_copy) { _, _ -> copyToClipboard(context, report) }
            .setNeutralButton(R.string.focus_log_clear) { _, _ ->
                FocusGuardLog.clear()
                Toast.makeText(context, R.string.focus_log_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.common_close, null)
            .show()
    }

    /** 打印一份配置快照到日志缓冲，然后打开日志对话框。 */
    fun dumpConfigAndShow(fragment: Fragment, reason: String) {
        FocusGuardLog.dumpConfig(fragment.requireContext(), reason)
        showLogDialog(fragment)
    }

    /**
     * 组装可直接外发的诊断报告。
     *
     * 除了日志本身，还带上「环境抬头」：设备、系统版本、应用版本、以及
     * **系统实际生效的无障碍事件订阅**。最后这项最关键 ——
     * 「服务订阅的是不是新配置」决定了关键词匹配能否工作，
     * 而它无法从日志正文推断，必须主动读出来。
     */
    fun buildReport(context: Context): String {
        val sb = StringBuilder()

        sb.appendLine("=== ZakoCountdown 防沉迷诊断报告 ===")
        sb.appendLine(
            "时间: " + java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
        )
        sb.appendLine("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine("系统: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("应用: ${BuildConfig.VERSION_NAME} / upgradeCode=${BuildConfig.UPGRADE_CODE}")

        val accessibilityOn = AccessibilityStatusHelper.isAccessibilityServiceEnabled(context)
        sb.appendLine("无障碍服务: ${if (accessibilityOn) "已开启" else "未开启"}")

        // 系统当前实际派发的事件类型 —— 判断「订阅是否陈旧」的直接依据
        sb.appendLine("系统生效的事件订阅: ${accessibilityEventTypesOf(context)}")

        // 配置快照
        val config = FocusGuardStore.load(context)
        sb.appendLine("总开关: ${config.enabled}")
        sb.appendLine("规则数: ${config.rules.size}")
        config.rules.forEach { rule ->
            sb.appendLine(
                "  · ${rule.appName} (${rule.packageName}) enabled=${rule.enabled} " +
                    "usable=${rule.isUsable} globalBlock=${rule.globalBlock} " +
                    "keywords=${rule.keywords} maskTop=${rule.maskTopPercent}% " +
                    "maskBottom=${rule.maskBottomPercent}% passes=${rule.dailyPassLimit} " +
                    "minutes=${rule.leisureMinutes}"
            )
        }
        sb.appendLine("今日已用次数: ${config.passUsed}")
        sb.appendLine("放开截止: ${config.leisureUntil}")
        sb.appendLine("计数日期: ${config.passDate}（今天=${FocusGuardStore.today()}）")

        sb.appendLine()
        sb.appendLine("--- 事件统计（按包名，全部事件，未被过滤）---")
        sb.appendLine(FocusGuardLog.statisticsReport())

        sb.appendLine()
        sb.appendLine("--- 判定日志（最新在前）---")
        val lines = FocusGuardLog.snapshot()
        if (lines.isEmpty()) {
            sb.appendLine("（空）")
        } else {
            lines.forEach { sb.appendLine(it) }
        }

        return sb.toString()
    }

    /**
     * 读系统当前**实际生效**的无障碍事件订阅。
     *
     * 走 `AccessibilityManager.getEnabledAccessibilityServiceList` ——
     * 这才代表「系统正在怎么派发」，而不是我们配置文件里写了什么。
     * 两者不一致时（典型场景：升级前就开过服务），关键词匹配不会工作。
     */
    private fun accessibilityEventTypesOf(context: Context): String {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? android.view.accessibility.AccessibilityManager
            val enabled = am?.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).orEmpty()
            val mine = enabled.firstOrNull {
                it.resolveInfo?.serviceInfo?.packageName == context.packageName
            }
            if (mine == null) {
                "未找到本应用的服务（可能未开启）"
            } else {
                android.view.accessibility.AccessibilityEvent
                    .eventTypeToString(mine.eventTypes)
                    .ifBlank { "eventTypes=${mine.eventTypes}" }
            }
        } catch (t: Throwable) {
            "读取失败: ${t.javaClass.simpleName}"
        }
    }

    private fun copyToClipboard(context: Context, report: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(context, R.string.focus_log_copy_failed, Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ZakoFocusReport", report))
        Toast.makeText(context, R.string.focus_log_copied, Toast.LENGTH_LONG).show()
    }
}
