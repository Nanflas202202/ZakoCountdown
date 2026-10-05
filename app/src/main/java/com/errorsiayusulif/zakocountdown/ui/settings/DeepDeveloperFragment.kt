// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/DeepDeveloperFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.LogRecorder

class DeepDeveloperFragment : ZakoPreferenceFragment() {

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.deep_developer_preferences, rootKey)
        // 获取 Application 级别的 PreferenceManager
        preferenceManager = (requireActivity().application as ZakoCountdownApplication).preferenceManager

        // 普通开发者模式开关
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.DEV_MODE_ENTRY_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setEnableEnterDevMode(newValue as Boolean)
            true // 【修复】必须返回 true
        }

        // 关于页面彩蛋开关
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.ABOUT_EASTER_EGG_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setAboutEasterEggEnabled(newValue as Boolean)
            true // 【修复】必须返回 true
        }

        // 实时日志
        findPreference<Preference>("nav_to_live_logs")?.setOnPreferenceClickListener {
            val action = DeepDeveloperFragmentDirections.actionDeepDeveloperFragmentToLogReaderFragment(null)
            findNavController().navigate(action)
            true
        }

        // 日志持久化开关
        findPreference<SwitchPreferenceCompat>(PreferenceKeys.LOG_PERSISTENCE_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            preferenceManager.setLogPersistenceEnabled(enabled)
            if (enabled) {
                LogRecorder.startRecording(requireContext())
            } else {
                LogRecorder.stopRecording()
            }
            true
        }

        // 历史日志列表
        findPreference<Preference>("nav_to_archived_logs")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_deepDeveloperFragment_to_logFileListFragment)
            true
        }
    }
}
