// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/DeveloperSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.SystemUtils

class DeveloperSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.developer_preferences, rootKey)
        preferenceManager = PreferenceManager(requireContext())

        // 显示系统应用
        findPreference<SwitchPreferenceCompat>("key_show_system_apps")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setShowSystemApps(newValue as Boolean)
            true
        }

        // 停用MIUI修正
        findPreference<SwitchPreferenceCompat>("key_miui_fix_override")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setMiuiFixOverride(newValue as Boolean)
            activity?.recreate()
            true
        }

        // --- 【核心修复】解锁所有卡片透明度 ---
        findPreference<SwitchPreferenceCompat>("key_unlock_global_alpha")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setUnlockGlobalAlpha(newValue as Boolean)
            true
        }

        // 启用实验性MD1
        findPreference<SwitchPreferenceCompat>("key_enable_md1_theme")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setEnableMd1Theme(newValue as Boolean)
            activity?.recreate()
            true
        }

        findPreference<Preference>("current_rom")?.summary = SystemUtils.getRomName().uppercase()

        findPreference<ListPreference>("key_popup_mode")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setPopupMode(newValue as String)
            true
        }
        // --- 【修复】日程本功能开关 ---
        // 务必确保 key 与 developer_preferences.xml 中的一致
        findPreference<SwitchPreferenceCompat>("key_enable_agenda_book")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setAgendaBookEnabled(newValue as Boolean)
            // 强制重启 Activity 以刷新导航菜单
            activity?.recreate()
            true
        }
    }
}