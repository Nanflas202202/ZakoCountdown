// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/NavModeHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import com.errorsiayusulif.zakocountdown.data.PreferenceManager

/**
 * 导航形态的唯一判定入口。
 *
 * 之前「紧凑模式强制 MD3」「底部导航选中态」「侧滑抽屉锁定」这些判断散落在
 * MainActivity / ZakoThemeApplier / 各个设置页里，各写各的，很容易互相打架。
 * 现在统一走这里，新增导航形态只需改这一个文件。
 *
 * 两种形态：
 *   - NAV_MODE_BOTTOM    底部导航栏（默认）
 *   - NAV_MODE_DRAWER    侧滑抽屉
 *
 * 「自动隐藏导航栏」**不是**第三种形态，而是底部导航栏的一个子开关
 * （见 [PreferenceManager.isAutoHideNavBar]）。设置页因此可以做到
 * 「只有选了底部导航栏才显示这个开关」。
 * 历史上它曾是独立形态（旧值 `floating`），
 * [PreferenceManager.getNavMode] 会把旧值自动迁移过来。
 */
object NavModeHelper {

    /** 是否是「底部系」导航。为了兼容迁移前的旧值 `floating`，这里也认它。 */
    fun isBottomNav(mode: String): Boolean {
        return mode == PreferenceManager.NAV_MODE_BOTTOM ||
            mode == PreferenceManager.NAV_MODE_LEGACY_FLOATING
    }

    fun isDrawer(mode: String): Boolean = mode == PreferenceManager.NAV_MODE_DRAWER

    fun from(context: Context): String = PreferenceManager(context).getNavMode()

    fun isBottomNav(context: Context): Boolean = isBottomNav(from(context))

    fun isDrawer(context: Context): Boolean = isDrawer(from(context))

    /**
     * 紧凑模式下是否应该强制 MD3。
     * 底部导航栏依赖 Material3 的 NavigationBar 观感，所以默认强制；开发者在
     * 开发者选项里解锁旧版主题后则尊重用户选择。
     */
    fun shouldForceM3ForCompact(context: Context): Boolean {
        val prefs = PreferenceManager(context)
        if (prefs.getHomeLayoutMode() != PreferenceManager.HOME_LAYOUT_COMPACT) return false
        return !prefs.isLegacyThemeUnlockedInCompact()
    }

    /** 侧滑抽屉是否可用（底部形态下左侧抽屉会被锁定）。 */
    fun isLeftDrawerAvailable(mode: String): Boolean = isDrawer(mode)
    /**
     * 当前是否应该让底部导航栏自动隐藏（下滑隐藏 / 上滑回来）。
     *
     * 只取决于两点：导航形态是底部导航栏、用户打开了开关。
     * 导航栏本身始终贴底（不是悬浮药丸），所以**不再**限制主题 ——
     * MD1/MD2 下同样可用，只是配色和字体按各自主题走。
     */
    fun isAutoHideNav(context: Context): Boolean {
        if (!isBottomNav(from(context))) return false
        return PreferenceManager(context).isAutoHideNavBar()
    }

    /**
     * 把用户存的导航形态收敛到当前有效取值。
     * 旧值 `floating`（曾是独立形态）统一归到 `bottom`，
     * 自动隐藏开关由 [PreferenceManager.getNavMode] 的迁移逻辑打开。
     */
    fun resolveNavMode(context: Context): String {
        val mode = from(context)
        return if (mode == PreferenceManager.NAV_MODE_LEGACY_FLOATING) {
            PreferenceManager.NAV_MODE_BOTTOM
        } else {
            mode
        }
    }
}