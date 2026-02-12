// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/AccessibilityStatusHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.errorsiayusulif.zakocountdown.services.AppOpenDetectorService

object AccessibilityStatusHelper {

    /**
     * 检查我们的无障碍服务是否已启用
     * 使用 AccessibilityManager 获取已启用的服务列表进行匹配，比解析 Settings 字符串更可靠
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        // 获取所有已启用的无障碍服务
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        val myComponentName = ComponentName(context, AppOpenDetectorService::class.java)

        for (service in enabledServices) {
            // 检查组件名是否匹配
            val serviceId = service.id // 格式通常为 "包名/类名"
            if (serviceId.contains(myComponentName.flattenToShortString()) ||
                serviceId.contains(myComponentName.className)) {
                return true
            }
        }
        return false
    }
}