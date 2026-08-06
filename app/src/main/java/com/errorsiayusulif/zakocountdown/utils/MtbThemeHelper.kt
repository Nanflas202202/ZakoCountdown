// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/MtbThemeHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.net.Uri
import androidx.core.graphics.drawable.DrawableCompat
import com.errorsiayusulif.zakocountdown.data.PreferenceManager

object MtbThemeHelper {

    const val PREF_IS_MTB_ENABLED = MtbThemeEngine.PREF_IS_MTB_ENABLED

    suspend fun importThemeJson(context: Context, uri: Uri, prefManager: PreferenceManager): Boolean {
        val success = MtbThemeEngine.importThemeJson(context, uri)
        if (success) {
            prefManager.saveAccentColor("CUSTOM_MTB")
        }
        return success
    }

    fun isDarkMode(context: Context): Boolean = MtbThemeEngine.isDarkMode(context)

    // --- 【核心修复】转接给 MtbThemeEngine.getResolvedColor ---
    fun getColor(context: Context, colorName: String, fallbackColorStr: String): Int {
        // 由于旧方法缺少 fallbackAttrResId，我们传入 0 绕过它，直接依赖 fallbackColorStr
        return MtbThemeEngine.getResolvedColor(context, colorName, 0, 0, fallbackColorStr)
    }

    fun setIconTint(drawable: android.graphics.drawable.Drawable?, color: Int) {
        drawable?.let {
            val wrapped = DrawableCompat.wrap(it)
            DrawableCompat.setTint(wrapped, color)
        }
    }
}