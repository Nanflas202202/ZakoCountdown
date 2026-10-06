// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/DarkModeHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.data.PreferenceManager

/**
 * 深色模式的单一入口。
 *
 * ## 为什么需要它
 *
 * 本应用的浅/深配色靠 `res/values-night/zako_color_roles.xml` 提供，
 * 走的是系统的 uiMode 机制。也就是说**默认行为完全由系统决定** ——
 * 系统开深色就深色，用户在这个应用里没有选择权。
 *
 * 要支持手动切换，正确做法是让 AppCompat 去改本应用的 uiMode：
 * [AppCompatDelegate.setDefaultNightMode]。它会：
 *   · 立刻重建已启动的 Activity，令 `values-night` 重新解析；
 *   · 把本进程的 configuration.uiMode 改掉，于是
 *     [MtbThemeEngine.isDarkMode] 读到的值也跟着变 ——
 *     主题引擎、浮层、微件都会自动使用正确的配色，**不需要各处再判断一次**。
 *
 * ## 生效时机很关键
 *
 * 必须在 `Application.onCreate` 里、任何 Activity 创建**之前**调用。
 * 否则首个 Activity 会先按系统配色完成一次布局，再被重建，
 * 表现为启动时闪一下错误的颜色。
 */
object DarkModeHelper {

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    /** 读取用户选择。缺省为跟随系统（保持原有行为，不改变老用户观感）。 */
    fun getMode(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences("zako_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PreferenceKeys.DARK_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
    }

    /** 用户可选项 → 文案，供设置页摘要使用。 */
    fun labelResId(mode: String): Int = when (mode) {
        MODE_LIGHT -> R.string.dark_mode_light
        MODE_DARK -> R.string.dark_mode_dark
        else -> R.string.dark_mode_system
    }

    /**
     * 应用用户的选择。
     *
     * 幂等：重复用同一个值调用不会触发多余的 Activity 重建，
     * 所以可以放心在 Application.onCreate 每次启动时都调用。
     */
    fun apply(context: Context) {
        val target = when (getMode(context)) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != target) {
            AppCompatDelegate.setDefaultNightMode(target)
        }
    }

    /**
     * 用户在设置页改动了选项时调用。
     *
     * 与 [apply] 的区别只是语义：设置页改完值再调一次，让改动立即生效
     * （`setDefaultNightMode` 会自动重建当前 Activity，用户当场就能看到效果，
     * 不必手动重启应用）。
     */
    fun onPreferenceChanged(context: Context, newMode: String) {
        val target = when (newMode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(target)
    }
}
