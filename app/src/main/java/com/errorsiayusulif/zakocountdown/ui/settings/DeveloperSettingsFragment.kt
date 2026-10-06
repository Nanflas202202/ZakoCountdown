// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/DeveloperSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.SystemUtils

class DeveloperSettingsFragment : ZakoPreferenceFragment() {

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.developer_preferences, rootKey)
        preferenceManager = PreferenceManager(requireContext())

        findPreference<SwitchPreferenceCompat>(PreferenceKeys.SHOW_SYSTEM_APPS)?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setShowSystemApps(newValue as Boolean)
            true
        }

        findPreference<SwitchPreferenceCompat>(PreferenceKeys.MIUI_FIX_DISABLED)?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setMiuiFixOverride(newValue as Boolean)
            activity?.recreate()
            true
        }

        findPreference<SwitchPreferenceCompat>(PreferenceKeys.CARD_ALPHA_UNLOCKED)?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setUnlockGlobalAlpha(newValue as Boolean)
            true
        }

        findPreference<SwitchPreferenceCompat>(PreferenceKeys.MD1_THEME_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
            preferenceManager.setEnableMd1Theme(newValue as Boolean)
            activity?.recreate()
            true
        }

        findPreference<Preference>("current_rom")?.summary = SystemUtils.getRomName().uppercase()

        findPreference<ListPreference>(PreferenceKeys.POPUP_MODE)?.setOnPreferenceChangeListener { _, newValue ->
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

        // --- 防沉迷诊断 ---
        // 从「高级设置 → 防沉迷 → 诊断」搬到这里：只有排查问题才用得上，
        // 对普通用户是噪声。报告组装逻辑在 FocusDiagnostics 里，与本页解耦。
        findPreference<Preference>("focus_view_log")?.setOnPreferenceClickListener {
            FocusDiagnostics.showLogDialog(this)
            true
        }

        findPreference<Preference>("focus_dump_config")?.setOnPreferenceClickListener {
            FocusDiagnostics.dumpConfigAndShow(this, "用户在调试台手动触发")
            true
        }

        // --- 【修复】清除所有本地设置并重启应用 ---
        findPreference<Preference>("clear_all_preferences")?.setOnPreferenceClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dev_clear_preferences_dialog_title)
                .setMessage(R.string.dev_clear_preferences_dialog_message)
                .setPositiveButton(R.string.dev_clear_preferences_confirm) { _, _ ->
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
                .setNegativeButton(R.string.common_cancel, null)
                .show()
            true
        }

        // --- 【v0.9.1】构建标识：升级代号 / 构建指纹 ---
        // 这是全应用唯一展示升级代号的地方。点击复制，方便反馈问题时附上。
        findPreference<Preference>("upgrade_code_display")?.apply {
            summary = getString(
                R.string.dev_upgrade_code_value,
                BuildConfig.UPGRADE_CODE,
                BuildConfig.UPGRADE_CODE_LABEL
            )
            setOnPreferenceClickListener {
                val clipboard = requireContext()
                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "ZakoUpgradeCode",
                        "${BuildConfig.UPGRADE_CODE_LABEL} / ${BuildConfig.UPGRADE_CODE} / ${BuildConfig.BUILD_ID}"
                    )
                )
                android.widget.Toast.makeText(requireContext(), R.string.dev_upgrade_code_copied, android.widget.Toast.LENGTH_SHORT).show()
                true
            }
        }

        findPreference<Preference>("build_id_display")?.apply {
            summary = BuildConfig.BUILD_ID
            setOnPreferenceClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dev_upgrade_code_dialog_title)
                    .setMessage(
                        getString(
                            R.string.dev_upgrade_code_dialog_message,
                            BuildConfig.UPGRADE_CODE,
                            BuildConfig.UPGRADE_CODE_LABEL,
                            BuildConfig.BUILD_ID
                        )
                    )
                    .setPositiveButton(R.string.common_ok, null)
                    .show()
                true
            }
        }

        // --- 【v0.9.1】更新节点池：摘要只显示「有几条」，绝不把 URL 明文铺在设置页上 ---
        updateUpdateUrlsSummaries()
        findPreference<androidx.preference.EditTextPreference>(PreferenceKeys.UPDATE_SOURCE_NODES)
            ?.setOnPreferenceChangeListener { _, _ ->
                // 延迟到偏好写入完成后再刷新摘要
                android.os.Handler(android.os.Looper.getMainLooper()).post { updateUpdateUrlsSummaries() }
                true
            }
        findPreference<Preference>("restore_default_update_urls")?.setOnPreferenceClickListener {
            preferenceManager.setUpdateUrls(preferenceManager.getBuiltInUpdateUrls().joinToString("\n"))
            updateUpdateUrlsSummaries()
            android.widget.Toast.makeText(requireContext(), R.string.common_operation_success, android.widget.Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到本页都重新压一遍摘要，防止 EditTextPreference 把 URL 明文写回 summary
        updateUpdateUrlsSummaries()
    }

    /**
     * 让「更新节点池」相关条目都有可读摘要，但**不暴露任何 URL**：
     *  - 节点池本身：只报数量
     *  - 在线同步开关：报同步后有几条可用节点
     *
     * 注意：EditTextPreference 默认会把持久化的文本直接写进 summary（这就是设置页里
     * 曾经铺满一长串 URL 的原因），所以这里必须每次在它之后覆盖 summary。
     */
    private fun updateUpdateUrlsSummaries() {
        val count = preferenceManager.getUpdateUrls().size

        findPreference<Preference>(PreferenceKeys.UPDATE_SOURCE_NODES)?.summary =
            if (count == 0) getString(R.string.dev_update_urls_empty)
            else getString(R.string.dev_update_urls_count, count)

        findPreference<Preference>(PreferenceKeys.UPDATE_SOURCE_SYNC_ENABLED)?.summary =
            if (count == 0) getString(R.string.dev_sync_update_urls_summary)
            else getString(R.string.dev_sync_update_urls_summary_count, count)
    }
}
