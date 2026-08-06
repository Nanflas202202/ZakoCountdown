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

        findPreference<SwitchPreferenceCompat>("key_show_system_apps")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setShowSystemApps(newValue as Boolean)
            true
        }

        findPreference<SwitchPreferenceCompat>("key_miui_fix_override")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setMiuiFixOverride(newValue as Boolean)
            activity?.recreate()
            true
        }

        findPreference<SwitchPreferenceCompat>("key_unlock_global_alpha")?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setUnlockGlobalAlpha(newValue as Boolean)
            true
        }

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

        // --- 【新增】强制重启 OOBE ---
        findPreference<Preference>("force_relaunch_oobe")?.setOnPreferenceClickListener {
            // 将标志位设为 false，以防用户在中途退出
            preferenceManager.setOobeCompleted(false)
            val intent = android.content.Intent(requireContext(), OobeActivity::class.java)
            startActivity(intent)
            requireActivity().finish() // 关闭当前界面
            true
        }

        // --- 【修复】清除所有本地设置并重启应用 ---
        findPreference<Preference>("clear_all_preferences")?.setOnPreferenceClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠危险操作")
                .setMessage("这将清除所有的主题、界面、弹窗等本地设置（您的日程和日程本数据不会丢失）。\n\n应用将会立即重启，确定吗？")
                .setPositiveButton("清除并重启") { _, _ ->
                    // 1. 清空 SharedPreferences
                    requireContext().getSharedPreferences("zako_prefs", android.content.Context.MODE_PRIVATE).edit().clear().apply()

                    // 2. 使用 Intent 启动主 Activity 并清空任务栈
                    val intent = requireActivity().packageManager.getLaunchIntentForPackage(requireActivity().packageName)

                    // 【核心修复】使用 ?.let 安全解包 Intent
                    intent?.let { safeIntent ->
                        safeIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(safeIntent)
                    }

                    // 3. 强杀进程确保彻底重置
                    Runtime.getRuntime().exit(0)
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }
}