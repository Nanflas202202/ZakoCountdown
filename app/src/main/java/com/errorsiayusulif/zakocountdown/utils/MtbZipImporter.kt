// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/MtbZipImporter.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.errorsiayusulif.zakocountdown.data.ImportedPalette
import com.errorsiayusulif.zakocountdown.data.ThemeArchive
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * 解析 Material Theme Builder 导出的 **Android (.zip)** 主题包。
 *
 * MTB 有两个导出入口，格式完全不同：
 *   1. `material-theme.json` —— JSON，由 [MtbThemeEngine.importThemeJson] 处理
 *   2. Android (.zip)        —— 本文件处理
 *
 * ZIP 包的结构（实测自 MTB 导出）：
 * ```
 * README.md
 * values/colors.xml          浅色全部色值
 * values-night/colors.xml    深色全部色值
 * values/themes.xml          浅色「AppTheme」把色值绑到角色
 * values-night/themes.xml
 * values/theme_overlays.xml  对比度叠加层
 * values-night/theme_overlays.xml
 * ```
 *
 * 关键点：**色值本身就在 `colors.xml` 里**，键名形如 `md_theme_surfaceContainer`，
 * 也就是 `md_theme_` + 角色名首字母小写。所以不需要去解析 `themes.xml`
 * 那种「角色 → @color/引用」的间接映射，直接从颜色表取即可，简单且不易错。
 *
 * 对比度变体（MTB 会在角色名后加后缀）：
 *   `md_theme_primary_mediumContrast` / `md_theme_primary_highContrast`
 * 我们只取**基础档**（没有后缀的那些），与本工程既有的 JSON 导入行为保持一致。
 *
 * 导入后写入的偏好键与 JSON 路径**完全相同**（`mtb_light_<role>` /
 * `mtb_dark_<role>`），因此 [MtbThemeEngine.getResolvedColor] 无需任何改动。
 */
object MtbZipImporter {

    private const val TAG = "MtbZipImporter"

    /** colors.xml 里色值的前缀。 */
    private const val COLOR_PREFIX = "md_theme_"

    /** 对比度变体后缀，带这些后缀的条目一律跳过。 */
    private val CONTRAST_SUFFIXES = listOf("_mediumContrast", "_highContrast")

    private const val ZIP_LIGHT = "values/colors.xml"
    private const val ZIP_DARK = "values-night/colors.xml"

    /**
     * 读取 ZIP 并写入冷暖两套方案。
     *
     * @return 成功时返回解析出的两套配色（供存档）；不是有效 MTB 包 / 读取失败返回 null
     */
    fun import(context: Context, uri: Uri): ImportedPalette? {
        return try {
            val entries = readEntries(context, uri)
            // 诊断日志：失败时能一眼看出是「没读到 entry」还是「读到了但解析不出色值」，
            // 而不是只能看到调用方那句笼统的「不是有效的主题包」
            Log.d(TAG, "读取到 ${entries.size} 个目标 entry：${entries.keys}")

            if (entries.isEmpty()) {
                Log.w(TAG, "ZIP 里找不到 $ZIP_LIGHT 或 $ZIP_DARK，不是 Material Theme Builder 导出的包")
                return null
            }

            val lightColors = entries[ZIP_LIGHT]?.let { parseColors(it) }.orEmpty()
            val darkColors = entries[ZIP_DARK]?.let { parseColors(it) }.orEmpty()
            Log.d(TAG, "解析结果：light=${lightColors.size} dark=${darkColors.size}")

            if (lightColors.isEmpty() && darkColors.isEmpty()) {
                Log.w(TAG, "ZIP 里没有解析出任何可用色值")
                return null
            }

            // 统一走 ThemeArchive 的写入点，键名只在一处定义
            ThemeArchive.applyColors(context, lightColors, darkColors)

            Log.d(
                TAG,
                "MTB ZIP 导入成功，写入 ${lightColors.size + darkColors.size} 个色值" +
                    "（light=${lightColors.size} dark=${darkColors.size}）"
            )
            ImportedPalette(light = lightColors, dark = darkColors)
        } catch (t: Throwable) {
            // 用户可能选到一个损坏 zip / 非 zip 文件，这里不能把异常抛给 UI
            Log.e(TAG, "解析 MTB ZIP 失败", t)
            null
        }
    }

    /**
     * 把 zip 里需要的两个 colors.xml 读成文本。
     *
     * ⚠️ 关键：**每个 entry 都必须完整读掉**，不管我们要不要它。
     * ZipInputStream 有一条硬性契约 —— 只有把当前 entry 的数据读到流末尾，
     * 它才能正确定位到下一个 entry 的头。曾经这里写成「只读需要的两个、
     * 其余直接 closeEntry()」，结果流位置错乱，后面所有 entry 都读不到，
     * 表现为「总是提示不是有效的主题包」。
     *
     * 所以做法是：逐个 entry 读空（不需要的内容直接丢弃），只把需要的两个留下来。
     * 包本身很小（几 KB），这点开销可以忽略。
     */
    private fun readEntries(context: Context, uri: Uri): Map<String, String> {
        val result = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.getNextEntry()
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    val wanted = name == ZIP_LIGHT || name == ZIP_DARK

                    // 无论如何都要把当前 entry 读完（这就是推进流位置的唯一手段）
                    val content = readFullyToString(zip)
                    if (wanted) result[name] = content

                    zip.closeEntry()
                    entry = zip.getNextEntry()
                }
            }
        } ?: Log.e(TAG, "无法打开输入流")
        return result
    }

    /**
     * 把当前 zip entry 读完并解码成字符串。
     *
     * 不用 Kotlin 的 `InputStream.readBytes()`：本工程依赖 okio，
     * 它提供的 `readBytes(ByteArray)` 会遮蔽 Kotlin 的同名无参扩展，
     * 直接调用会解析到 okio 的签名而编译不过。这里显式循环，行为最确定。
     */
    private fun readFullyToString(input: java.io.InputStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    /**
     * 从 colors.xml 文本里抽取基础档色值。
     *
     * 实现选择说明：这里用**正则**而不是 [android.util.Xml] 的 Pull 解析。
     *
     * 起因是设备上一直报「不是有效的主题包」，而逐字节验证证明 zip 里的
     * colors.xml 能正常读出来（两个 entry 都拿到了、各 141 条色值），
     * 所以问题出在解析这一步。Pull 解析必须先
     * `setFeature(FEATURE_PROCESS_NAMESPACES, true)`，而不同 ROM 的解析器
     * 对这条特性支持不一致，一旦抛异常就被外层 catch 吞掉，
     * 表现成「包无效」这种与真实原因无关的提示。
     *
     * colors.xml 结构极其规整 —— 每行就是
     * `<color name="md_theme_xxx">#RRGGBB</color>`，
     * 用正则没有兼容性风险，也不依赖命名空间处理。
     */
    private fun parseColors(xml: String): Map<String, String> {
        val out = linkedMapOf<String, String>()

        // MTB 导出的实际写法：<color name="md_theme_primary">#415F91</color>
        val tagContentForm = Regex(
            """<color\s+name\s*=\s*"([^"]+)"\s*>\s*(#[0-9A-Fa-f]{6,8})\s*</color>"""
        )
        for (m in tagContentForm.findAll(xml)) {
            acceptRole(m.groupValues[1], m.groupValues[2], out)
        }

        // 兜底：形如 <color name="x" color="#RRGGBB"/> 的属性写法
        val attributeForm = Regex(
            """<color\s+[^>]*name\s*=\s*"([^"]+)"[^>]*?\scolor\s*=\s*"(#[0-9A-Fa-f]{6,8})"[^>]*/?>"""
        )
        for (m in attributeForm.findAll(xml)) {
            acceptRole(m.groupValues[1], m.groupValues[2], out)
        }

        if (out.isEmpty()) {
            Log.w(TAG, "正则未提取到任何色值，colors.xml 前 200 字符：${xml.take(200)}")
        } else {
            Log.d(TAG, "从 colors.xml 提取到 ${out.size} 个基础档色值")
        }
        return out
    }

    /** 过滤前缀 / 对比度变体 / 非法色值，合格的写进 [out]。 */
    private fun acceptRole(rawName: String, rawValue: String, out: MutableMap<String, String>) {
        if (!rawName.startsWith(COLOR_PREFIX)) return
        val role = rawName.removePrefix(COLOR_PREFIX)
        if (CONTRAST_SUFFIXES.any { role.endsWith(it) }) return
        if (!isUsableColor(rawValue)) return
        out[role] = rawValue.trim()
    }

    /** 只接受 #RRGGBB / #AARRGGBB 这类可直接用于 Color.parseColor 的值。 */
    private fun isUsableColor(value: String): Boolean {
        val v = value.trim()
        if (!v.startsWith("#")) return false
        return v.length == 7 || v.length == 9
    }
}
