// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/PreferenceManager.kt

package com.errorsiayusulif.zakocountdown.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.os.Build

class PreferenceManager(context: Context) {

    // --- 【核心修复】 ---
    // 声明为一个 lateinit var，表示它是一个稍后会被初始化的变量
    // --- 【核心修复】恢复为最简单的、默认的 SharedPreferences 初始化方式 ---
    // 并确保 context 是 applicationContext，防止内存泄漏
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 删除 lateinit 和 init 代码块



    fun saveTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getTheme(): String {
        return prefs.getString(KEY_THEME, THEME_M3) ?: THEME_M3
    }

    fun saveAccentColor(color: String) {
        prefs.edit().putString(KEY_ACCENT_COLOR, color).apply()
    }

    fun getAccentColor(): String {
        return prefs.getString(KEY_ACCENT_COLOR, ACCENT_MONET) ?: ACCENT_MONET
    }

    fun saveImportantApps(selectedApps: Set<String>) {
        prefs.edit().putStringSet(KEY_IMPORTANT_APPS, selectedApps).apply()
    }

    fun getImportantApps(): Set<String> {
        return prefs.getStringSet(KEY_IMPORTANT_APPS, emptySet()) ?: emptySet()
    }

    fun setShowSystemApps(shouldShow: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, shouldShow).apply()
    }

    fun getShowSystemApps(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
    }

    fun setMiuiFixOverride(override: Boolean) {
        prefs.edit().putBoolean(KEY_MIUI_FIX_OVERRIDE, override).apply()
    }

    fun isMiuiFixOverridden(): Boolean {
        return prefs.getBoolean(KEY_MIUI_FIX_OVERRIDE, false)
    }

    fun setEnableMd1Theme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_MD1_THEME, enabled).apply()
    }

    fun isMd1ThemeEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLE_MD1_THEME, false)
    }

    fun saveWidgetEventId(appWidgetId: Int, eventId: Long) {
        prefs.edit().putLong("${WIDGET_PREF_PREFIX}${appWidgetId}", eventId).apply()
    }

    fun getWidgetEventId(appWidgetId: Int): Long {
        return prefs.getLong("${WIDGET_PREF_PREFIX}${appWidgetId}", -1L)
    }

    fun deleteWidgetEventId(appWidgetId: Int) {
        prefs.edit().remove("${WIDGET_PREF_PREFIX}${appWidgetId}").apply()
    }

    fun saveWidgetBackground(appWidgetId: Int, backgroundType: String) {
        prefs.edit().putString("${WIDGET_PREF_BG_PREFIX}${appWidgetId}", backgroundType).apply()
    }

    fun getWidgetBackground(appWidgetId: Int): String {
        return prefs.getString("${WIDGET_PREF_BG_PREFIX}${appWidgetId}", "transparent") ?: "transparent"
    }

    fun isPermanentNotificationEnabled(): Boolean {
        val isEnabled = prefs.getBoolean("enable_permanent_notification", false)
        Log.d("PrefManager", "Reading 'enable_permanent_notification': value is $isEnabled")
        return isEnabled
    }

    fun setPopupMode(mode: String) {
        prefs.edit().putString(KEY_POPUP_MODE, mode).apply()
    }

    fun getPopupMode(): String {
        return prefs.getString(KEY_POPUP_MODE, POPUP_MODE_AUTO) ?: POPUP_MODE_AUTO
    }
    fun saveHomepageWallpaperUri(uriString: String?) {
        prefs.edit().putString(KEY_HOMEPAGE_WALLPAPER, uriString).apply()
    }

    fun getHomepageWallpaperUri(): String? {
        return prefs.getString(KEY_HOMEPAGE_WALLPAPER, null)
    }
    fun saveReminderTime(timeValue: String) {
        prefs.edit().putString(KEY_REMINDER_TIME, timeValue).apply()
    }

    fun getReminderTime(): String {
        return prefs.getString(KEY_REMINDER_TIME, REMINDER_TIME_1_DAY) ?: REMINDER_TIME_1_DAY
    }

    fun isPopupReminderEnabled(): Boolean {
        return prefs.getBoolean("enable_popup_reminder", true)
    }


    companion object {
        private const val PREFS_NAME = "zako_prefs"
        private const val KEY_THEME = "key_theme"
        const val THEME_M1 = "MD1"
        const val THEME_M2 = "M2"
        const val THEME_M3 = "M3"
        private const val KEY_ACCENT_COLOR = "key_accent_color"
        const val ACCENT_MONET = "MONET"
        const val ACCENT_PINK = "PINK"
        const val ACCENT_BLUE = "BLUE"
        private const val KEY_POPUP_MODE = "key_popup_mode"
        const val POPUP_MODE_AUTO = "auto"
        const val POPUP_MODE_ACTIVITY = "activity"
        const val POPUP_MODE_WINDOW = "window"
        private const val KEY_IMPORTANT_APPS = "important_apps_list"
        private const val KEY_SHOW_SYSTEM_APPS = "key_show_system_apps"
        private const val KEY_MIUI_FIX_OVERRIDE = "key_miui_fix_override"
        private const val KEY_ENABLE_MD1_THEME = "key_enable_md1_theme"
        private const val WIDGET_PREF_PREFIX = "widget_event_id_"
        private const val WIDGET_PREF_BG_PREFIX = "widget_bg_"
        private const val KEY_HOMEPAGE_WALLPAPER = "key_homepage_wallpaper"
        private const val KEY_REMINDER_TIME = "key_reminder_time"
        const val REMINDER_TIME_NONE = "none"
        const val REMINDER_TIME_1_DAY = "1_day"
        const val REMINDER_TIME_3_DAYS = "3_days"
        const val REMINDER_TIME_1_WEEK = "1_week"
    }
}