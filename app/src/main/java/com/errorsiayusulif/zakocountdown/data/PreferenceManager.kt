// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/PreferenceManager.kt
package com.errorsiayusulif.zakocountdown.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.errorsiayusulif.zakocountdown.utils.LocaleHelper

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var sharedPreferencesName: String = PREFS_NAME

    fun saveTheme(theme: String) {
        prefs.edit().putString(PreferenceKeys.THEME_MODE, theme).apply()
    }

    fun getTheme(): String {
        return prefs.getString(PreferenceKeys.THEME_MODE, THEME_M3) ?: THEME_M3
    }

    fun saveAccentColor(color: String) {
        prefs.edit().putString(PreferenceKeys.ACCENT_COLOR, color).apply()
    }

    // --- 【核心修改】智能获取颜色 ---
    fun getAccentColor(): String {
        val selectedColor = prefs.getString(PreferenceKeys.ACCENT_COLOR, ACCENT_MONET) ?: ACCENT_MONET
        val currentTheme = getTheme()

        // 如果用户选择了 Monet
        if (selectedColor == ACCENT_MONET) {
            // 1. 如果系统不支持 (Android 12 以下) -> 强制蓝色
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return ACCENT_BLUE
            }
            // 2. 如果当前主题是 MD1 或 MD2 (风格不兼容) -> 强制蓝色
            if (currentTheme == THEME_M1 || currentTheme == THEME_M2) {
                return ACCENT_BLUE
            }
        }
        return selectedColor
    }

    // ... 其他方法保持不变 (复制您之前的文件内容，这里仅列出核心修改) ...
    // 为节省篇幅，请确保包含 saveNavMode, getNavMode 等所有之前的 getter/setter

    /** 保存导航形态：底部导航栏 / 侧滑抽屉。 */
    fun saveNavMode(mode: String) {
        prefs.edit().putString(PreferenceKeys.APP_LAYOUT_MODE, mode).apply()
    }

    /**
     * 读取导航形态，默认底部导航栏。
     *
     * 兼容旧取值：v0.9.1 之前把「悬浮底部导航栏」单独当成一种导航形态（`floating`）。
     * 后来它被改造成底部导航栏的一个开关（自动隐藏），所以读到旧值时要迁移：
     * 形态落回 [NAV_MODE_BOTTOM]，同时把「自动隐藏」打开，
     * 这样老用户升级后看到的效果和之前一致。
     */
    fun getNavMode(): String {
        val stored = prefs.getString(PreferenceKeys.APP_LAYOUT_MODE, NAV_MODE_BOTTOM) ?: NAV_MODE_BOTTOM
        if (stored == NAV_MODE_LEGACY_FLOATING) {
            prefs.edit()
                .putString(PreferenceKeys.APP_LAYOUT_MODE, NAV_MODE_BOTTOM)
                .putBoolean(PreferenceKeys.AUTO_HIDE_NAV_BAR, true)
                .apply()
            return NAV_MODE_BOTTOM
        }
        return stored
    }

    /** 底部导航栏是否在向下滚动时自动隐藏（MD3 Expressive 的悬浮观感）。 */
    fun isAutoHideNavBar(): Boolean = prefs.getBoolean(PreferenceKeys.AUTO_HIDE_NAV_BAR, true)

    fun saveAutoHideNavBar(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.AUTO_HIDE_NAV_BAR, enabled).apply()
    }

    fun saveImportantApps(selectedApps: Set<String>) {
        prefs.edit().putStringSet(PreferenceKeys.POPUP_TARGET_APPS, selectedApps).apply()
    }

    fun getImportantApps(): Set<String> {
        return prefs.getStringSet(PreferenceKeys.POPUP_TARGET_APPS, emptySet()) ?: emptySet()
    }

    fun setShowSystemApps(shouldShow: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.SHOW_SYSTEM_APPS, shouldShow).apply()
    }

    fun getShowSystemApps(): Boolean {
        return prefs.getBoolean(PreferenceKeys.SHOW_SYSTEM_APPS, false)
    }

    fun setMiuiFixOverride(override: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.MIUI_FIX_DISABLED, override).apply()
    }

    fun isMiuiFixOverridden(): Boolean {
        return prefs.getBoolean(PreferenceKeys.MIUI_FIX_DISABLED, false)
    }

    fun setEnableMd1Theme(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.MD1_THEME_ENABLED, enabled).apply()
    }

    fun isMd1ThemeEnabled(): Boolean {
        return prefs.getBoolean(PreferenceKeys.MD1_THEME_ENABLED, false)
    }

    fun saveWidgetEventId(appWidgetId: Int, eventId: Long) {
        prefs.edit().putLong("${PreferenceKeys.WIDGET_EVENT_ID_PREFIX}${appWidgetId}", eventId).apply()
    }

    fun getWidgetEventId(appWidgetId: Int): Long {
        return prefs.getLong("${PreferenceKeys.WIDGET_EVENT_ID_PREFIX}${appWidgetId}", -1L)
    }

    fun deleteWidgetEventId(appWidgetId: Int) {
        prefs.edit().remove("${PreferenceKeys.WIDGET_EVENT_ID_PREFIX}${appWidgetId}").apply()
    }

    fun saveWidgetBackground(appWidgetId: Int, backgroundType: String) {
        prefs.edit().putString("${PreferenceKeys.WIDGET_BACKGROUND_PREFIX}${appWidgetId}", backgroundType).apply()
    }

    fun getWidgetBackground(appWidgetId: Int): String {
        return prefs.getString("${PreferenceKeys.WIDGET_BACKGROUND_PREFIX}${appWidgetId}", "transparent") ?: "transparent"
    }

    fun isPermanentNotificationEnabled(): Boolean {
        return prefs.getBoolean(PreferenceKeys.PERSISTENT_NOTIFICATION_ENABLED, false)
    }

    fun setPopupMode(mode: String) {
        prefs.edit().putString(PreferenceKeys.POPUP_MODE, mode).apply()
    }

    fun getPopupMode(): String {
        return prefs.getString(PreferenceKeys.POPUP_MODE, POPUP_MODE_AUTO) ?: POPUP_MODE_AUTO
    }
    fun saveHomepageWallpaperUri(uriString: String?) {
        prefs.edit().putString(PreferenceKeys.HOME_WALLPAPER_URI, uriString).apply()
    }

    fun getHomepageWallpaperUri(): String? {
        return prefs.getString(PreferenceKeys.HOME_WALLPAPER_URI, null)
    }
    fun saveReminderTime(timeValue: String) {
        prefs.edit().putString(PreferenceKeys.REMINDER_LEAD_TIME, timeValue).apply()
    }

    fun getReminderTime(): String {
        return prefs.getString(PreferenceKeys.REMINDER_LEAD_TIME, REMINDER_TIME_1_DAY) ?: REMINDER_TIME_1_DAY
    }

    fun isPopupReminderEnabled(): Boolean {
        return prefs.getBoolean(PreferenceKeys.POPUP_REMINDER_ENABLED, true)
    }

    fun saveWidgetImageUri(appWidgetId: Int, uriString: String?) {
        prefs.edit().putString("${PreferenceKeys.WIDGET_IMAGE_URI_PREFIX}${appWidgetId}", uriString).apply()
    }

    fun getWidgetImageUri(appWidgetId: Int): String? {
        return prefs.getString("${PreferenceKeys.WIDGET_IMAGE_URI_PREFIX}${appWidgetId}", null)
    }

    fun saveWidgetColor(appWidgetId: Int, colorHex: String?) {
        prefs.edit().putString("${PreferenceKeys.WIDGET_COLOR_PREFIX}${appWidgetId}", colorHex).apply()
    }

    fun getWidgetColor(appWidgetId: Int): String? {
        return prefs.getString("${PreferenceKeys.WIDGET_COLOR_PREFIX}${appWidgetId}", null)
    }

    fun saveWidgetAlpha(appWidgetId: Int, alpha: Int) {
        prefs.edit().putInt("${PreferenceKeys.WIDGET_ALPHA_PREFIX}${appWidgetId}", alpha).apply()
    }

    fun getWidgetAlpha(appWidgetId: Int): Int {
        return prefs.getInt("${PreferenceKeys.WIDGET_ALPHA_PREFIX}${appWidgetId}", 40)
    }

    fun saveWidgetImageAlpha(appWidgetId: Int, alpha: Int) {
        prefs.edit().putInt("${PreferenceKeys.WIDGET_IMAGE_ALPHA_PREFIX}${appWidgetId}", alpha).apply()
    }
    fun getWidgetImageAlpha(appWidgetId: Int): Int {
        return prefs.getInt("${PreferenceKeys.WIDGET_IMAGE_ALPHA_PREFIX}${appWidgetId}", 100)
    }

    fun saveWidgetShowScrim(appWidgetId: Int, show: Boolean) {
        prefs.edit().putBoolean("${PreferenceKeys.WIDGET_SHOW_SCRIM_PREFIX}${appWidgetId}", show).apply()
    }
    fun getWidgetShowScrim(appWidgetId: Int): Boolean {
        return prefs.getBoolean("${PreferenceKeys.WIDGET_SHOW_SCRIM_PREFIX}${appWidgetId}", true)
    }

    fun saveWidgetScrimAlpha(appWidgetId: Int, alpha: Int) {
        prefs.edit().putInt("${PreferenceKeys.WIDGET_SCRIM_ALPHA_PREFIX}${appWidgetId}", alpha).apply()
    }
    fun getWidgetScrimAlpha(appWidgetId: Int): Int {
        return prefs.getInt("${PreferenceKeys.WIDGET_SCRIM_ALPHA_PREFIX}${appWidgetId}", 40)
    }

    fun getScrimColorMode(): String {
        return prefs.getString(PreferenceKeys.SCRIM_COLOR_MODE, SCRIM_MODE_THEME) ?: SCRIM_MODE_THEME
    }

    fun getScrimAlpha(): Int {
        return prefs.getInt(PreferenceKeys.SCRIM_ALPHA, 25)
    }
    fun saveScrimCustomColor(colorHex: String) {
        prefs.edit().putString(PreferenceKeys.SCRIM_CUSTOM_COLOR, colorHex).apply()
    }
    fun getScrimCustomColor(): String {
        return prefs.getString(PreferenceKeys.SCRIM_CUSTOM_COLOR, "#333333") ?: "#333333"
    }

    fun setUnlockGlobalAlpha(unlock: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.CARD_ALPHA_UNLOCKED, unlock).apply()
    }
    fun isGlobalAlphaUnlocked(): Boolean {
        return prefs.getBoolean(PreferenceKeys.CARD_ALPHA_UNLOCKED, false)
    }

    fun setEnableEnterDevMode(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.DEV_MODE_ENTRY_ENABLED, enabled).apply()
    }

    fun isEnableEnterDevMode(): Boolean {
        return prefs.getBoolean(PreferenceKeys.DEV_MODE_ENTRY_ENABLED, false)
    }
    fun setLogPersistenceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.LOG_PERSISTENCE_ENABLED, enabled).apply()
    }

    fun isLogPersistenceEnabled(): Boolean {
        return prefs.getBoolean(PreferenceKeys.LOG_PERSISTENCE_ENABLED, false)
    }

    fun setAboutEasterEggEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.ABOUT_EASTER_EGG_ENABLED, enabled).apply()
    }

    fun isAboutEasterEggEnabled(): Boolean {
        return prefs.getBoolean(PreferenceKeys.ABOUT_EASTER_EGG_ENABLED, true)
    }
    // 在 PreferenceManager 类中添加
    fun setAgendaBookEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.AGENDA_BOOK_ENABLED, enabled).apply()
    }

    fun isAgendaBookEnabled(): Boolean {
        return prefs.getBoolean(PreferenceKeys.AGENDA_BOOK_ENABLED, true)
    }
    // --- 日程本视图记忆 ---
    fun setAgendaViewMode(isGrid: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.AGENDA_VIEW_IS_GRID, isGrid).apply()
    }

    fun isAgendaViewModeGrid(): Boolean {
        // 默认 true (网格)
        return prefs.getBoolean(PreferenceKeys.AGENDA_VIEW_IS_GRID, true)
    }

    // --- 开屏弹窗高级设置 ---
    fun setPopupDuration(seconds: Int) {
        prefs.edit().putInt(PreferenceKeys.POPUP_DURATION_SECONDS, seconds).apply()
    }

    fun getPopupDuration(): Int {
        // 默认 5 秒
        return prefs.getInt(PreferenceKeys.POPUP_DURATION_SECONDS, 5)
    }

    fun setPopupSkippable(skippable: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.POPUP_SKIPPABLE, skippable).apply()
    }

    fun isPopupSkippable(): Boolean {
        // 默认允许跳过
        return prefs.getBoolean(PreferenceKeys.POPUP_SKIPPABLE, true)
    }

    fun setPopupSkipDelay(seconds: Int) {
        prefs.edit().putInt(PreferenceKeys.POPUP_SKIP_DELAY_SECONDS, seconds).apply()
    }

    fun getPopupSkipDelay(): Int {
        // 默认延迟 0 秒 (立即出现关闭按钮)
        return prefs.getInt(PreferenceKeys.POPUP_SKIP_DELAY_SECONDS, 0)
    }
    fun setHomeLayoutMode(mode: String) {
        prefs.edit().putString(PreferenceKeys.HOME_LAYOUT_MODE, mode).apply()
    }

    fun getHomeLayoutMode(): String {
        return prefs.getString(PreferenceKeys.HOME_LAYOUT_MODE, HOME_LAYOUT_STANDARD) ?: HOME_LAYOUT_STANDARD
    }
    fun isLegacyThemeUnlockedInCompact(): Boolean {
        return prefs.getBoolean(PreferenceKeys.LEGACY_THEME_IN_COMPACT, false)
    }

    // ==========================================
    // v0.9.1 新增：侧滑栏顶部图像自定义
    // ==========================================
    fun saveDrawerHeaderImageUri(uriString: String?) {
        prefs.edit().putString(PreferenceKeys.DRAWER_HEADER_IMAGE_URI, uriString).apply()
    }

    fun getDrawerHeaderImageUri(): String? {
        return prefs.getString(PreferenceKeys.DRAWER_HEADER_IMAGE_URI, null)
    }

    fun setDrawerHeaderImageEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.DRAWER_HEADER_IMAGE_ENABLED, enabled).apply()
    }

    /** 默认开启：只要用户设置过图像就显示；没设置过时头部回退为纯色品牌样式。 */
    fun isDrawerHeaderImageEnabled(): Boolean {
        return prefs.getBoolean(PreferenceKeys.DRAWER_HEADER_IMAGE_ENABLED, true)
    }
    // 在 PreferenceManager 中添加
    fun saveDefaultBookCover(isImportantBook: Boolean, uriString: String?) {
        val key = if (isImportantBook) PreferenceKeys.DEFAULT_BOOK_COVER_IMPORTANT else PreferenceKeys.DEFAULT_BOOK_COVER_ALL
        prefs.edit().putString(key, uriString).apply()
    }

    fun getDefaultBookCover(isImportantBook: Boolean): String? {
        val key = if (isImportantBook) PreferenceKeys.DEFAULT_BOOK_COVER_IMPORTANT else PreferenceKeys.DEFAULT_BOOK_COVER_ALL
        return prefs.getString(key, null)
    }
    // --- 【新增】无障碍提示标记 ---
    fun setHasPromptedAccessibility(prompted: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.ACCESSIBILITY_GUIDE_PROMPTED, prompted).apply()
    }

    fun hasPromptedAccessibility(): Boolean {
        return prefs.getBoolean(PreferenceKeys.ACCESSIBILITY_GUIDE_PROMPTED, false)
    }


    // --- 新增：默认日程本的透明度支持 ---
    fun saveDefaultBookAlpha(isImportantBook: Boolean, alpha: Float) {
        val key = if (isImportantBook) PreferenceKeys.DEFAULT_BOOK_ALPHA_IMPORTANT else PreferenceKeys.DEFAULT_BOOK_ALPHA_ALL
        prefs.edit().putFloat(key, alpha).apply()
    }

    fun getDefaultBookAlpha(isImportantBook: Boolean): Float {
        val key = if (isImportantBook) PreferenceKeys.DEFAULT_BOOK_ALPHA_IMPORTANT else PreferenceKeys.DEFAULT_BOOK_ALPHA_ALL
        return prefs.getFloat(key, 1.0f) // 默认不透明
    }

// ==========================================
    // v0.9.0 新增：OOBE、EULA 与更新检查设置
    // ==========================================

    fun setOobeCompleted(completed: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.OOBE_COMPLETED, completed).apply()
    }

    fun isOobeCompleted(): Boolean {
        // 默认 false，强制新用户进入 OOBE
        return prefs.getBoolean(PreferenceKeys.OOBE_COMPLETED, false)
    }

    fun setEulaAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.EULA_ACCEPTED, accepted).apply()
    }

    fun isEulaAccepted(): Boolean {
        return prefs.getBoolean(PreferenceKeys.EULA_ACCEPTED, false)
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.AUTO_UPDATE_ENABLED, enabled).apply()
    }

    fun isAutoUpdateEnabled(): Boolean {
        // 默认开启检查更新
        return prefs.getBoolean(PreferenceKeys.AUTO_UPDATE_ENABLED, true)
    }
    fun setPopupReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.POPUP_REMINDER_ENABLED, enabled).apply()
    }
    // ==========================================
    // v0.9.0 新增：主页手势自定义
    // ==========================================
    fun getSwipeLeftAction(): String {
        return prefs.getString(PreferenceKeys.SWIPE_LEFT_ACTION, "delete") ?: "delete"
    }

    fun getSwipeRightAction(): String {
        return prefs.getString(PreferenceKeys.SWIPE_RIGHT_ACTION, "delete") ?: "delete"
    }

    fun setPromptedSwipeActions(prompted: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.SWIPE_GUIDE_PROMPTED, prompted).apply()
    }

    fun hasPromptedSwipeActions(): Boolean {
        return prefs.getBoolean(PreferenceKeys.SWIPE_GUIDE_PROMPTED, false)
    }
    // ==========================================
    // v0.9.0 新增：多备用更新源引擎设置
    // ==========================================
    fun getUpdateUrls(): List<String> {
        val urlsString = prefs.getString(PreferenceKeys.UPDATE_SOURCE_NODES, "")
        if (urlsString.isNullOrBlank()) {
            // 本地为空时回退到内置的高可用源，保证 OTA 引擎始终可用
            return BUILT_IN_UPDATE_URLS
        }
        // 按行分割，过滤掉空白行
        return urlsString.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setUpdateUrls(urlsString: String) {
        prefs.edit().putString(PreferenceKeys.UPDATE_SOURCE_NODES, urlsString).apply()
    }

    fun isSyncUpdateUrlsEnabled(): Boolean {
        // 默认不开启，仅在开发者选项中开启
        return prefs.getBoolean(PreferenceKeys.UPDATE_SOURCE_SYNC_ENABLED, true)
    }

    // ==========================================
    // v0.9.1 新增：应用语言（多语言支持）
    // ==========================================
    fun getAppLanguage(): String {
        val raw = prefs.getString(LocaleHelper.KEY_APP_LANGUAGE, LocaleHelper.LANGUAGE_SYSTEM)
            ?: LocaleHelper.LANGUAGE_SYSTEM
        // 规范化，保证 ListPreference 一定能匹配到条目
        return LocaleHelper.normalizeLanguageTag(raw)
    }

    fun setAppLanguage(languageTag: String) {
        prefs.edit().putString(LocaleHelper.KEY_APP_LANGUAGE, languageTag).apply()
    }

    // ==========================================
    // v0.9.1 新增：默认更新节点池初始化
    // ==========================================
    /**
     * 首次启动时把内置的高可用更新源写入设置，这样导出/开发者选项里
     * 都有内容可看、可编辑，而不是一片空白。
     * 只在用户从未设置过（本地为空）且开启了在线同步时执行一次。
     */
    fun ensureDefaultUpdateUrls() {
        if (prefs.getBoolean(PreferenceKeys.UPDATE_SOURCE_NODES_INITIALIZED, false)) return
        prefs.edit().putBoolean(PreferenceKeys.UPDATE_SOURCE_NODES_INITIALIZED, true).apply()

        if (!isSyncUpdateUrlsEnabled()) return
        if (!prefs.getString(PreferenceKeys.UPDATE_SOURCE_NODES, "").isNullOrBlank()) return
        prefs.edit().putString(PreferenceKeys.UPDATE_SOURCE_NODES, BUILT_IN_UPDATE_URLS.joinToString("\n")).apply()
    }

    /** 内置默认更新源节点池（对外公开，供开发者选项的"恢复默认"使用）。 */
    fun getBuiltInUpdateUrls(): List<String> = BUILT_IN_UPDATE_URLS

    // 键名常量统一放在 PreferenceKeys 中，这里只保留「取值」常量：
    companion object {
        private const val PREFS_NAME = PreferenceKeys.PREFS_FILE

        /** 供 PreferenceFragmentCompat 等系统组件使用的 SharedPreferences 文件名。 */
        const val PREFS_NAME_FOR_PREFERENCES = PREFS_NAME

        // ---- 主题取值 ----
        /** Material Design 3 Expressive：更大的圆角、靠容器色阶表达层次。 */
        const val THEME_M3E = "M3E"
        const val THEME_M1 = "MD1"
        const val THEME_M2 = "M2"
        const val THEME_M3 = "M3"

        // ---- 强调色取值 ----
        const val ACCENT_MONET = "MONET"
        const val ACCENT_PINK = "PINK"
        const val ACCENT_BLUE = "BLUE"
        const val ACCENT_CUSTOM_MTB = "CUSTOM_MTB" // MTB 导入的自定义动态色

        // ---- 弹窗模式取值 ----
        const val POPUP_MODE_AUTO = "auto"
        const val POPUP_MODE_ACTIVITY = "activity"
        const val POPUP_MODE_WINDOW = "window"

        // ---- 提醒时间取值 ----
        const val REMINDER_TIME_NONE = "none"
        const val REMINDER_TIME_1_DAY = "1_day"
        const val REMINDER_TIME_3_DAYS = "3_days"
        const val REMINDER_TIME_1_WEEK = "1_week"

        // ---- 遮罩模式取值 ----
        const val SCRIM_MODE_THEME = "theme"
        const val SCRIM_MODE_BLACK = "black"
        const val SCRIM_MODE_WHITE = "white"
        const val SCRIM_MODE_CUSTOM = "custom"

        // ---- 导航与布局取值 ----
        /**
         * 历史遗留取值：悬浮式底部导航栏曾经是独立的导航形态。
         * 现在它已改为底部导航栏的「自动隐藏」开关，读到这个值会迁移成
         * [NAV_MODE_BOTTOM] + 打开自动隐藏。保留常量只为识别旧数据。
         */
        const val NAV_MODE_LEGACY_FLOATING = "floating"

        /** 底部导航栏（默认）。 */
        const val NAV_MODE_BOTTOM = "bottom"

        /** 侧滑抽屉导航。 */
        const val NAV_MODE_DRAWER = "drawer"

        const val HOME_LAYOUT_STANDARD = "standard"
        const val HOME_LAYOUT_COMPACT = "compact"

        /** 目标版本代号：从这个版本起支持悬浮导航与侧滑栏自定义图像。 */
        const val TARGET_VERSION_FLOATING_NAV = 901

        /**
         * 内置的默认更新源节点池。
         * 首次启动时写入 [PreferenceKeys.UPDATE_SOURCE_NODES]，
         * 用户可在开发者选项中自由修改，或一键恢复为这里的默认值。
         */
        val BUILT_IN_UPDATE_URLS = listOf(
            "https://raw.githubusercontent.com/Nanflas202202/Nanflas202202.github.io/refs/heads/main/update/zakolatest.json",
            "https://nanflas202202.github.io/update/zakolatest.json",
            "https://nanflas202202-github-io.pages.dev/update/zakolatest.json"
        )

    }
}
