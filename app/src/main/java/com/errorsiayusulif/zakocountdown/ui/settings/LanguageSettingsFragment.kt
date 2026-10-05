// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/settings/LanguageSettingsFragment.kt
package com.errorsiayusulif.zakocountdown.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.utils.LocaleHelper
import com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine

/**
 * 语言选择页面。
 *
 * 数据流：ListPreference 的 entryValues 是 BCP-47 语言标签（或 "system"），
 * 选择后写入 SharedPreferences 并立即通过 [LocaleHelper] 应用到全应用。
 * 新增语言只需在 res/values/arrays.xml 里追加 language_entries / language_values 条目。
 */
class LanguageSettingsFragment : ZakoPreferenceFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MtbThemeEngine.applyToPreferenceFragment(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = PreferenceManager.PREFS_NAME_FOR_PREFERENCES
        setPreferencesFromResource(R.xml.language_preferences, rootKey)

        val languagePref = findPreference<ListPreference>(LocaleHelper.KEY_APP_LANGUAGE) ?: return

        // 让 AppCompat 在 Activity 重建时能自动恢复这个页面的状态
        languagePref.isPersistent = false
        languagePref.value = PreferenceManager(requireContext()).getAppLanguage()

        languagePref.setOnPreferenceChangeListener { _, newValue ->
            val tag = newValue as? String ?: return@setOnPreferenceChangeListener false
            LocaleHelper.setLanguage(requireContext(), tag)

            // 立即用新语言重建当前界面（Activity + Fragment 都会重新走 attachBaseContext）
            activity?.recreate()
            true
        }
    }
}
