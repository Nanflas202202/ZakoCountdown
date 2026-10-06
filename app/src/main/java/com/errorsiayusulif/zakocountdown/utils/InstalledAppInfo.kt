// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/InstalledAppInfo.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * 已安装应用的信息查询工具。
 *
 * 用途：防沉迷规则里显示「真实应用名 + 图标」，而不是让用户面对一串包名。
 * 规则存储只存包名（稳定），显示名每次渲染时从系统查（可能随应用更新而变）。
 */
object InstalledAppInfo {

    private const val TAG = "InstalledAppInfo"

    /** 一个可启动的应用。 */
    data class LaunchableApp(
        val packageName: String,
        val label: String,
        val icon: Drawable?
    )

    /**
     * 列出所有带桌面入口的应用，按名称排序。
     *
     * 只取 `CATEGORY_LAUNCHER` 是因为防沉迷的对象是「用户会主动去点的应用」；
     * 没有桌面入口的服务/插件类应用列出来只会干扰选择。
     */
    fun launchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = try {
            pm.queryIntentActivities(intent, 0)
        } catch (t: Throwable) {
            Log.e(TAG, "查询可启动应用失败", t)
            return emptyList()
        }

        return resolved
            .asSequence()
            // 按包名去重：一个应用可能有多个桌面入口（如带别名的图标）
            .distinctBy { it.activityInfo?.packageName }
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                // 把自己排除掉，遮挡自己毫无意义
                if (pkg == context.packageName) return@mapNotNull null
                LaunchableApp(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull()
                )
            }
            .sortedBy { it.label }
            .toList()
    }

    /** 取某个包名的显示名；取不到就退回包名本身。 */
    fun labelOf(context: Context, packageName: String): String {
        val pm = context.packageManager
        return try {
            val info: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (t: Throwable) {
            // 应用已被卸载时 getApplicationInfo 会抛 NameNotFoundException，
            // 这时用包名顶上，至少规则还能显示出来（用户可以自行删掉）
            packageName
        }
    }

    /** 取某个包名的图标；取不到返回 null。 */
    fun iconOf(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (t: Throwable) {
            null
        }
    }

    /** 该包名当前是否仍安装着。 */
    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
