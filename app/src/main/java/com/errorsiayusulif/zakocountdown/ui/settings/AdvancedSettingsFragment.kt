// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/AdvancedSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper

class AdvancedSettingsFragment : PreferenceFragmentCompat() {
    private lateinit var appPreferenceManager: PreferenceManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)
        appPreferenceManager = PreferenceManager(requireContext())

        // [新增] 导航模式切换监听
        findPreference<ListPreference>("key_nav_mode")?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveNavMode(newValue as String)
            activity?.recreate()
            true
        }

        findPreference<Preference>("select_important_apps")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_advancedSettingsFragment_to_appSelectorFragment)
            true
        }

        findPreference<Preference>("permission_accessibility")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_global_permissionsFragment)
            true
        }
        findPreference<Preference>("permission_overlay")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_global_permissionsFragment)
            true
        }
        findPreference<ListPreference>("key_home_layout_mode")?.setOnPreferenceChangeListener { _, _ ->
            // 切换布局模式后，必须重启 Activity 才能重新应用 Drawer/BottomNav 的显隐状态
            activity?.recreate()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val pref = findPreference<Preference>("permission_accessibility") ?: return
        val isEnabled = AccessibilityStatusHelper.isAccessibilityServiceEnabled(requireContext())
        pref.summary = if (isEnabled) "已开启" else "未开启，点击跳转至权限中心开启"
    }
}