// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/ThemeArchive.kt
package com.errorsiayusulif.zakocountdown.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine

/**
 * 一套配色方案（角色名 → #RRGGBB）。
 *
 * 角色名沿用 Material Theme Builder 的首字母小写写法（`primary`、`surfaceContainer`…），
 * 这样 ZIP / JSON 两种导入来源以及 [MtbThemeEngine] 的读取三方共用同一套命名。
 */
typealias ThemeColors = Map<String, String>

/**
 * 一份被保存下来的导入主题。
 *
 * @param id        唯一标识，用导入时刻的毫秒时间戳
 * @param name      展示名（默认取文件名）
 * @param importedAt 导入时间戳，用于排序与显示
 */
data class SavedTheme(
    val id: Long,
    val name: String,
    val importedAt: Long,
    val light: ThemeColors,
    val dark: ThemeColors
) {
    /** 是否含可用的浅色/深色数据，避免存档里有空壳。 */
    val isEmpty: Boolean get() = light.isEmpty() && dark.isEmpty()
}

/**
 * 一次导入解析出来的两套配色。
 *
 * 导入器（[com.errorsiayusulif.zakocountdown.utils.MtbZipImporter] /
 * [com.errorsiayusulif.zakocountdown.utils.MtbThemeEngine]）除了把色值写进偏好，
 * 还会把这个对象交回来，供 [ThemeArchive] 存档。
 */
data class ImportedPalette(
    val light: ThemeColors,
    val dark: ThemeColors
) {
    val isEmpty: Boolean get() = light.isEmpty() && dark.isEmpty()
}

/**
 * 导入主题的本地存档。
 *
 * 每次从 Material Theme Builder 导入（zip 或 json）都会把整套配色存一份进来，
 * 用户之后可以在列表里切回任意一套，不必重新找文件。
 *
 * 存储位置刻意用**独立的 prefs 文件**，不放 `zako_prefs`：
 *   · 主题存档体积明显大于普通设置（每套 47×2 个色值），
 *     混进 `zako_prefs` 会让设置备份/导出跟着膨胀；
 *   · 它属于「素材库」而不是「配置」，语义上就该分开。
 */
object ThemeArchive {

    private const val TAG = "ThemeArchive"

    private const val PREFS_FILE = "zako_theme_archive"
    private const val KEY_THEMES = "saved_themes"

    /** 最多保留多少套，防止无限增长。 */
    private const val MAX_THEMES = 30

    private val gson = Gson()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** 读取全部存档，按导入时间倒序（最新在前）。 */
    fun load(context: Context): List<SavedTheme> {
        val json = prefs(context).getString(KEY_THEMES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedTheme>>() {}.type
            val list: List<SavedTheme>? = gson.fromJson(json, type)
            list.orEmpty()
                .filterNot { it.isEmpty }
                .sortedByDescending { it.importedAt }
        } catch (t: Throwable) {
            // 存档损坏时不能让整个设置页挂掉，退回空列表即可
            Log.e(TAG, "读取主题存档失败，按空处理", t)
            emptyList()
        }
    }

    private fun persist(context: Context, themes: List<SavedTheme>) {
        try {
            prefs(context).edit()
                .putString(KEY_THEMES, gson.toJson(themes))
                .apply()
        } catch (t: Throwable) {
            Log.e(TAG, "写入主题存档失败", t)
        }
    }

    /**
     * 保存一套刚导入的主题。
     *
     * 若「浅色 + 深色」与已有存档完全一致，则不重复保存，直接返回已有条目 ——
     * 用户重复导入同一个文件时不该堆出一串一样的记录。
     */
    fun save(context: Context, name: String, light: ThemeColors, dark: ThemeColors): SavedTheme {
        val existing = load(context)
        existing.firstOrNull { it.light == light && it.dark == dark }?.let { return it }

        val now = System.currentTimeMillis()
        val entry = SavedTheme(
            id = now,
            name = name.ifBlank { "Theme $now" },
            importedAt = now,
            light = light,
            dark = dark
        )

        // 超过上限时丢掉最旧的
        val merged = (listOf(entry) + existing).take(MAX_THEMES)
        persist(context, merged)
        Log.d(TAG, "已保存导入主题「${entry.name}」，当前共 ${merged.size} 套")
        return entry
    }

    fun delete(context: Context, id: Long) {
        val remaining = load(context).filterNot { it.id == id }
        persist(context, remaining)
    }

    fun rename(context: Context, id: Long, newName: String) {
        if (newName.isBlank()) return
        val updated = load(context).map { if (it.id == id) it.copy(name = newName) else it }
        persist(context, updated)
    }

    // ========================================================================
    // 应用到当前主题
    // ========================================================================

    /**
     * 把某一套配色写进 `zako_prefs`，使其成为当前生效的 MTB 主题。
     *
     * ⚠️ 这里是**唯一**的写入点：导入器解析完成后也调它，
     * 避免「导入」和「切换存档」两条路径各写一套键名、日后漂移。
     */
    fun applyColors(context: Context, light: ThemeColors, dark: ThemeColors) {
        val editor = context
            .getSharedPreferences(PreferenceKeys.PREFS_FILE, Context.MODE_PRIVATE)
            .edit()

        light.forEach { (role, hex) -> editor.putString("$PREF_PREFIX_LIGHT$role", hex) }
        dark.forEach { (role, hex) -> editor.putString("$PREF_PREFIX_DARK$role", hex) }

        editor.putBoolean(PreferenceKeys.MTB_THEME_ENABLED, true)
        editor.putString(PreferenceKeys.ACCENT_COLOR, PreferenceManager.ACCENT_CUSTOM_MTB)
        editor.apply()

        Log.d(TAG, "已应用配色：light=${light.size} dark=${dark.size}")
    }

    /**
     * 切换到某个存档主题。
     *
     * 除了写入色值，还把主题名记进 [PreferenceKeys.MTB_THEME_NAME]，
     * 「已保存的主题」列表靠它标出哪一套正在使用。
     */
    fun apply(context: Context, theme: SavedTheme) {
        applyColors(context, theme.light, theme.dark)
        context.getSharedPreferences(PreferenceKeys.PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(PreferenceKeys.MTB_THEME_NAME, theme.name)
            .apply()
    }

    /** 记录当前生效的导入主题名。供导入路径调用。 */
    fun rememberName(context: Context, name: String) {
        context.getSharedPreferences(PreferenceKeys.PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(PreferenceKeys.MTB_THEME_NAME, name)
            .apply()
    }

    /**
     * 偏好的键前缀，与 [MtbThemeEngine] 读取时一致。
     * 改动时必须同步确认那边，否则会出现「存了却读不出来」。
     */
    const val PREF_PREFIX_LIGHT = "mtb_light_"
    const val PREF_PREFIX_DARK = "mtb_dark_"
}
