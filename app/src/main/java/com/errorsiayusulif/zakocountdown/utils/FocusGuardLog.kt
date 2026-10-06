// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/FocusGuardLog.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.util.Log
import com.errorsiayusulif.zakocountdown.data.FocusGuardConfig
import com.errorsiayusulif.zakocountdown.data.FocusGuardStore

/**
 * 防沉迷的日志出口。
 *
 * ## 为什么要单独做一层
 *
 * 防沉迷是一条**跨进程、跨事件源**的长链路：
 *
 * ```
 * 用户点开目标应用
 *   → 系统派发无障碍事件（可能好几种类型）
 *   → AppOpenDetectorService 过滤（本应用 / 系统包 / 是否跨应用切换）
 *   → FocusGuardEngine 判定（总开关 / 规则是否存在 / 是否在放开时段 / 关键词命中）
 *   → FocusOverlayService 添加窗口
 * ```
 *
 * 任何一环断掉的表现都是**完全一样**的：「没反应」。没有分步日志时，
 * 只能靠猜。所以这里给每一环都打上带序号的日志，并统一前缀，
 * 让 logcat 一次过滤就能看出卡在哪一步。
 *
 * ## 抓取方式
 *
 * ```
 * adb logcat -s ZakoFocus:V
 * ```
 *
 * 想更啰嗦（每个被过滤掉的事件都打）就把 [VERBOSE] 改成 true 重新构建。
 */
object FocusGuardLog {

    /** 统一 tag，方便 `adb logcat -s ZakoFocus:V` 一次捞全。 */
    const val TAG = "ZakoFocus"

    /**
     * 详细模式。
     *
     * false 时只打印「决策节点」；true 时连每个被过滤掉的系统包/事件也打印。
     * 排查「完全没反应」这类问题时把它打开，因为那时恰恰需要知道
     * 「到底有没有事件打进来」。
     */
    const val VERBOSE = true

    /**
     * 内存环形缓冲。
     *
     * 为什么要有它：logcat 需要 adb，而「点开应用没反应」这种问题恰恰
     * 常在普通使用场景下被发现。这里把日志同时留在内存里，
     * 设置页可以直接看到最近一段轨迹，不必接线调试。
     *
     * 上限 400 条：防沉迷日志单条很短，400 条足够覆盖一次完整的
     * 「打开应用 → 判定 → 浮层」过程，又不会占多少内存。
     */
    private const val MAX_BUFFERED_LINES = 400

    private val buffer = ArrayDeque<String>()
    private val timeFormat = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())

    private fun record(level: String, step: String, message: String) {
        synchronized(buffer) {
            val line = "${timeFormat.format(java.util.Date())} $level [$step] $message"
            buffer.addLast(line)
            while (buffer.size > MAX_BUFFERED_LINES) buffer.removeFirst()
        }
    }

    /** 取最近的日志（最新在前），供设置页展示。 */
    fun snapshot(): List<String> = synchronized(buffer) { buffer.toList().asReversed() }

    /** 清空缓冲（用户手动清理，或设置页离开时）。 */
    fun clear() = synchronized(buffer) { buffer.clear() }

    // ========================================================================
    // 事件统计
    // ------------------------------------------------------------------------
    // 用途：日志正文会被截断，看不出「受管制应用到底有没有来过事件」。
    // 这里按包名累计计数，报告里输出一张小表，一眼就能分辨：
    //   · 表里根本没有受管制应用 → 用户没打开，或系统没派发它的事件
    //   · 表里有它、但判定环节没记录 → 判定逻辑被提前 return 掉了
    //   · 表里有它、contentChanged 为 0 → 该系统/应用不发内容变化事件，
    //     关键词匹配这条路走不通，只能靠「整应用遮挡」
    // ========================================================================

    /** key = 包名，value = [状态变化次数, 内容变化次数] */
    private val eventStats = linkedMapOf<String, IntArray>()

    fun countEvent(packageName: String, isStateChanged: Boolean) {
        synchronized(eventStats) {
            val slot = eventStats.getOrPut(packageName) { IntArray(2) }
            if (isStateChanged) slot[0]++ else slot[1]++
            // 防止极端情况下的无界增长
            if (eventStats.size > 60) {
                val oldest = eventStats.keys.firstOrNull() ?: return
                eventStats.remove(oldest)
            }
        }
    }

    /** 输出事件统计表，按总次数倒序。 */
    fun statisticsReport(): String {
        val snapshotMap = synchronized(eventStats) { eventStats.mapValues { it.value.copyOf() } }
        if (snapshotMap.isEmpty()) return "（还没有收到任何无障碍事件）"

        val sb = StringBuilder()
        sb.appendLine("包名 | 状态变化 | 内容变化")
        snapshotMap.entries
            .sortedByDescending { it.value[0] + it.value[1] }
            .forEach { (pkg, counts) ->
                sb.appendLine("$pkg | ${counts[0]} | ${counts[1]}")
            }
        return sb.toString().trimEnd()
    }

    fun clearStatistics() = synchronized(eventStats) { eventStats.clear() }

    fun d(step: String, message: String) {
        Log.d(TAG, "[$step] $message")
        record("D", step, message)
    }

    fun v(step: String, message: String) {
        if (VERBOSE) {
            Log.v(TAG, "[$step] $message")
            record("V", step, message)
        }
    }

    fun w(step: String, message: String, t: Throwable? = null) {
        if (t == null) Log.w(TAG, "[$step] $message") else Log.w(TAG, "[$step] $message", t)
        record("W", step, message + (t?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    fun e(step: String, message: String, t: Throwable? = null) {
        if (t == null) Log.e(TAG, "[$step] $message") else Log.e(TAG, "[$step] $message", t)
        record("E", step, message + (t?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    /**
     * 打印当前配置快照。
     *
     * 排查「配了规则却没反应」时，第一件要确认的事就是
     * 「服务读到的配置，和设置页写进去的是不是同一份」。
     * SharedPreferences 是文件级的，跨进程/跨组件读取偶发不同步，
     * 把快照打出来能一次性排除这类怀疑。
     */
    fun dumpConfig(context: Context, reason: String) {
        try {
            val config: FocusGuardConfig = FocusGuardStore.load(context)
            d("CONFIG", "快照（$reason）")
            d("CONFIG", "  enabled=${config.enabled}")
            d("CONFIG", "  规则数=${config.rules.size}")
            config.rules.forEach { rule ->
                d(
                    "CONFIG",
                    "  · ${rule.appName} (${rule.packageName}) " +
                        "enabled=${rule.enabled} usable=${rule.isUsable} " +
                        "globalBlock=${rule.globalBlock} " +
                        "keywords=${rule.keywords} " +
                        "maskTop=${rule.maskTopPercent}% maskBottom=${rule.maskBottomPercent}% " +
                        "passes=${rule.dailyPassLimit} minutes=${rule.leisureMinutes}"
                )
            }
            d("CONFIG", "  已用次数=${config.passUsed}")
            d("CONFIG", "  放开截止=${config.leisureUntil}")
            d("CONFIG", "  计数日期=${config.passDate}（今天=${FocusGuardStore.today()}）")
        } catch (t: Throwable) {
            e("CONFIG", "读取配置快照失败", t)
        }
    }

    /** 判断某个包名在配置里的状况，用于「为什么这个应用没被挡」的精确定位。 */
    fun explainRule(context: Context, packageName: String) {
        try {
            val config = FocusGuardStore.load(context)
            if (!config.enabled) {
                d("RULE", "$packageName → 总开关未开启")
                return
            }
            val rule = config.ruleFor(packageName)
            if (rule == null) {
                d("RULE", "$packageName → 不在规则表内（规则表：${config.rules.map { it.packageName }}）")
                return
            }
            d(
                "RULE",
                "$packageName → 命中规则「${rule.appName}」enabled=${rule.enabled} " +
                    "usable=${rule.isUsable} globalBlock=${rule.globalBlock} keywords=${rule.keywords}"
            )
        } catch (t: Throwable) {
            e("RULE", "解释规则失败", t)
        }
    }
}
