// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/LocaleHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import java.util.Locale

/**
 * Central helper for in-app language selection.
 *
 * How it works
 * ------------
 *  * The selection is stored in the app's own SharedPreferences under [KEY_APP_LANGUAGE],
 *    so it survives reboots and is independent from the system locale.
 *  * The same value is mirrored into [AppCompatDelegate.setApplicationLocales], which is the
 *    API Android 13+ surfaces in "System settings → Apps → ZakoCountdown → Language".
 *  * [wrap] re-applies the locale to a Context so every Activity / Service / View inflater
 *    uses the chosen language even on devices where the platform per-app locale is unavailable.
 *
 * Adding a language
 * -----------------
 *  1. Create `res/values-<locale>/strings.xml` (e.g. `values-ja`).
 *  2. Add the locale to `res/xml/locales_config.xml`.
 *  3. Add one entry to the `language_entries` / `language_values` arrays
 *     (see `res/values/arrays.xml`).
 *  Nothing else in the code base needs to change.
 */
object LocaleHelper {

    /** SharedPreferences file that also holds every other user setting. */
    private const val PREFS_NAME = PreferenceKeys.PREFS_FILE

    /** Public because it is mirrored into SharedPreferences by the settings screen. */
    const val KEY_APP_LANGUAGE = PreferenceKeys.APP_LANGUAGE

    /** Sentinel meaning "follow the system language". */
    const val LANGUAGE_SYSTEM = "system"

    /** Languages the app currently ships translations for. */
    val SUPPORTED_LOCALES: List<String> = listOf("en", "zh-CN")

    /**
     * The language the user picked, or [LANGUAGE_SYSTEM] when they never chose one.
     */
    fun getSavedLanguage(context: Context): String {
        return readSaved(context)
    }

    /**
     * 把任意语言标签规范化成「设置里存在的取值」。
     * 例如系统返回的 `zh-Hans-CN` 或某个我们还没有翻译的语言，都会被归为「跟随系统」，
     * 这样语言选择列表不会出现「匹配不到任何条目」的空选状态。
     */
    fun normalizeLanguageTag(tag: String?): String {
        if (tag.isNullOrBlank() || tag == LANGUAGE_SYSTEM) return LANGUAGE_SYSTEM
        SUPPORTED_LOCALES.firstOrNull { it.equals(tag, ignoreCase = true) }?.let { return it }
        // 允许 "en-US" 之类的区域变体回退到其基础语言
        val base = tag.substringBefore('-')
        return SUPPORTED_LOCALES.firstOrNull { it.substringBefore('-').equals(base, ignoreCase = true) }
            ?: LANGUAGE_SYSTEM
    }

    private fun readSaved(context: Context): String {
        val fromPrefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, null)
        if (!fromPrefs.isNullOrBlank()) return fromPrefs

        // Fall back to whatever the platform per-app locale already holds, so a language
        // chosen from the system UI is still reflected inside the app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val platformTag = context.getSystemService(android.app.LocaleManager::class.java)
                ?.applicationLocales?.get(0)?.toLanguageTag()
            if (!platformTag.isNullOrBlank()) return platformTag
        }
        return LANGUAGE_SYSTEM
    }

    /**
     * Resolves the language tag that should actually be applied, or `null` for "follow system".
     */
    fun resolveLanguageTag(context: Context): String? {
        val saved = readSaved(context)
        return if (saved.isBlank() || saved == LANGUAGE_SYSTEM) null else saved
    }

    /**
     * Persists the selection, pushes it to the platform / AppCompat per-app locale and
     * hot-swaps the resources of the running application.
     */
    fun setLanguage(context: Context, languageTag: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, languageTag)
            .apply()

        applyApplicationLocales(languageTag)
    }

    /**
     * Re-applies the persisted language. Call once from [android.app.Application.onCreate].
     */
    fun syncFromStorage(context: Context) {
        applyApplicationLocales(readSaved(context))
    }

    private fun applyApplicationLocales(languageTag: String) {
        val locales = if (languageTag.isBlank() || languageTag == LANGUAGE_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        // AppCompatDelegate handles the API 33 LocaleManager path itself and falls back to a
        // configuration override on older releases.
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Returns a Context whose resources are forced to the selected language.
     * Safe to call with any Context, including ones that are already wrapped.
     */
    fun wrap(context: Context): Context {
        val tag = resolveLanguageTag(context) ?: return context

        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isEmpty()) return context

        Locale.setDefault(locale)

        val base = context.resources.configuration
        val config = Configuration(base).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
}
