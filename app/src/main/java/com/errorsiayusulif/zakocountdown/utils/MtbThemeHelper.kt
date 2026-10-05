// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/MtbThemeHelper.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.graphics.drawable.DrawableCompat
import com.errorsiayusulif.zakocountdown.data.ImportedPalette
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.data.SavedTheme
import com.errorsiayusulif.zakocountdown.data.ThemeArchive

object MtbThemeHelper {

    private const val TAG = "MtbThemeHelper"

    const val PREF_IS_MTB_ENABLED = MtbThemeEngine.PREF_IS_MTB_ENABLED

    /**
     * 统一的主题导入入口，自动识别 Material Theme Builder 的两种导出格式：
     *
     *   · **Android (.zip)** —— `values/colors.xml` + `values-night/colors.xml`
     *     （MTB 的「Android」导出，见 [MtbZipImporter]）
     *   · **material-theme.json** —— 单个 JSON（[MtbThemeEngine.importThemeJson]）
     *
     * 判断顺序是「先看扩展名，再嗅探内容」：部分文件管理器给出的 URI
     * 没有可靠扩展名，只看后缀会漏判 ZIP。
     *
     * 导入成功后会把整套配色**存档**一份（见 [ThemeArchive]），
     * 之后可以在主题列表里随时切回来，不必再找原文件。
     *
     * @return 成功返回存档条目；失败返回 null
     */
    suspend fun importTheme(context: Context, uri: Uri, prefManager: PreferenceManager): SavedTheme? {
        val palette: ImportedPalette? = if (detectZip(context, uri)) {
            MtbZipImporter.import(context, uri)
        } else {
            MtbThemeEngine.importThemeJson(context, uri)
        }

        if (palette == null) return null

        // 导入器已把色值写进偏好，这里补上「标记为自定义动态色」，
        // 保证 MtbThemeEngine.isMtbActive() 判定为启用
        prefManager.saveAccentColor(PreferenceManager.ACCENT_CUSTOM_MTB)

        return ThemeArchive.save(
            context = context,
            name = guessThemeName(uri),
            light = palette.light,
            dark = palette.dark
        ).also { saved ->
            // 记下名字，供「已保存的主题」列表标出当前使用中的那一套
            ThemeArchive.rememberName(context, saved.name)
        }
    }

    /**
     * 从 URI 猜一个可读的主题名。
     *
     * MTB 导出的文件名常见是 `material-theme.zip` / `material-theme (1).zip`，
     * 直接拿文件名的可读部分即可；实在取不到就用时间兜底（[ThemeArchive.save] 里处理）。
     */
    private fun guessThemeName(uri: Uri): String {
        val raw = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
        return raw
            .removeSuffix(".zip")
            .removeSuffix(".json")
            .trim()
            .take(60)
    }

    // ========================================================================
    // 主题存档：查询 / 应用 / 删除
    // ========================================================================

    /** 已保存的导入主题，最新在前。 */
    fun savedThemes(context: Context): List<SavedTheme> = ThemeArchive.load(context)

    /** 切换到某套已保存的主题。调用方通常随后要重建 Activity 以生效。 */
    fun applySavedTheme(context: Context, theme: SavedTheme, prefManager: PreferenceManager) {
        ThemeArchive.apply(context, theme)
        prefManager.saveAccentColor(PreferenceManager.ACCENT_CUSTOM_MTB)
    }

    fun deleteSavedTheme(context: Context, id: Long) = ThemeArchive.delete(context, id)

    fun renameSavedTheme(context: Context, id: Long, name: String) =
        ThemeArchive.rename(context, id, name)

    /**
     * 判断选中的是不是 ZIP。
     *
     * 扩展名优先（最省事）；扩展名不可辨认时读魔数 ——
     * ZIP 固定以 `PK\x03\x04` 开头，判据足够可靠。
     */
    private fun detectZip(context: Context, uri: Uri): Boolean {
        val name = uri.lastPathSegment?.lowercase().orEmpty()
        if (name.endsWith(".zip")) return true
        if (name.endsWith(".json")) return false

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(4)
                input.read(head) == 4 &&
                    head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                    head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
            } ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "嗅探文件类型失败，按 JSON 处理", t)
            false
        }
    }

    /** 旧名保留（调用点较多），行为已并入 [importTheme]。 */
    suspend fun importThemeJson(context: Context, uri: Uri, prefManager: PreferenceManager): SavedTheme? =
        importTheme(context, uri, prefManager)

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