// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/FocusGuardStore.kt
package com.errorsiayusulif.zakocountdown.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 防沉迷配置的本地存储。
 *
 * 存储位置是**独立的 prefs 文件**（`zako_focus_guard`），不进 `zako_prefs`：
 *   · 规则列表会随用户添加的应用增长，混进去会让设置备份/导出跟着膨胀；
 *   · 免答题次数这类「当日计数」属于运行时状态，不该跟配置一起被导出。
 *
 * 相比 anti-addiction 的 MMKV 实现，这里刻意用 SharedPreferences + Gson：
 * 本工程其余部分都是这套，少一个依赖、少一个迁移面。
 */
object FocusGuardStore {

    private const val TAG = "FocusGuardStore"

    private const val PREFS_FILE = "zako_focus_guard"
    private const val KEY_CONFIG = "focus_guard_config"

    /**
     * 已经执行过的数据迁移版本。
     *
     * 用单调递增的整数，每次新增迁移 +1，读到小于当前值就依次补跑。
     */
    private const val KEY_MIGRATION = "focus_guard_migration"
    private const val CURRENT_MIGRATION = 1

    private val gson = Gson()

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** 今天日期字符串，用于跨天重置免答题次数。 */
    fun today(): String = dayFormat.format(Date())

    /**
     * 读取配置。
     *
     * 顺带处理**跨天重置**：如果存档里的 [FocusGuardConfig.passDate] 不是今天，
     * 就把已用次数清零。放在读取路径上而不是定时任务里，是因为这个应用不常驻，
     * 用闹钟维护「每天零点重置」反而更不可靠。
     */
    fun load(context: Context): FocusGuardConfig {
        migrateIfNeeded(context)

        val raw = prefs(context).getString(KEY_CONFIG, null)
        val config = try {
            if (raw.isNullOrBlank()) FocusGuardConfig()
            else gson.fromJson(raw, FocusGuardConfig::class.java) ?: FocusGuardConfig()
        } catch (t: Throwable) {
            // 存档损坏不能让设置页崩掉，退回默认配置
            Log.e(TAG, "读取防沉迷配置失败，按默认处理", t)
            FocusGuardConfig()
        }

        val today = today()
        return if (config.passDate != today) {
            // 新的一天：清空当日已用次数与已过期的放开截止时间
            val now = System.currentTimeMillis()
            val rolled = config.copy(
                passUsed = emptyMap(),
                passDate = today,
                leisureUntil = config.leisureUntil.filterValues { it > now }
            )
            save(context, rolled)
            rolled
        } else {
            config
        }
    }

    fun save(context: Context, config: FocusGuardConfig) {
        try {
            prefs(context).edit().putString(KEY_CONFIG, gson.toJson(config)).apply()
        } catch (t: Throwable) {
            Log.e(TAG, "写入防沉迷配置失败", t)
        }
    }

    // ========================================================================
    // 数据迁移
    // ========================================================================

    /**
     * 一次性数据迁移。
     *
     * **迁移 1**：把已有规则的 `maskTopPercent` 从 0 提到
     * [DEFAULT_MASK_TOP_PERCENT]（10%）。
     *
     * 为什么需要它：默认值只在**新建**规则时生效，而 `maskTopPercent = 0`
     * 已经被序列化进了已有用户的 JSON 存档（Gson 遇到 JSON 里存在的字段
     * 就不会用 Kotlin 的默认值）。若不迁移，老用户永远停在 0%，
     * 表现为「更新了但顶部还是没留出来」。
     *
     * 只在 `== 0` 时改写：用户若主动选过 0（明确不想留边距），
     * 迁移跑过一次后就不会再动他。
     */
    private fun migrateIfNeeded(context: Context) {
        try {
            val p = prefs(context)
            val done = p.getInt(KEY_MIGRATION, 0)
            if (done >= CURRENT_MIGRATION) return

            val raw = p.getString(KEY_CONFIG, null)
            if (!raw.isNullOrBlank()) {
                var config = gson.fromJson(raw, FocusGuardConfig::class.java)
                if (config != null && config.rules.isNotEmpty()) {
                    config = config.copy(
                        rules = config.rules.map { rule ->
                            if (rule.maskTopPercent == 0) {
                                rule.copy(maskTopPercent = DEFAULT_MASK_TOP_PERCENT)
                            } else {
                                rule
                            }
                        }
                    )
                    p.edit().putString(KEY_CONFIG, gson.toJson(config)).apply()
                    Log.i(TAG, "迁移 1：已把 ${config.rules.size} 条规则的顶部留白设为 $DEFAULT_MASK_TOP_PERCENT%")
                }
            }

            p.edit().putInt(KEY_MIGRATION, CURRENT_MIGRATION).apply()
        } catch (t: Throwable) {
            // 迁移失败不该阻断读取：宁可保持旧值，也不能让设置页打不开
            Log.e(TAG, "防沉迷配置迁移失败，保持原值", t)
        }
    }

    // ========================================================================
    // 常用操作
    // ========================================================================

    /** 首次开启时写入预置规则（只在规则为空时填，不会覆盖用户已有的改动）。 */
    fun seedDefaultsIfEmpty(context: Context): FocusGuardConfig {
        val config = load(context)
        if (config.rules.isNotEmpty()) return config
        val seeded = config.copy(rules = FocusGuardDefaults.PRESETS)
        save(context, seeded)
        return seeded
    }

    /** 新增或更新一条规则（按包名匹配）。 */
    fun upsertRule(context: Context, rule: FocusAppRule): FocusGuardConfig {
        val config = load(context)
        val existing = config.rules.indexOfFirst { it.packageName == rule.packageName }
        val rules = config.rules.toMutableList().apply {
            if (existing >= 0) this[existing] = rule else add(rule)
        }
        val updated = config.copy(rules = rules)
        save(context, updated)
        return updated
    }

    fun removeRule(context: Context, packageName: String): FocusGuardConfig {
        val updated = load(context).let { config ->
            config.copy(
                rules = config.rules.filterNot { it.packageName == packageName },
                leisureUntil = config.leisureUntil - packageName,
                passUsed = config.passUsed - packageName
            )
        }
        save(context, updated)
        return updated
    }

    fun setEnabled(context: Context, enabled: Boolean): FocusGuardConfig {
        val updated = load(context).copy(enabled = enabled)
        save(context, updated)
        return updated
    }

    // ========================================================================
    // 免答题配额
    // ========================================================================

    /**
     * 今天该应用还剩几次免答题机会。
     * 返回 0 表示今天已经用完，只能答题解锁（或直接离开）。
     */
    fun remainingPasses(context: Context, rule: FocusAppRule): Int {
        val used = load(context).passUsed[rule.packageName] ?: 0
        return (rule.dailyPassLimit - used).coerceAtLeast(0)
    }

    /**
     * 消耗一次免答题机会，并记录放开截止时间。
     *
     * 返回是否消耗成功 —— 配额用尽时返回 false，调用方据此改走答题流程。
     */
    fun consumePass(context: Context, rule: FocusAppRule): Boolean {
        val config = load(context)
        val used = config.passUsed[rule.packageName] ?: 0
        if (used >= rule.dailyPassLimit) return false

        val until = System.currentTimeMillis() + rule.leisureMinutes * 60_000L
        val updated = config.copy(
            passUsed = config.passUsed + (rule.packageName to (used + 1)),
            leisureUntil = config.leisureUntil + (rule.packageName to until),
            passDate = today()
        )
        save(context, updated)
        Log.d(TAG, "${rule.appName} 使用免答题，放开至 $until")
        return true
    }

    /**
     * 记录一次答题通过（不消耗配额），并放开同样时长。
     *
     * 与 [consumePass] 的区别：答题通过不占用每日免答题次数 ——
     * 用户付出了脑力，不该再扣配额。
     */
    fun grantByChallenge(context: Context, rule: FocusAppRule): Long {
        val config = load(context)
        val until = System.currentTimeMillis() + rule.leisureMinutes * 60_000L
        val updated = config.copy(
            leisureUntil = config.leisureUntil + (rule.packageName to until),
            passDate = today()
        )
        save(context, updated)
        Log.d(TAG, "${rule.appName} 答题通过，放开至 $until")
        return until
    }

    /** 该应用当前是否处于「已放开」状态。 */
    fun isLeisureActive(context: Context, packageName: String): Boolean {
        val until = load(context).leisureUntil[packageName] ?: 0L
        return until > System.currentTimeMillis()
    }

    /** 提前结束放开状态（用户主动点「立即恢复遮挡」）。 */
    fun endLeisure(context: Context, packageName: String): FocusGuardConfig {
        val updated = load(context).let { it.copy(leisureUntil = it.leisureUntil - packageName) }
        save(context, updated)
        return updated
    }
}
