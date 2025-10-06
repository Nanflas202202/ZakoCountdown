// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/PermissionUtils.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.os.Build

object PermissionUtils {
    fun getAutostartIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") -> {
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            }
            manufacturer.contains("oppo") -> {
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
            }
            manufacturer.contains("vivo") -> {
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
            }
            manufacturer.contains("huawei") -> {
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            }
            else -> null
        }
    }
}