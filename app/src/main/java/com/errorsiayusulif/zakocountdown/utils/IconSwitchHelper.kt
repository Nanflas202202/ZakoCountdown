// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/IconSwitchHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconSwitchHelper {

    // 这里的别名必须和 AndroidManifest.xml 中声明的一字不差！
    const val ALIAS_DEFAULT = "com.errorsiayusulif.zakocountdown.MainActivityAliasDefault"
    const val ALIAS_NEW = "com.errorsiayusulif.zakocountdown.MainActivityAliasNew"

    // 将所有你在清单文件里写的别名放进这个列表
    private val ALL_ALIASES = listOf(ALIAS_DEFAULT, ALIAS_NEW)

    fun switchIcon(context: Context, targetAliasName: String) {
        val pm = context.packageManager

        // 1. 启用目标别名
        pm.setComponentEnabledSetting(
            ComponentName(context, targetAliasName),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // 2. 禁用其他所有别名 (真实的 MainActivity 安全无恙，不受影响)
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