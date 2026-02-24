// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/SettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // 不再需要设置 sharedPreferencesName，因为这个页面没有可保存的设置了
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<Preference>("nav_to_personalization")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_personalizationFragment)
            true
        }
        findPreference<Preference>("nav_to_notifications")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_notificationSettingsFragment)
            true
        }
        findPreference<Preference>("nav_to_advanced")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_advancedSettingsFragment)
            true
        }
        findPreference<Preference>("nav_to_permissions")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_permissionsFragment)
            true
        }
        findPreference<Preference>("nav_to_about")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_aboutFragment)
            true
        }
        findPreference<Preference>("nav_to_backup_restore")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.backupRestoreFragment) // 或者具体的 action
            // 由于我们没在 nav_graph 里写从 settings 过去的 action，可以直接用目标 ID
            // findNavController().navigate(R.id.backupRestoreFragment)
            true
        }
    }
}