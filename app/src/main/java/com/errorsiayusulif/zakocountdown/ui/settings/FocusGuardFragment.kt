// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/FocusGuardFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.FocusGuardStore
import com.errorsiayusulif.zakocountdown.services.AppOpenDetectorService
import com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper

/**
 * 防沉迷总设置（高级功能 → 防沉迷）。
 *
 * 这一页刻意只放两件事：总开关、进入应用列表。
 * 具体规则（关键词 / 整应用遮挡 / 每日免答题次数 / 每次解锁时长）在下一级编辑。
 */
class FocusGuardFragment : ZakoPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.focus_guard_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findPreference<SwitchPreferenceCompat>("focus_enabled")?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean

            // 没有无障碍权限就不允许开启。
            //
            // 为什么在这里硬拦：防沉迷的**每一条**判定都来自无障碍事件，
            // 未授权时打开这个开关既不会遮挡、也不会报错，
            // 用户只会觉得「功能是坏的」。与其让人去猜，
            // 不如在启用这一刻就说清原因并送到授权页。
            if (enabled && !AccessibilityStatusHelper.isAccessibilityServiceEnabled(requireContext())) {
                Toast.makeText(
                    requireContext(),
                    R.string.perm_accessibility_required_for_feature,
                    Toast.LENGTH_LONG
                ).show()
                openAccessibilitySettings()
                return@setOnPreferenceChangeListener false
            }

            FocusGuardStore.setEnabled(requireContext(), enabled)
            // 首次开启时补上预置规则，否则用户会面对一个空列表不知从何下手
            if (enabled) {
                FocusGuardStore.seedDefaultsIfEmpty(requireContext())
            }
            // 关掉总开关时立刻撤下可能正显示的浮层
            AppOpenDetectorService.resetFocusGuard(requireContext())
            true
        }

        findPreference<Preference>("focus_manage_apps")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_focusGuardFragment_to_focusAppListFragment)
            true
        }

        // 诊断入口已移到「调试台」（开发者选项 → 调试）。
        // 本页只保留「总开关 + 进入应用列表」两件事，保持轻盈。
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    /**
     * 刷新界面状态。
     *
     * 核心是**把「无障碍服务没开」这件事显式摆出来**：
     * 防沉迷完全依赖无障碍服务派发事件，服务没开时规则配得再对也不会有任何反应，
     * 而现象和「功能坏了」一模一样。之前这条前提只写在开关的副标题里，
     * 太容易被忽略（实际就有用户因此以为功能失效）。
     * 现在遇到这种情况会置顶一条红色警告项，并且点一下直达系统无障碍设置。
     */
    private fun refreshState() {
        val context = requireContext()
        val config = FocusGuardStore.load(context)

        val accessibilityOn = AccessibilityStatusHelper.isAccessibilityServiceEnabled(context)

        // ---- 置顶的阻塞警告 ----
        val warning = findPreference<Preference>("focus_accessibility_warning")
        if (!accessibilityOn) {
            // 首次进入时动态插入；已存在则复用
            if (warning == null) {
                val created = Preference(context).apply {
                    key = "focus_accessibility_warning"
                    title = getString(R.string.focus_accessibility_missing_title)
                    summary = getString(R.string.focus_accessibility_missing_summary)
                    setOnPreferenceClickListener {
                        openAccessibilitySettings()
                        true
                    }
                }
                // order 设为负值确保排在最前，且不与既有 order 冲突
                created.order = -1000
                preferenceScreen?.addPreference(created)
            } else {
                warning.summary = getString(R.string.focus_accessibility_missing_summary)
            }
        } else {
            // 服务已开启 → 移除警告
            warning?.let { preferenceScreen?.removePreference(it) }
        }

        findPreference<SwitchPreferenceCompat>("focus_enabled")?.apply {
            // 未授权时把开关本身置灰：让人一眼看出「现在开不了」，
            // 而不是点一下才被弹回。配合上面的置顶警告，原因和出路都在同一屏。
            isEnabled = accessibilityOn

            if (!accessibilityOn) {
                // 顺带把存量的「已开启」状态落回 false。
                // 否则会出现「开关显示已开启、实际完全不生效」的矛盾状态 ——
                // 这正是之前让人误判为「功能坏了」的原因。
                if (config.enabled) {
                    FocusGuardStore.setEnabled(context, false)
                    AppOpenDetectorService.resetFocusGuard(context)
                }
                isChecked = false
            } else {
                isChecked = config.enabled
            }

            summary = if (accessibilityOn) {
                getString(R.string.focus_master_switch_summary)
            } else {
                getString(R.string.focus_need_accessibility)
            }
        }

        findPreference<Preference>("focus_manage_apps")?.summary =
            if (config.rules.isEmpty()) {
                getString(R.string.focus_manage_apps_summary_empty)
            } else {
                getString(R.string.focus_manage_apps_summary, config.rules.size)
            }
    }

    /** 直达系统无障碍设置页，省得用户自己翻。 */
    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (t: Throwable) {
            Toast.makeText(requireContext(), R.string.focus_open_settings_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
