// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/AccessibilityStatusHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityStatusHelper {

    /**
     * 检查我们的无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/.services.AppOpenDetectorService"
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            val settingValue = TextUtils.SimpleStringSplitter(':')
            if (accessibilityEnabled == 1) {
                settingValue.setString(
                    Settings.Secure.getString(
                        context.applicationContext.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    )
                )
                while (settingValue.hasNext()) {
                    val accessibilityService = settingValue.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // In case of any error, assume it's not enabled
        }
        return false
    }
}