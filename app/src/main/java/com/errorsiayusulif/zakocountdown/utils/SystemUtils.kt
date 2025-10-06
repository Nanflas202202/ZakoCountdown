// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/SystemUtils.kt
package com.errorsiayusulif.zakocountdown.utils

import android.os.Build
import android.text.TextUtils
import java.io.BufferedReader
import java.io.InputStreamReader

// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/SystemUtils.kt
object SystemUtils {
    private const val ROM_MIUI = "miui"
    private const val ROM_EMUI = "emui"
    private const val ROM_FLYME = "flyme"
    private const val ROM_OPPO = "oppo"
    private const val ROM_VIVO = "vivo"
    private const val ROM_OXYGEN = "oxygen" // OnePlus
    private const val UNKNOWN = "unknown"

    private var romName: String? = null

    fun getRomName(): String {
        if (romName == null) {
            romName = when {
                isMiui() -> ROM_MIUI
                isEmui() -> ROM_EMUI
                isFlyme() -> ROM_FLYME
                isOppo() -> ROM_OPPO
                isVivo() -> ROM_VIVO
                isOxygen() -> ROM_OXYGEN
                else -> UNKNOWN
            }
        }
        return romName!!
    }

    fun isChineseRom(): Boolean {
        return getRomName() != UNKNOWN
    }

    // --- 【关键修复】移除 private，让这些方法变为公开 ---
    fun isMiui(): Boolean = !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name"))
    fun isEmui(): Boolean = !TextUtils.isEmpty(getSystemProperty("ro.build.version.emui"))
    fun isFlyme(): Boolean = getSystemProperty("ro.build.display.id")?.lowercase()?.contains("flyme") == true
    fun isOppo(): Boolean = !TextUtils.isEmpty(getSystemProperty("ro.build.version.opporom"))
    fun isVivo(): Boolean = !TextUtils.isEmpty(getSystemProperty("ro.vivo.os.version"))
    fun isOxygen(): Boolean = !TextUtils.isEmpty(getSystemProperty("ro.oxygen.version"))
}

    private fun getSystemProperty(propName: String): String? {
        return try {
            val p = Runtime.getRuntime().exec("getprop $propName")
            val input = BufferedReader(InputStreamReader(p.inputStream), 1024)
            val line = input.readLine()
            input.close()
            line
        } catch (ex: Exception) {
            null
        }
    }
