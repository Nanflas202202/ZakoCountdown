// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/AdvancedSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper
import com.errorsiayusulif.zakocountdown.utils.NavModeHelper

class AdvancedSettingsFragment : ZakoPreferenceFragment() {
    private lateinit var appPreferenceManager: PreferenceManager

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine.applyToPreferenceFragment(this)
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)
        appPreferenceManager = PreferenceManager(requireContext())

        // 1. 导航模式切换监听
        findPreference<ListPreference>(PreferenceKeys.APP_LAYOUT_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveNavMode(newValue as String)
            activity?.recreate()
            true
        }

        // 2. 选择应用名单
        findPreference<Preference>("select_important_apps")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_advancedSettingsFragment_to_appSelectorFragment)
            true
        }

        // 3. 权限入口 (统一导航)
        findPreference<Preference>("permission_accessibility")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_global_permissionsFragment)
            true
        }
        findPreference<Preference>("permission_overlay")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_global_permissionsFragment)
            true
        }

        // 4. 布局模式切换监听
        findPreference<ListPreference>(PreferenceKeys.HOME_LAYOUT_MODE)?.setOnPreferenceChangeListener { _, _ ->
            // 切换布局模式后，必须重启 Activity 才能重新应用 Drawer/BottomNav 的显隐状态
            activity?.recreate()
            true
        }

        // ==========================================
        // 5. 新增：更换应用桌面图标监听
        // ==========================================
        findPreference<ListPreference>(PreferenceKeys.APP_ICON_ALIAS)?.setOnPreferenceChangeListener { _, newValue ->
            val aliasName = newValue as String
            // 调用我们写的 IconSwitchHelper
            com.errorsiayusulif.zakocountdown.utils.IconSwitchHelper.switchIcon(requireContext(), aliasName)

            android.widget.Toast.makeText(
                requireContext(),
                R.string.adv_app_icon_changed,
                android.widget.Toast.LENGTH_LONG
            ).show()
            true
        }

        // 防沉迷入口：进入二级页面
        findPreference<Preference>("focus_guard_entry")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_advancedSettingsFragment_to_focusGuardFragment)
            true
        }

        // ==========================================
        // 6. 开屏弹窗提醒：必须有无障碍权限才能启用
        // ==========================================
        // 开屏提醒依赖无障碍服务监听「切换到了哪个应用」。
        // 未授权时打开这个开关**不会报错，只是永远不会触发** ——
        // 这种「静默无效」比直接拒绝更难排查，所以在启用这一步就拦下来，
        // 并直接把用户送去授权页。
        findPreference<androidx.preference.SwitchPreferenceCompat>(PreferenceKeys.POPUP_REMINDER_ENABLED)
            ?.setOnPreferenceChangeListener { _, newValue ->
                val wantOn = newValue as Boolean
                if (wantOn && !AccessibilityStatusHelper.isAccessibilityServiceEnabled(requireContext())) {
                    Toast.makeText(
                        requireContext(),
                        R.string.perm_accessibility_required_for_feature,
                        Toast.LENGTH_LONG
                    ).show()
                    findNavController().navigate(R.id.action_global_permissionsFragment)
                    return@setOnPreferenceChangeListener false
                }
                true
            }

        // 导航方式一变，「自动隐藏导航栏」是否出现要立刻跟着变，
        // 所以在这里挂监听（onResume 只在回到页面时触发，切选项不会触发）。
        findPreference<ListPreference>(PreferenceKeys.APP_LAYOUT_MODE)?.setOnPreferenceChangeListener { _, _ ->
            // 值写入发生在回调之后，延到下一帧再刷新可见性
            view?.post { updateNavModePreferenceState() }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateNavModePreferenceState()
        refreshPopupReminderState()
    }

    /**
     * 开屏弹窗提醒开关的状态要与无障碍权限保持一致。
     *
     * 失去权限时 [MainActivity] 会把偏好写回 `false`（见 checkAccessibilityAndPopup），
     * 但设置页自身的控件状态不会自动跟着变 —— 回到这一页时必须重新读一次，
     * 否则会看到「开关还是打开的」，与实际行为不符。
     *
     * 同时把开关本身置灰：没有权限时它确实开不了，
     * 让人一眼看出来比点了再被弹回更好。
     */
    private fun refreshPopupReminderState() {
        val pref = findPreference<androidx.preference.SwitchPreferenceCompat>(
            PreferenceKeys.POPUP_REMINDER_ENABLED
        ) ?: return

        val accessibilityOn = AccessibilityStatusHelper.isAccessibilityServiceEnabled(requireContext())
        val manager = PreferenceManager(requireContext())

        pref.isEnabled = accessibilityOn

        if (!accessibilityOn) {
            // 兜底：若因某种原因（例如权限刚被撤销、MainActivity 还没走到）
            // 偏好仍是 true，这里也落回 false，保证存储与界面一致。
            if (manager.isPopupReminderEnabled()) {
                manager.setPopupReminderEnabled(false)
            }
            pref.isChecked = false
            pref.summary = getString(R.string.popup_reminder_needs_accessibility)
        } else {
            pref.isChecked = manager.isPopupReminderEnabled()
            pref.summary = getString(R.string.adv_enable_popup_summary)
        }
    }

    /**
     * 导航相关设置项的可见性。
     *
     * 规则：
     *  1. 紧凑模式下导航栏整体不生效 -> 导航方式设置项置灰；
     *  2. 「自动隐藏导航栏」是底部导航栏的**子开关**：
     *     只有当前导航方式 = 底部导航栏时才**显示**，选侧滑抽屉时直接隐藏。
     */
    private fun updateNavModePreferenceState() {
        val navPref = findPreference<ListPreference>(PreferenceKeys.APP_LAYOUT_MODE) ?: return
        val appPreferenceManager = PreferenceManager(requireContext())
        val layoutMode = appPreferenceManager.getHomeLayoutMode()
        val compact = layoutMode == PreferenceManager.HOME_LAYOUT_COMPACT

        navPref.isEnabled = !compact
        navPref.summary = if (compact) {
            getString(R.string.adv_nav_mode_compact_locked)
        } else {
            navPref.entry
        }

        // 「自动隐藏导航栏」只在导航方式 = 底部导航栏时出现
        findPreference<androidx.preference.SwitchPreferenceCompat>(PreferenceKeys.AUTO_HIDE_NAV_BAR)?.let { pref ->
            pref.isVisible = !compact && NavModeHelper.isBottomNav(navPref.value)
            pref.summary = getString(R.string.adv_auto_hide_nav_summary)
        }
    }

    private fun updateAccessibilityStatus() {
        val pref = findPreference<Preference>("permission_accessibility") ?: return
        val isEnabled = AccessibilityStatusHelper.isAccessibilityServiceEnabled(requireContext())
        pref.summary = if (isEnabled) getString(R.string.common_enabled) else getString(R.string.perm_status_disabled)
    }
}
