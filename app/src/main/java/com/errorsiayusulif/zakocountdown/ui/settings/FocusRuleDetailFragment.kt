// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/FocusRuleDetailFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.FOCUS_DAILY_PASS_OPTIONS
import com.errorsiayusulif.zakocountdown.data.FOCUS_LEISURE_MINUTE_OPTIONS
import com.errorsiayusulif.zakocountdown.data.FOCUS_MASK_OFFSET_OPTIONS
import com.errorsiayusulif.zakocountdown.data.FocusAppRule
import com.errorsiayusulif.zakocountdown.data.FocusGuardStore
import com.errorsiayusulif.zakocountdown.services.AppOpenDetectorService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast

/**
 * 单条防沉迷规则的详情编辑。
 *
 * 偏好项**在代码里动态构建**，而不是写 XML：
 * 所有值都存在 [FocusGuardStore] 的一份 JSON 里，用 XML 声明就得为它写一个
 * 自定义 `PreferenceDataStore`；动态构建能直接读写配置对象，更直白也更好维护。
 *
 * 值一经改动就立刻落盘并通知无障碍服务刷新状态 —— 防沉迷这类功能
 * 「改完要重启才生效」是不可接受的。
 */
class FocusRuleDetailFragment : ZakoPreferenceFragment() {

    private val args: FocusRuleDetailFragmentArgs by navArgs()

    private lateinit var context2: android.content.Context

    /** 当前编辑中的规则快照，所有改动都基于它。 */
    private var rule: FocusAppRule? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // 不加载 XML（focus_rule_preferences 是空的），全部动态构建
        setPreferencesFromResource(R.xml.focus_rule_preferences, rootKey)
        context2 = requireContext()

        rule = FocusGuardStore.load(context2).ruleFor(args.packageName)
        if (rule == null) {
            // 规则可能已被删除（例如从列表页删掉后返回栈里还留着这一页）
            Toast.makeText(context2, R.string.focus_rule_missing, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        buildPreferences(rule!!)
    }

    private fun buildPreferences(initial: FocusAppRule) {
        val screen = preferenceScreen ?: return

        // ---------- 生效开关 ----------
        val enableCategory = PreferenceCategory(context2).apply {
            title = initial.appName
        }
        screen.addPreference(enableCategory)

        enableCategory.addPreference(
            SwitchPreferenceCompat(context2).apply {
                key = "rule_enabled"
                title = getString(R.string.focus_rule_enabled)
                isChecked = initial.enabled
                setOnPreferenceChangeListener { _, newValue ->
                    update { it.copy(enabled = newValue as Boolean) }
                    true
                }
            }
        )

        // ---------- 遮挡方式 ----------
        val blockCategory = PreferenceCategory(context2).apply {
            title = getString(R.string.focus_rule_keywords)
        }
        screen.addPreference(blockCategory)

        blockCategory.addPreference(
            SwitchPreferenceCompat(context2).apply {
                key = "rule_global_block"
                title = getString(R.string.focus_rule_global_block)
                summary = getString(R.string.focus_rule_global_block_summary)
                isChecked = initial.globalBlock
                setOnPreferenceChangeListener { _, newValue ->
                    update { it.copy(globalBlock = newValue as Boolean) }
                    // 整应用遮挡开关会影响关键词项是否有意义，刷新一下说明文案
                    refreshBlockSummary()
                    true
                }
            }
        )

        blockCategory.addPreference(
            EditTextPreference(context2).apply {
                key = "rule_keywords"
                title = getString(R.string.focus_rule_keywords)
                dialogTitle = getString(R.string.focus_rule_keywords)
                // 关键词是逗号分隔的自由文本，不用多选列表：
                // 用户可能需要「荐」这种单字词，预置列表框不住
                text = initial.keywordsText()

                // ⚠️ 这里**不能**设 summaryProvider。
                // EditTextPreference 的父类构造函数已经装好了 SummaryProvider，
                // 之后再对它 setSummary() 会直接抛
                // IllegalStateException: Preference already has a SummaryProvider set.
                // （这个崩溃实际踩过。）
                // 反正我们本来就要手写上下文相关的说明（关键词被忽略 / 还没配 / 列出关键词），
                // 自动摘要反而是多余的。摘要统一由 refreshBlockSummary() 维护。

                setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_TEXT
                    editText.hint = getString(R.string.focus_rule_keywords_hint)
                }
                setOnPreferenceChangeListener { _, newValue ->
                    val keywords = parseKeywords(newValue as String)
                    update { it.copy(keywords = keywords) }
                    refreshBlockSummary()
                    true
                }
            }
        )

        // ---------- 遮罩范围 ----------
        val maskCategory = PreferenceCategory(context2).apply {
            title = getString(R.string.focus_category_mask)
        }
        screen.addPreference(maskCategory)

        maskCategory.addPreference(
            ListPreference(context2).apply {
                key = "rule_mask_top"
                title = getString(R.string.focus_rule_mask_top)
                entries = FOCUS_MASK_OFFSET_OPTIONS.map { offsetLabel(it) }.toTypedArray()
                entryValues = FOCUS_MASK_OFFSET_OPTIONS.map { it.toString() }.toTypedArray()
                value = initial.maskTopPercent.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    val percent = (newValue as String).toIntOrNull() ?: 0
                    update { it.copy(maskTopPercent = percent) }
                    refreshMaskSummary()
                    true
                }
            }
        )

        maskCategory.addPreference(
            ListPreference(context2).apply {
                key = "rule_mask_bottom"
                title = getString(R.string.focus_rule_mask_bottom)
                entries = FOCUS_MASK_OFFSET_OPTIONS.map { offsetLabel(it) }.toTypedArray()
                entryValues = FOCUS_MASK_OFFSET_OPTIONS.map { it.toString() }.toTypedArray()
                value = initial.maskBottomPercent.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    val percent = (newValue as String).toIntOrNull() ?: 0
                    update { it.copy(maskBottomPercent = percent) }
                    refreshMaskSummary()
                    true
                }
            }
        )

        // ---------- 配额 ----------
        val quotaCategory = PreferenceCategory(context2).apply {
            title = getString(R.string.focus_category_prefs)
        }
        screen.addPreference(quotaCategory)

        quotaCategory.addPreference(
            ListPreference(context2).apply {
                key = "rule_daily_passes"
                title = getString(R.string.focus_rule_daily_passes)
                entries = FOCUS_DAILY_PASS_OPTIONS.map { passLabel(it) }.toTypedArray()
                entryValues = FOCUS_DAILY_PASS_OPTIONS.map { it.toString() }.toTypedArray()
                value = initial.dailyPassLimit.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    val limit = (newValue as String).toIntOrNull() ?: 1
                    update { it.copy(dailyPassLimit = limit) }
                    true
                }
            }
        )

        quotaCategory.addPreference(
            ListPreference(context2).apply {
                key = "rule_leisure_minutes"
                title = getString(R.string.focus_rule_leisure_minutes)
                entries = FOCUS_LEISURE_MINUTE_OPTIONS.map { getString(R.string.focus_minutes_option, it) }.toTypedArray()
                entryValues = FOCUS_LEISURE_MINUTE_OPTIONS.map { it.toString() }.toTypedArray()
                value = initial.leisureMinutes.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    val minutes = (newValue as String).toIntOrNull() ?: 2
                    update { it.copy(leisureMinutes = minutes) }
                    true
                }
            }
        )

        // ---------- 删除 ----------
        screen.addPreference(
            Preference(context2).apply {
                key = "rule_delete"
                title = getString(R.string.focus_rule_delete)
                setOnPreferenceClickListener {
                    confirmDelete()
                    true
                }
            }
        )
    }

    /**
     * 把改动写回存储并通知无障碍服务。
     *
     * 每次都整条 upsert（而不是改单个字段）—— 规则是值对象，
     * 整条替换能避免「部分字段更新」带来的状态不一致。
     */
    private fun update(transform: (FocusAppRule) -> FocusAppRule) {
        val current = rule ?: return
        val updated = transform(current).let { candidate ->
            // dailyPassLimit 允许为 0（表示完全不给免答题机会），
            // 这里只做下界保护，不设上界（选项本身已经限定了范围）
            candidate.copy(dailyPassLimit = candidate.dailyPassLimit.coerceAtLeast(0))
        }
        rule = updated
        FocusGuardStore.upsertRule(context2, updated)
        // 规则变了，让无障碍服务丢掉旧的内部状态（例如「已经挡过了」的标记）
        AppOpenDetectorService.resetFocusGuard(context2)
    }

    /** 关键词文本 → 列表：按中英文逗号/顿号/分号切分，去空去重。 */
    private fun parseKeywords(raw: String): List<String> {
        return raw.split(',', '，', '、', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun passLabel(count: Int): String =
        if (count == 0) getString(R.string.focus_passes_none)
        else escapePercent(getString(R.string.focus_passes_count, count))

    /** 让出比例：0 显示为「不留边距」，其余显示为百分比。 */
    private fun offsetLabel(percent: Int): String =
        if (percent == 0) getString(R.string.focus_offset_none)
        else escapePercent(getString(R.string.focus_offset_percent, percent))

    /**
     * 把字面量 `%` 转义成 `%%`。
     *
     * `ListPreference.getSummary()` 内部会执行 `String.format(summary, entry)`，
     * 而 `setValue()` 又会自动调 `setSummary()` —— 也就是说**任何**带 `%` 的摘要
     * 都会被当成格式串解析。像 `90%` 里的 `% ` 会被读成转换符 `o`
     * （`%o` 是八进制整数），直接抛
     * `IllegalFormatConversionException: o != java.lang.String`。
     *
     * 转义成 `%%` 之后，String.format 会把它们还原成一个字面量 `%`，
     * 显示效果不变。**凡是喂给 ListPreference 的文案（摘要与条目）都必须过这里。**
     */
    private fun escapePercent(text: String): String = text.replace("%", "%%")

    /**
     * 在尺寸项下方说明「实际遮挡多少」。
     *
     * 上下两个比例都会被 [FocusAppRule.maskCoverageRatio] 钳制到合计 80%，
     * 所以用户设了 20+20 却只看到 60% 时，需要一句话解释，否则会以为是 bug。
     */
    private fun refreshMaskSummary() {
        val current = rule ?: return
        val coverage = (current.maskCoverageRatio * 100).toInt()
        val summary = getString(R.string.focus_mask_coverage_summary, coverage)

        // 走 setSafeSummary：它内部会转义「90%」里的字面量 %
        findPreference<ListPreference>("rule_mask_top")?.let { setSafeSummary(it, summary) }
        findPreference<ListPreference>("rule_mask_bottom")?.let { setSafeSummary(it, summary) }
    }

    /** 关键词项在「整应用遮挡」开启时没有意义，把说明换掉以免误导。 */
    private fun refreshBlockSummary() {
        val current = rule ?: return
        val keywordsPref = findPreference<EditTextPreference>("rule_keywords") ?: return
        setSafeSummary(
            keywordsPref,
            if (current.globalBlock) {
                getString(R.string.focus_keywords_ignored)
            } else if (current.keywords.isEmpty()) {
                getString(R.string.focus_rule_needs_keyword)
            } else {
                current.keywordsText()
            }
        )
    }

    /**
     * 安全地设置摘要。这是本页**唯一**允许写摘要的入口。
     *
     * 踩过两个坑，都在这里统一挡掉：
     *
     * 1. `Preference.setSummary()` 在**已装有 SummaryProvider** 时会抛
     *    `IllegalStateException: Preference already has a SummaryProvider set.`
     *    `EditTextPreference` 的父类构造函数会自动装一个，我们是后手，只能先清掉。
     *
     * 2. `ListPreference.getSummary()` 与 `EditTextPreference.getSummary()` 都会把摘要
     *    交给 `String.format`。任何字面量 `%` 都会被当成格式符 ——
     *    「90%」里的 `% ` 会被读成八进制转换符 `o`，直接抛
     *    `IllegalFormatConversionException: o != java.lang.String`。
     *    而关键词是**用户自由输入**的，完全可能包含 `%`，所以这里必须无条件转义，
     *    不能只依赖调用方自觉。
     *
     * 转义成 `%%` 后，String.format 会把它还原成一个字面量 `%`，显示效果不变。
     */
    private fun setSafeSummary(preference: Preference, text: String) {
        // 清掉可能已存在的 Provider（只有会自动装的那种需要）
        (preference as? EditTextPreference)?.let {
            runCatching { it.summaryProvider = null }
        }
        preference.summary = text.replace("%", "%%")
    }

    private fun confirmDelete() {
        val current = rule ?: return
        MaterialAlertDialogBuilder(context2)
            .setTitle(R.string.focus_rule_delete)
            .setMessage(getString(R.string.focus_rule_delete_confirm, current.appName))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                FocusGuardStore.removeRule(context2, current.packageName)
                AppOpenDetectorService.resetFocusGuard(context2)
                findNavController().navigateUp()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // 首帧时 rule 可能还没准备好（见 onCreatePreferences 的空值分支）
        refreshBlockSummary()
        refreshMaskSummary()
    }
}
