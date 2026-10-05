// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/ZakoThemeApplier.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceManager

/**
 * 单一主题解析入口。
 *
 * 以前 MainActivity、PopupViewService 各自散落一份「MD1/MD2/MD3 + 强调色」→ style 的映射，
 * 结果弹窗、微件配置页等入口拿到的主题和主界面不一致（颜色/样式无法统一）。
 * 现在所有需要主题的地方都走这里，新增主题只需改这一处。
 */
object ZakoThemeApplier {

    /**
     * 真正生效的主题取值（已套用「紧凑模式强制 MD3」等兜底规则）。
     * 想按主题分流功能开关时用这个，不要直接读 `getTheme()`。
     */
    fun resolveEffectiveThemeKey(context: Context): String {
        val prefs = PreferenceManager(context)
        return if (NavModeHelper.shouldForceM3ForCompact(context)) {
            PreferenceManager.THEME_M3
        } else {
            prefs.getTheme()
        }
    }

    /** 返回用户当前选择对应的主题资源 ID。 */
    fun resolveThemeResId(context: Context): Int {
        val prefs = PreferenceManager(context)
        val colorKey = prefs.getAccentColor()
        val finalThemeKey = resolveEffectiveThemeKey(context)

        return when (finalThemeKey) {
            PreferenceManager.THEME_M1 -> when (colorKey) {
                PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_MD1_Pink
                PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_MD1_Blue
                else -> R.style.Theme_ZakoCountdown_MD1
            }
            PreferenceManager.THEME_M2 -> when (colorKey) {
                PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_MD2_Pink
                PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_MD2_Blue
                else -> R.style.Theme_ZakoCountdown_MD2
            }
            PreferenceManager.THEME_M3E -> when (colorKey) {
                PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_M3E_Pink
                PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_M3E_Blue
                else -> R.style.Theme_ZakoCountdown_M3E
            }
            else -> when (colorKey) {
                PreferenceManager.ACCENT_PINK -> R.style.Theme_ZakoCountdown_M3_Pink
                PreferenceManager.ACCENT_BLUE -> R.style.Theme_ZakoCountdown_M3_Blue
                else -> R.style.Theme_ZakoCountdown_M3
            }
        }
    }

    /**
     * 把解析出的主题应用到 Activity，并在需要时叠加系统 Monet 动态色。
     * 必须在 `super.onCreate()` 之前调用 `setTheme` 才会生效。
     */
    fun applyToActivity(activity: android.app.Activity) {
        activity.setTheme(resolveThemeResId(activity))
        applyMonetIfAvailable(activity)
    }

    /**
     * 把同一个主题套到非 Activity 的 Context 上（弹窗用的 ContextThemeWrapper、
     * Service 里手工 inflate 的布局等），保证这些入口的配色与主界面一致。
     */
    fun wrapContext(context: Context): Context {
        return androidx.appcompat.view.ContextThemeWrapper(context, resolveThemeResId(context))
    }

    /** 仅在「MD3 + 跟随壁纸(Monet) + 系统支持」时叠加动态色。 */
    fun applyMonetIfAvailable(activity: android.app.Activity) {
        val prefs = PreferenceManager(activity)
        if (prefs.getTheme() == PreferenceManager.THEME_M3 &&
            prefs.getAccentColor() == PreferenceManager.ACCENT_MONET &&
            com.google.android.material.color.DynamicColors.isDynamicColorAvailable()
        ) {
            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(activity)
        }
    }
}
