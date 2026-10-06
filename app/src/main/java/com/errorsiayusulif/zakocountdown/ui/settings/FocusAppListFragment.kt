// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/FocusAppListFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.FocusAppRule
import com.errorsiayusulif.zakocountdown.data.FocusGuardStore
import com.errorsiayusulif.zakocountdown.databinding.FragmentFocusAppListBinding
import com.errorsiayusulif.zakocountdown.databinding.ItemFocusAppBinding
import com.errorsiayusulif.zakocountdown.utils.InstalledAppInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 受管制应用列表。
 *
 * 用普通 Fragment + RecyclerView 而不是 PreferenceFragmentCompat：
 * 每一条需要「应用图标 + 真实应用名 + 关键词摘要」，
 * Preference 体系拿不到应用图标，硬做要自定义 Preference + 布局，反而更绕。
 */
class FocusAppListFragment : Fragment() {

    private var _binding: FragmentFocusAppListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RuleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFocusAppListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RuleAdapter()
        binding.focusAppList.layoutManager = LinearLayoutManager(requireContext())
        binding.focusAppList.adapter = adapter

        // 行与行之间留 1dp **透明**分隔，与设置页「隐藏分隔线」的做法一致：
        // 既有呼吸感，又不会被画线打断整块列表的连续感。
        binding.focusAppList.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                requireContext(),
                LinearLayoutManager.VERTICAL
            ).apply {
                setDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                )
            }
        )

        binding.fabAddApp.setOnClickListener { showAppPicker() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        binding.focusAppList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun refresh() {
        val rules = FocusGuardStore.load(requireContext()).rules
        adapter.submit(rules)

        val empty = rules.isEmpty()
        binding.focusEmptyView.visibility = if (empty) View.VISIBLE else View.GONE
    }

    /** 列出可启动应用供选择；已在规则表里的应用不再重复列出。 */
    private fun showAppPicker() {
        val context = requireContext()
        val existing = FocusGuardStore.load(context).rules.map { it.packageName }.toSet()

        val candidates = InstalledAppInfo.launchableApps(context)
            .filterNot { it.packageName in existing }

        if (candidates.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.focus_pick_app)
                .setMessage(R.string.focus_no_apps_found)
                .setPositiveButton(R.string.common_ok, null)
                .show()
            return
        }

        val labels = candidates.map { it.label }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.focus_pick_app)
            .setItems(labels) { _, which ->
                val picked = candidates[which]
                // 新规则默认：1 次免答题、每次 2 分钟、无关键词也无整应用遮挡
                // → isUsable 为 false，会提示用户去补关键词，不会静默失效
                val rule = FocusAppRule(
                    packageName = picked.packageName,
                    appName = picked.label,
                    keywords = emptyList(),
                    globalBlock = false,
                    dailyPassLimit = 1,
                    leisureMinutes = 2,
                    enabled = true
                )
                FocusGuardStore.upsertRule(context, rule)
                refresh()
                openDetail(picked.packageName)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun openDetail(packageName: String) {
        val action = FocusAppListFragmentDirections
            .actionFocusAppListFragmentToFocusRuleDetailFragment(packageName)
        findNavController().navigate(action)
    }

    // ========================================================================
    // Adapter
    // ========================================================================

    private inner class RuleAdapter : RecyclerView.Adapter<RuleAdapter.Holder>() {

        private val items = mutableListOf<FocusAppRule>()

        fun submit(rules: List<FocusAppRule>) {
            items.clear()
            items.addAll(rules)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemFocusAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        inner class Holder(private val itemBinding: ItemFocusAppBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(rule: FocusAppRule) {
                val context = requireContext()

                // 应用可能已被卸载：此时 getApplicationInfo 会失败，
                // labelOf / iconOf 已做兜底（名称退回包名、图标为 null）
                itemBinding.tvAppName.text = rule.appName.ifBlank {
                    InstalledAppInfo.labelOf(context, rule.packageName)
                }
                itemBinding.ivAppIcon.setImageDrawable(InstalledAppInfo.iconOf(context, rule.packageName))

                itemBinding.tvRuleSummary.text = buildSummary(rule)
                itemBinding.tvDisabledBadge.visibility = if (rule.isUsable) View.GONE else View.VISIBLE

                itemBinding.root.setOnClickListener { openDetail(rule.packageName) }
            }

            /** 「关键词：推荐 · 每日 2 次 · 每次 2 分钟 · 遮挡 90%」一行摘要。 */
            private fun buildSummary(rule: FocusAppRule): String {
                val parts = mutableListOf<String>()
                if (rule.globalBlock) {
                    parts += getString(R.string.focus_rule_global_block)
                } else if (rule.keywords.isNotEmpty()) {
                    parts += getString(R.string.focus_summary_keywords, rule.keywordsText())
                } else {
                    // 既没关键词也没开整应用遮挡 → 明确说明「这条规则不会生效」
                    parts += getString(R.string.focus_rule_needs_keyword)
                }
                if (rule.dailyPassLimit > 0) {
                    parts += getString(R.string.focus_summary_passes, rule.dailyPassLimit)
                }
                parts += getString(R.string.focus_summary_minutes, rule.leisureMinutes)
                // 只有真的让出了边距才显示覆盖率，否则纯噪声（默认就是遮满）
                if (rule.maskTopPercent > 0 || rule.maskBottomPercent > 0) {
                    parts += getString(
                        R.string.focus_summary_coverage,
                        (rule.maskCoverageRatio * 100).toInt()
                    )
                }
                return parts.joinToString(" · ")
            }
        }
    }
}
