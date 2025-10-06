// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/PersonalizationFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager

class PersonalizationFragment : PreferenceFragmentCompat() {

    private lateinit var appPreferenceManager: PreferenceManager

    // 图片选择器的启动器
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    // 获取持久化权限
                    val contentResolver = requireActivity().contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    // 保存URI字符串
                    appPreferenceManager.saveHomepageWallpaperUri(uri.toString())
                    Toast.makeText(requireContext(), "背景图已设置", Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Toast.makeText(requireContext(), "无法获取图片权限，请在系统设置中授予", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "zako_prefs"
        setPreferencesFromResource(R.xml.personalization_preferences, rootKey)
        appPreferenceManager = PreferenceManager(requireContext())

        setupListeners()
    }

    private fun setupListeners() {
        // 主题切换
        findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveTheme(newValue as String)
            activity?.recreate()
            true
        }

        // 强调色切换
        findPreference<ListPreference>("accent_color")?.setOnPreferenceChangeListener { _, newValue ->
            appPreferenceManager.saveAccentColor(newValue as String)
            activity?.recreate()
            true
        }

        // 更换背景图
        findPreference<Preference>("change_wallpaper")?.setOnPreferenceClickListener {
            pickImageLauncher.launch("image/*")
            true
        }

        // 移除背景图
        findPreference<Preference>("remove_wallpaper")?.setOnPreferenceClickListener {
            appPreferenceManager.saveHomepageWallpaperUri(null)
            Toast.makeText(requireContext(), "背景图已移除", Toast.LENGTH_SHORT).show()
            true
        }
    }
}