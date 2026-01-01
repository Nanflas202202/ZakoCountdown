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

    fun saveWidgetImageUri(appWidgetId: Int, uriString: String?) {
        prefs.edit().putString("${WIDGET_PREF_IMG_PREFIX}${appWidgetId}", uriString).apply()
    }

    fun getWidgetImageUri(appWidgetId: Int): String? {
        return prefs.getString("${WIDGET_PREF_IMG_PREFIX}${appWidgetId}", null)
    }

    // 保存微件自定义颜色 (Hex)
    fun saveWidgetColor(appWidgetId: Int, colorHex: String?) {
        prefs.edit().putString("${WIDGET_PREF_COLOR_PREFIX}${appWidgetId}", colorHex).apply()
    }

    fun getWidgetColor(appWidgetId: Int): String? {
        return prefs.getString("${WIDGET_PREF_COLOR_PREFIX}${appWidgetId}", null)
    }

    // 保存微件透明度 (0-100)
    fun saveWidgetAlpha(appWidgetId: Int, alpha: Int) {
        prefs.edit().putInt("${WIDGET_PREF_ALPHA_PREFIX}${appWidgetId}", alpha).apply()
    }

    fun getWidgetAlpha(appWidgetId: Int): Int {
        // 默认透明度 40% (即 60% 透明)
        return prefs.getInt("${WIDGET_PREF_ALPHA_PREFIX}${appWidgetId}", 40)
    }
    // 图片透明度 (0-100)
    fun saveWidgetImageAlpha(appWidgetId: Int, alpha: Int) {
        prefs.edit().putInt("${WIDGET_PREF_IMG_ALPHA_PREFIX}${appWidgetId}", alpha).apply()
    }
    fun getWidgetImageAlpha(appWidgetId: Int): Int {
        return prefs.getInt("${WIDGET_PREF_IMG_ALPHA_PREFIX}${appWidgetId}", 100) // 默认不透明
    }

    // 遮罩开关
    fun saveWidgetShowScrim(appWidgetId: Int, show: Boolean) {
        prefs.edit().putBoolean("${WIDGET_PREF_SHOW_SCRIM_PREFIX}${appWidgetId}", show).apply()
    }
    fun getWidgetShowScrim(appWidgetId: Int): Boolean {
        return prefs.getBoolean("${WIDGET_PREF_SHOW_SCRIM_PREFIX}${appWidgetId}", true) // 默认开启
    }

    // 遮罩透明度 (0-100)
    fun saveWidgetScrimAlpha(appWidgetId: Int, alpha: Int) {
        prefs.edit().putInt("${WIDGET_PREF_SCRIM_ALPHA_PREFIX}${appWidgetId}", alpha).apply()
    }
    fun getWidgetScrimAlpha(appWidgetId: Int): Int {
        return prefs.getInt("${WIDGET_PREF_SCRIM_ALPHA_PREFIX}${appWidgetId}", 40) // 默认 40%
    }
    // --- 【新功能】遮罩设置 ---
    fun getScrimColorMode(): String {
        return prefs.getString(KEY_SCRIM_COLOR_MODE, SCRIM_MODE_THEME) ?: SCRIM_MODE_THEME
    }

    fun getScrimAlpha(): Int {
        // 默认透明度 25% (0-100)
        return prefs.getInt(KEY_SCRIM_ALPHA, 25)
    }
    fun saveScrimCustomColor(colorHex: String) {
        prefs.edit().putString(KEY_SCRIM_CUSTOM_COLOR, colorHex).apply()
    }
    fun getScrimCustomColor(): String {
        // 默认深灰色
        return prefs.getString(KEY_SCRIM_CUSTOM_COLOR, "#333333") ?: "#333333"
    }

    // 开发者选项：解锁全局透明度
    fun setUnlockGlobalAlpha(unlock: Boolean) {
        prefs.edit().putBoolean(KEY_UNLOCK_GLOBAL_ALPHA, unlock).apply()
    }
    fun isGlobalAlphaUnlocked(): Boolean {
        return prefs.getBoolean(KEY_UNLOCK_GLOBAL_ALPHA, false)
    }
    // 设置是否允许通过“创建5个日程”进入开发者模式
    fun setEnableEnterDevMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_ENTER_DEV_MODE, enabled).apply()
    }

    fun isEnableEnterDevMode(): Boolean {
        // 【关键】默认关闭 (false)，需要通过暗码开启
        return prefs.getBoolean(KEY_ENABLE_ENTER_DEV_MODE, false)
    }
    fun setLogPersistenceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOG_PERSISTENCE, enabled).apply()
    }

    fun isLogPersistenceEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOG_PERSISTENCE, false)
    }
    // --- 【修复】补全彩蛋开关方法 ---
    fun setAboutEasterEggEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_ABOUT_EASTER_EGG, enabled).apply()
    }

    fun isAboutEasterEggEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLE_ABOUT_EASTER_EGG, true)
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
        private const val WIDGET_PREF_IMG_PREFIX = "widget_img_"
        private const val WIDGET_PREF_COLOR_PREFIX = "widget_color_"
        private const val WIDGET_PREF_ALPHA_PREFIX = "widget_alpha_"
        private const val WIDGET_PREF_IMG_ALPHA_PREFIX = "widget_img_alpha_"
        private const val WIDGET_PREF_SHOW_SCRIM_PREFIX = "widget_show_scrim_"
        private const val WIDGET_PREF_SCRIM_ALPHA_PREFIX = "widget_scrim_alpha_"
        private const val KEY_SCRIM_COLOR_MODE = "key_scrim_color_mode"
        private const val KEY_SCRIM_ALPHA = "key_scrim_alpha"

        private const val KEY_SCRIM_CUSTOM_COLOR = "key_scrim_custom_color"
        private const val KEY_UNLOCK_GLOBAL_ALPHA = "key_unlock_global_alpha"

        const val SCRIM_MODE_THEME = "theme"
        const val SCRIM_MODE_BLACK = "black"
        const val SCRIM_MODE_WHITE = "white"
        const val SCRIM_MODE_CUSTOM = "custom" // 新增自定义模式
        private const val KEY_ENABLE_ENTER_DEV_MODE = "key_enable_enter_dev_mode"
        private const val KEY_LOG_PERSISTENCE = "key_log_persistence"
        private const val KEY_ENABLE_ABOUT_EASTER_EGG = "key_enable_about_easter_egg"
    }
}