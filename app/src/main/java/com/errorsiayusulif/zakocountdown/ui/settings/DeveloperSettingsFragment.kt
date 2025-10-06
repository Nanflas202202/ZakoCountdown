// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/DeveloperSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference // <-- 【修复】导入
import androidx.preference.Preference // <-- 【修复】导入
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.SystemUtils // <-- 【修复】导入

class DeveloperSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.developer_preferences, rootKey)
        preferenceManager = PreferenceManager(requireContext())

        findPreference<SwitchPreferenceCompat>("key_show_system_apps")?.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as? Boolean ?: false
            preferenceManager.setShowSystemApps(isEnabled)
            Log.d("DevSettings", "Show System Apps saved as: $isEnabled")
            true
        }

        findPreference<SwitchPreferenceCompat>("key_miui_fix_override")?.setOnPreferenceChangeListener { _, newValue ->
            val isEnabled = newValue as? Boolean ?: false
            preferenceManager.setMiuiFixOverride(isEnabled)
            Log.d("DevSettings", "MIUI Fix Override saved as: $isEnabled")
            // 当这个开关状态改变时，我们需要重启Activity来让设置页的UI刷新
            activity?.recreate()
            true
        }
        findPreference<SwitchPreferenceCompat>("key_enable_md1_theme")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setEnableMd1Theme(newValue as Boolean)
            // 重启Activity以应用新设置
            activity?.recreate()
            true
        }
        // 显示当前ROM
        findPreference<Preference>("current_rom")?.summary = SystemUtils.getRomName().uppercase()

        // 弹窗模式监听
        findPreference<ListPreference>("key_popup_mode")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setPopupMode(newValue as String)
            true
        }
    }
}