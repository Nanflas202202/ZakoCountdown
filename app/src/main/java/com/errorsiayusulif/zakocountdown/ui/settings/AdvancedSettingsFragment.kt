// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/AdvancedSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.utils.AccessibilityStatusHelper

class AdvancedSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)

        findPreference<Preference>("select_important_apps")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_advancedSettingsFragment_to_appSelectorFragment)
            true
        }

        // 权限入口的逻辑将由 PermissionsFragment 统一处理，这里只做导航
        findPreference<Preference>("enable_accessibility")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_global_permissionsFragment)
            true
        }
        findPreference<Preference>("permission_overlay")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_global_permissionsFragment)
            true
        }
    }
    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun updateAccessibilityStatus() {
        val pref = findPreference<Preference>("enable_accessibility") ?: return
        val isEnabled = AccessibilityStatusHelper.isAccessibilityServiceEnabled(requireContext())
        pref.summary = if (isEnabled) "已开启" else "未开启，点击跳转至权限中心开启"
    }
}