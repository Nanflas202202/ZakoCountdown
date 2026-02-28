// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/IconSwitchHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconSwitchHelper {

    // 定义与 AndroidManifest.xml 中一致的别名类名
    const val ALIAS_DEFAULT = "com.errorsiayusulif.zakocountdown.MainActivityAliasDefault"
    const val ALIAS_DARK = "com.errorsiayusulif.zakocountdown.MainActivityAliasDark"
    const val ALIAS_SPECIAL = "com.errorsiayusulif.zakocountdown.MainActivityAliasSpecial"

    private val ALL_ALIASES = listOf(ALIAS_DEFAULT, ALIAS_DARK, ALIAS_SPECIAL)

    /**
     * 切换桌面图标
     * @param context 上下文
     * @param targetAliasName 要启用的别名全限定名
     */
    fun switchIcon(context: Context, targetAliasName: String) {
        val pm = context.packageManager

        // 1. 启用目标别名
        pm.setComponentEnabledSetting(
            ComponentName(context, targetAliasName),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP // 尽量不要立即杀死应用 (但部分Launcher依然会重载桌面导致闪屏)
        )

        // 2. 禁用其他所有别名
        for (alias in ALL_ALIASES) {
            if (alias != targetAliasName) {
                pm.setComponentEnabledSetting(
                    ComponentName(context, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}