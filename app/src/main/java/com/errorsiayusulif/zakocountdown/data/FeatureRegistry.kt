// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/FeatureRegistry.kt
package com.errorsiayusulif.zakocountdown.data

/**
 * 功能清单（Feature Registry）。
 *
 * 解决的问题：EYF 备份包里只有一堆键值对，看不出「这个包到底带哪些功能的数据」。
 * 导入时只能靠 `internalCode` 逐条判断，人工排查很痛苦。
 *
 * 现在每个功能都在这里登记一次，登记内容包含：
 *   - [key]      稳定标识，写进备份包的 DocumentFeatures.json
 *   - [sinceCode] 从哪个 internalCode 开始存在（用于按目标版本裁剪）
 *   - [settingsKeys] 该功能占用的设置键，便于对照导出内容
 *   - [exportable] 是否属于「数据可跨设备迁移」的功能
 *
 * 新增功能时在这里加一条即可，导出清单会自动带上它。
 */
object FeatureRegistry {

    data class Feature(
        /** 稳定标识，不要随意改名 —— 它会出现在备份包里。 */
        val key: String,
        /** 中文名（写进导出清单，方便用户直接看）。 */
        val displayNameZh: String,
        /** 英文名。 */
        val displayNameEn: String,
        /** 从哪个 internalCode 开始可用。 */
        val sinceCode: Int,
        /** 该功能相关的设置键。 */
        val settingsKeys: List<String> = emptyList(),
        /** 是否属于可导出/可迁移的功能（false = 纯运行时能力）。 */
        val exportable: Boolean = true
    )

    // ==================== 登记表 ====================
    val ALL: List<Feature> = listOf(
        // ---------- v0.8.9 及更早 ----------
        Feature("core_countdown", "倒数日卡片与计时", "Countdown cards", 89, exportable = false),
        Feature("theme_basic", "基础主题（MD1/MD2）", "Basic themes", 89,
            settingsKeys = listOf(PreferenceKeys.THEME_MODE)),
        Feature("navigation_basic", "导航形态切换", "Navigation mode", 89,
            settingsKeys = listOf(PreferenceKeys.APP_LAYOUT_MODE)),
        Feature("agenda_books", "日程本分类", "Agenda books", 89,
            settingsKeys = listOf(PreferenceKeys.AGENDA_BOOK_ENABLED, PreferenceKeys.AGENDA_VIEW_IS_GRID)),

        // ---------- v0.8.11 ----------
        Feature("default_book_covers", "日程本默认封面与透明度", "Default book covers & opacity", 811,
            settingsKeys = listOf(
                PreferenceKeys.DEFAULT_BOOK_COVER_ALL,
                PreferenceKeys.DEFAULT_BOOK_COVER_IMPORTANT,
                PreferenceKeys.DEFAULT_BOOK_ALPHA_ALL,
                PreferenceKeys.DEFAULT_BOOK_ALPHA_IMPORTANT
            )),

        // ---------- v0.9.0 ----------
        Feature("popup_reminder", "开屏弹窗提醒", "Launch popup reminder", 900,
            settingsKeys = listOf(
                PreferenceKeys.POPUP_REMINDER_ENABLED,
                PreferenceKeys.POPUP_MODE,
                PreferenceKeys.POPUP_DURATION_SECONDS,
                PreferenceKeys.POPUP_SKIPPABLE,
                PreferenceKeys.POPUP_SKIP_DELAY_SECONDS,
                PreferenceKeys.POPUP_TARGET_APPS
            )),
        Feature("scheduled_reminder", "定时通知提醒", "Scheduled reminders", 900,
            settingsKeys = listOf(PreferenceKeys.REMINDER_LEAD_TIME)),
        Feature("persistent_notification", "常驻通知", "Persistent notification", 900,
            settingsKeys = listOf(PreferenceKeys.PERSISTENT_NOTIFICATION_ENABLED)),
        Feature("swipe_actions", "主页滑动快捷操作", "Home swipe actions", 900,
            settingsKeys = listOf(PreferenceKeys.SWIPE_LEFT_ACTION, PreferenceKeys.SWIPE_RIGHT_ACTION)),
        Feature("dynamic_icon", "桌面图标动态切换", "Dynamic launcher icon", 900,
            settingsKeys = listOf(PreferenceKeys.APP_ICON_ALIAS)),
        Feature("ota_multi_source", "多源回退 OTA 更新引擎", "Multi-source OTA engine", 900,
            settingsKeys = listOf(
                PreferenceKeys.AUTO_UPDATE_ENABLED,
                PreferenceKeys.UPDATE_SOURCE_SYNC_ENABLED,
                PreferenceKeys.UPDATE_SOURCE_NODES
            )),
        Feature("mtb_theme", "Material Theme Builder 动态色彩", "MTB dynamic color", 900,
            settingsKeys = listOf(PreferenceKeys.MTB_THEME_ENABLED, PreferenceKeys.ACCENT_COLOR)),
        Feature("scrim_custom", "背景遮罩颜色与浓度", "Background scrim", 900,
            settingsKeys = listOf(
                PreferenceKeys.SCRIM_COLOR_MODE,
                PreferenceKeys.SCRIM_ALPHA,
                PreferenceKeys.SCRIM_CUSTOM_COLOR
            )),
        Feature("global_alpha", "解锁卡片全局透明度", "Unlock global card opacity", 900,
            settingsKeys = listOf(PreferenceKeys.CARD_ALPHA_UNLOCKED)),
        Feature("log_persistence", "日志持久化与阅读器", "Persistent logs & reader", 900,
            settingsKeys = listOf(PreferenceKeys.LOG_PERSISTENCE_ENABLED)),
        Feature("oobe_eula", "开箱引导与 EULA", "OOBE & EULA", 900, exportable = false),

        // ---------- v0.9.1 ----------
        Feature("floating_nav_bar", "自动隐藏式底部导航栏", "Auto-hide bottom navigation", 901),
        Feature("drawer_header_image", "侧滑栏顶部图像自定义", "Custom drawer header image", 901,
            settingsKeys = listOf(
                PreferenceKeys.DRAWER_HEADER_IMAGE_URI,
                PreferenceKeys.DRAWER_HEADER_IMAGE_ENABLED
            )),
        Feature("eyf_upgrade_code", "EYF 升级代号与功能清单", "EYF upgrade code & feature manifest", 901,
            exportable = false),

        // 说明：MD3 Expressive 主题已在本版本暂时下线（通栏列表底色在真机上不稳定），
        // 样式资源仍保留，重新上线时把这条恢复回去并把 sinceCode 保持 901 即可。
        // Feature("md3_expressive", "Material Design 3 Expressive 主题", "MD3 Expressive theme", 901),

        // ---------- v0.9.2 ----------
        Feature("drawer_header_replaces_logo", "侧滑栏顶图替换 Logo 且左右抽屉共用",
            "Drawer header replaces logo & shared across drawers", 902,
            settingsKeys = listOf(PreferenceKeys.DRAWER_HEADER_IMAGE_URI)),
        Feature("share_card_sizes", "分享卡片多尺寸输出", "Share card size presets", 902,
            settingsKeys = listOf(PreferenceKeys.SHARE_CARD_SIZE), exportable = false),
        Feature("theme_archive", "导入主题的收藏与切换", "Saved imported themes", 902,
            exportable = false),
        Feature("mtb_zip_import", "Material Theme Builder 主题包(.zip)导入",
            "MTB theme package (.zip) import", 902,
            settingsKeys = listOf(PreferenceKeys.MTB_THEME_NAME)),
        Feature("auto_hide_nav", "底部导航栏滚动自动隐藏", "Scroll-aware navigation bar", 902,
            settingsKeys = listOf(PreferenceKeys.AUTO_HIDE_NAV_BAR)),

        /**
         * 防沉迷（Focus Guard）。
         *
         * 移植自 anti-addiction（MIT, © 2025 wd），做了三处刻意裁剪：
         * 去掉全部云端上报与服务端配置拉取（纯本地）、去掉 MMKV、
         * 把多档配额收敛为「每日免答题次数 + 每次解锁时长」两个参数。
         * 因为已无任何设置键落在 zako_prefs，所以按不可导出功能登记。
         */
        Feature("focus_guard", "防沉迷遮挡（推荐流屏蔽与解题解锁）",
            "Focus guard (feed blocking with unlock challenge)", 902,
            exportable = false)
    )

    /** 目标版本下可用的全部功能（包含运行时能力）。 */
    fun availableFor(targetInternalCode: Int): List<Feature> =
        ALL.filter { it.sinceCode <= targetInternalCode }

    /** 目标版本下可导出/可迁移的功能 —— 也就是写进 DocumentFeatures.json 的清单。 */
    fun exportableFor(targetInternalCode: Int): List<Feature> =
        availableFor(targetInternalCode).filter { it.exportable }

    /** 当前版本相对目标版本「新增」的功能（目标版本没有的）。 */
    fun addedAfter(targetInternalCode: Int): List<Feature> =
        ALL.filter { it.sinceCode > targetInternalCode }

    /** 按功能键取单个功能。 */
    fun byKey(key: String): Feature? = ALL.find { it.key == key }
}
