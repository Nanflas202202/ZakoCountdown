// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/PreferenceKeys.kt
package com.errorsiayusulif.zakocountdown.data

import android.content.Context

/**
 * 所有 SharedPreferences 键的单一数据源。
 *
 * 设计原则
 * --------
 *  1. **键名 = 它代表的意思**。不再有 `key_xxx` 这种没有信息量的前缀，
 *     命名统一为 `<域>_<含义>`，例如 `popup_reminder_enabled`、`theme_mode`。
 *  2. 常量集中在 [PreferenceKeys]，业务代码不要再写字符串字面量。
 *  3. [LEGACY_KEY_MAP] 负责旧键 → 新键的一次性迁移，保证老用户升级后设置不丢。
 *
 * 新增设置项时：在这里加常量 → 在 XML 里用同名 app:key → 在 [LEGACY_KEY_MAP] 里
 * 只有在「重命名已有键」时才需要加映射。
 */
object PreferenceKeys {

    /** SharedPreferences 文件名。 */
    const val PREFS_FILE = "zako_prefs"

    // ==================== 主题与外观 ====================
    const val THEME_MODE = "theme_mode"
    const val ACCENT_COLOR = "accent_color"
    const val MTB_THEME_ENABLED = "mtb_theme_enabled"

    /**
     * 当前生效的「导入主题」名称。
     * 「已保存的主题」列表靠它标出哪一套正在使用。
     */
    const val MTB_THEME_NAME = "mtb_theme_name"
    const val MD1_THEME_ENABLED = "md1_theme_enabled"
    const val LEGACY_THEME_IN_COMPACT = "legacy_theme_unlock_in_compact"

    /**
     * 已下线的主题取值 M3E（Material 3 Expressive）。
     *
     * 它的「通栏列表」底色在真机上没能稳定渲染，所以先从主题列表里撤下。
     * 这两个常量只服务于 [migrateLegacyKeys] 的取值迁移 ——
     * 业务代码里请用 [PreferenceManager.THEME_M3] 这类有效取值。
     * 重新上线 M3E 时，把这里的迁移和 arrays.xml 的选项一起加回来/去掉。
     */
    private const val THEME_M3E_DISABLED = "M3E"
    private const val THEME_M3_FALLBACK = "M3"

    // ==================== 布局与导航 ====================
    /**
     * 应用整体布局形态。二选一：
     *   - [PreferenceManager.NAV_MODE_BOTTOM]    底部导航栏（默认）
     *   - [PreferenceManager.NAV_MODE_DRAWER]    侧滑抽屉导航
     *
     * 「悬浮药丸 + 自动隐藏」已不是独立形态，而是底部导航栏的子开关，
     * 见下面的 [AUTO_HIDE_NAV_BAR]。旧值 `floating` 会被
     * [PreferenceManager.getNavMode] 自动迁移。
     */
    const val APP_LAYOUT_MODE = "app_layout_mode"

    /**
     * 底部导航栏是否自动隐藏（下滑隐藏 / 上滑回来）。
     * 它是底部导航栏的一个**子开关**，只在导航形态为底部导航栏且主题支持时才显示。
     */
    const val AUTO_HIDE_NAV_BAR = "auto_hide_nav_bar"

    /** 主页列表是否使用紧凑单页布局。 */
    const val HOME_LAYOUT_MODE = "home_layout_mode"

    /** 侧滑栏顶部是否显示自定义图像（关闭时回退为纯色品牌头部）。 */
    const val DRAWER_HEADER_IMAGE_ENABLED = "drawer_header_image_enabled"

    /** 侧滑栏顶部自定义图像的本地副本路径。 */
    const val DRAWER_HEADER_IMAGE_URI = "drawer_header_image_uri"

    /** 「选择侧滑栏顶图」这个动作型设置项（无持久化值，仅作 key）。 */
    const val DRAWER_HEADER_PICK = "drawer_header_pick"

    /** 「清除侧滑栏顶图」这个动作型设置项（无持久化值，仅作 key）。 */
    const val DRAWER_HEADER_CLEAR = "drawer_header_clear"

    /** 「侧滑栏顶图」整组设置的分类 key，用于在非侧滑导航下整组隐藏。 */
    const val CATEGORY_DRAWER_HEADER = "category_drawer_header"

    const val APP_ICON_ALIAS = "app_icon_alias"
    const val AGENDA_BOOK_ENABLED = "agenda_book_enabled"
    const val AGENDA_VIEW_IS_GRID = "agenda_view_is_grid"

    // ==================== 主页手势 ====================
    const val SWIPE_LEFT_ACTION = "swipe_left_action"
    const val SWIPE_RIGHT_ACTION = "swipe_right_action"
    const val SWIPE_GUIDE_PROMPTED = "swipe_guide_prompted"

    // ==================== 背景与遮罩 ====================
    const val HOME_WALLPAPER_URI = "home_wallpaper_uri"
    const val SCRIM_COLOR_MODE = "scrim_color_mode"
    const val SCRIM_ALPHA = "scrim_alpha"
    const val SCRIM_CUSTOM_COLOR = "scrim_custom_color"
    const val DEFAULT_BOOK_COVER_ALL = "default_book_cover_all"
    const val DEFAULT_BOOK_COVER_IMPORTANT = "default_book_cover_important"
    const val DEFAULT_BOOK_ALPHA_ALL = "default_book_alpha_all"
    const val DEFAULT_BOOK_ALPHA_IMPORTANT = "default_book_alpha_important"
    const val CARD_ALPHA_UNLOCKED = "card_alpha_unlocked"

    // ==================== 弹窗提醒 ====================
    const val POPUP_REMINDER_ENABLED = "popup_reminder_enabled"
    const val POPUP_MODE = "popup_mode"
    const val POPUP_DURATION_SECONDS = "popup_duration_seconds"
    const val POPUP_SKIPPABLE = "popup_skippable"
    const val POPUP_SKIP_DELAY_SECONDS = "popup_skip_delay_seconds"
    const val POPUP_TARGET_APPS = "popup_target_apps"
    const val ACCESSIBILITY_GUIDE_PROMPTED = "accessibility_guide_prompted"

    // ==================== 通知与提醒 ====================
    const val PERSISTENT_NOTIFICATION_ENABLED = "persistent_notification_enabled"
    const val REMINDER_LEAD_TIME = "reminder_lead_time"

    // ==================== 联网与更新 ====================
    const val AUTO_UPDATE_ENABLED = "auto_update_enabled"
    const val UPDATE_SOURCE_SYNC_ENABLED = "update_source_sync_enabled"
    const val UPDATE_SOURCE_NODES = "update_source_nodes"
    const val UPDATE_SOURCE_NODES_INITIALIZED = "update_source_nodes_initialized"

    // ==================== 语言 ====================
    const val APP_LANGUAGE = "app_language"

    // ==================== 开发者与调试 ====================
    const val DEV_MODE_ENTRY_ENABLED = "dev_mode_entry_enabled"
    const val ABOUT_EASTER_EGG_ENABLED = "about_easter_egg_enabled"
    const val LOG_PERSISTENCE_ENABLED = "log_persistence_enabled"
    const val MIUI_FIX_DISABLED = "miui_fix_disabled"
    const val SHOW_SYSTEM_APPS = "show_system_apps"

    /**
     * 日志级别开关。
     * 这两个键本身就是「当前日志级别」的表达：开着 INFO 就是 INFO 级，开着 OFF 就是关闭。
     * 它们成对出现、互斥，只写在 SharedPreferences 里供日志阅读器读取，
     * 所以这里给它们起了能自解释的名字。
     */
    const val LOG_LEVEL_IS_INFO = "log_level_is_info"
    const val LOG_LEVEL_IS_OFF = "log_level_is_off"

    /** 「功能与提醒」分类在偏好树中的 key（仅用于代码里定位该分类）。 */
    const val OOBE_FEATURES_CATEGORY = "oobe_features_category"

    // ==================== 开箱引导（永远不导出） ====================
    const val OOBE_COMPLETED = "oobe_completed"
    const val EULA_ACCEPTED = "eula_accepted"

    // ==================== 微件（按 appWidgetId 拼接后缀） ====================
    const val WIDGET_EVENT_ID_PREFIX = "widget_event_id_"
    const val WIDGET_BACKGROUND_PREFIX = "widget_background_"
    const val WIDGET_IMAGE_URI_PREFIX = "widget_image_uri_"
    const val WIDGET_COLOR_PREFIX = "widget_color_"
    const val WIDGET_ALPHA_PREFIX = "widget_alpha_"
    const val WIDGET_IMAGE_ALPHA_PREFIX = "widget_image_alpha_"
    const val WIDGET_SHOW_SCRIM_PREFIX = "widget_show_scrim_"
    const val WIDGET_SCRIM_ALPHA_PREFIX = "widget_scrim_alpha_"

    /**
     * 旧键 → 新键。仅用于把已有安装的设置平滑迁移到新命名。
     * 迁移是幂等的：新键已存在时不覆盖，旧键随后被删除。
     */
    val LEGACY_KEY_MAP: Map<String, String> = mapOf(
        // 主题
        "key_theme" to THEME_MODE,
        "key_accent_color" to ACCENT_COLOR,
        "is_mtb_theme_enabled" to MTB_THEME_ENABLED,
        "key_enable_md1_theme" to MD1_THEME_ENABLED,
        "key_unlock_legacy_theme_compact" to LEGACY_THEME_IN_COMPACT,
        // 布局与导航
        "key_nav_mode" to APP_LAYOUT_MODE,
        // v0.9.1 上一轮把 key_nav_mode 改成了 navigation_mode，这里再并入 app_layout_mode
        "navigation_mode" to APP_LAYOUT_MODE,
        "key_home_layout_mode" to HOME_LAYOUT_MODE,
        "key_app_icon" to APP_ICON_ALIAS,
        "key_enable_agenda_book" to AGENDA_BOOK_ENABLED,
        "key_agenda_view_mode" to AGENDA_VIEW_IS_GRID,
        // 手势
        "key_swipe_left_action" to SWIPE_LEFT_ACTION,
        "key_swipe_right_action" to SWIPE_RIGHT_ACTION,
        "key_has_prompted_swipe" to SWIPE_GUIDE_PROMPTED,
        // 背景与遮罩
        "key_homepage_wallpaper" to HOME_WALLPAPER_URI,
        "key_drawer_header_image" to DRAWER_HEADER_IMAGE_URI,
        "key_drawer_header_image_enabled" to DRAWER_HEADER_IMAGE_ENABLED,
        "key_scrim_color_mode" to SCRIM_COLOR_MODE,
        "key_scrim_alpha" to SCRIM_ALPHA,
        "key_scrim_custom_color" to SCRIM_CUSTOM_COLOR,
        "cover_book_all" to DEFAULT_BOOK_COVER_ALL,
        "cover_book_important" to DEFAULT_BOOK_COVER_IMPORTANT,
        "alpha_book_all" to DEFAULT_BOOK_ALPHA_ALL,
        "alpha_book_important" to DEFAULT_BOOK_ALPHA_IMPORTANT,
        "key_unlock_global_alpha" to CARD_ALPHA_UNLOCKED,
        // 弹窗
        "enable_popup_reminder" to POPUP_REMINDER_ENABLED,
        "key_popup_mode" to POPUP_MODE,
        "key_popup_duration" to POPUP_DURATION_SECONDS,
        "key_popup_skippable" to POPUP_SKIPPABLE,
        "key_popup_skip_delay" to POPUP_SKIP_DELAY_SECONDS,
        "important_apps_list" to POPUP_TARGET_APPS,
        "key_has_prompted_accessibility" to ACCESSIBILITY_GUIDE_PROMPTED,
        // 通知
        "enable_permanent_notification" to PERSISTENT_NOTIFICATION_ENABLED,
        "key_reminder_time" to REMINDER_LEAD_TIME,
        // 更新
        "key_auto_update" to AUTO_UPDATE_ENABLED,
        "key_sync_update_urls" to UPDATE_SOURCE_SYNC_ENABLED,
        "key_custom_update_urls" to UPDATE_SOURCE_NODES,
        "key_update_urls_initialized" to UPDATE_SOURCE_NODES_INITIALIZED,
        // 语言
        "key_app_language" to APP_LANGUAGE,
        // 开发者
        "key_enable_enter_dev_mode" to DEV_MODE_ENTRY_ENABLED,
        "key_enable_about_easter_egg" to ABOUT_EASTER_EGG_ENABLED,
        "key_log_persistence" to LOG_PERSISTENCE_ENABLED,
        "key_miui_fix_override" to MIUI_FIX_DISABLED,
        "key_show_system_apps" to SHOW_SYSTEM_APPS,
        // 日志级别
        "log_level_info" to LOG_LEVEL_IS_INFO,
        "log_level_off" to LOG_LEVEL_IS_OFF,
        "cat_features" to OOBE_FEATURES_CATEGORY,
        // 引导
        "key_oobe_completed" to OOBE_COMPLETED,
        "key_eula_accepted" to EULA_ACCEPTED,
        // 微件
        "widget_bg_" to WIDGET_BACKGROUND_PREFIX,
        "widget_img_" to WIDGET_IMAGE_URI_PREFIX,
        "widget_img_alpha_" to WIDGET_IMAGE_ALPHA_PREFIX,
        "widget_scrim_alpha_" to WIDGET_SCRIM_ALPHA_PREFIX
    )

    /** 把旧键名翻译成新键名；没有映射时原样返回。用于导入旧版 EYF 备份。 */
    fun migrateKeyName(legacyKey: String): String {
        LEGACY_KEY_MAP[legacyKey]?.let { return it }
        // 前缀型微件键：widget_bg_12 → widget_background_12
        for ((oldPrefix, newPrefix) in LEGACY_KEY_MAP) {
            if (oldPrefix.endsWith("_") && legacyKey.startsWith(oldPrefix) && oldPrefix != newPrefix) {
                return newPrefix + legacyKey.removePrefix(oldPrefix)
            }
        }
        return legacyKey
    }

    /**
     * 值为 Int 的设置项。
     * 导入 EYF 时 Gson 会把所有数字解析成 Double，必须按这张表精准还原成 Int，
     * 否则读取时会抛 ClassCastException。新增 Int 型设置时记得加进来。
     */
    val INT_SETTING_KEYS: Set<String> = setOf(
        SCRIM_ALPHA,
        POPUP_DURATION_SECONDS,
        POPUP_SKIP_DELAY_SECONDS
    )

    fun isIntSetting(key: String): Boolean = key in INT_SETTING_KEYS

    /**
     * 一次性把旧键迁移到新键。幂等，可在 Application.onCreate 里无脑调用。
     * @return 实际迁移的键数量
     */
    fun migrateLegacyKeys(context: Context): Int {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val all = prefs.all
        if (all.isEmpty()) return 0

        val editor = prefs.edit()
        var migrated = 0

        for ((oldKey, newKey) in LEGACY_KEY_MAP) {
            if (oldKey.endsWith("_")) {
                // 前缀型：展开所有匹配项
                all.keys.filter { it.startsWith(oldKey) }.forEach { matched ->
                    val target = newKey + matched.removePrefix(oldKey)
                    if (!all.containsKey(target)) {
                        copyValue(editor, target, all[matched])
                        migrated++
                    }
                    editor.remove(matched)
                }
            } else {
                if (!all.containsKey(oldKey)) continue
                if (!all.containsKey(newKey)) {
                    copyValue(editor, newKey, all[oldKey])
                    migrated++
                }
                editor.remove(oldKey)
            }
        }

        // ---- 取值级迁移（与键名无关） ----
        // M3E 主题已从选项列表下线：它的「通栏列表」底色在真机上没能稳定渲染。
        // 已选 M3E 的用户必须落回 M3，否则会停留在一个再也选不回去的取值上，
        // 而且主题列表里对应的那一项已经不存在了。
        if (all[THEME_MODE] == THEME_M3E_DISABLED) {
            editor.putString(THEME_MODE, THEME_M3_FALLBACK)
            migrated++
        }

        if (migrated > 0) editor.apply()
        return migrated
    }

    @Suppress("UNCHECKED_CAST")
    private fun copyValue(editor: android.content.SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> Unit
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Set<*> -> editor.putStringSet(key, value as Set<String>)
            else -> editor.putString(key, value.toString())
        }
    }
}
