// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/BackupManager.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.net.Uri
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.data.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // 当前应用支持的最高 EYF 格式版本
    private const val SUPPORTED_EYF_VERSION = 2.0f

    // =========================================================================
    // 导出 (EXPORT) - 支持目标版本降级
    // =========================================================================

    /**
     * @param targetVersionCode 用户选择的要兼容的旧版本号 (例如 9 代表 v0.8.9)
     */
    suspend fun exportToStream(
        context: Context,
        outputStream: OutputStream,
        repository: EventRepository,
        rootNodes: List<SelectableNode>,
        targetVersionCode: Int
    ) = withContext(Dispatchers.IO) {

        // 1. 判断是否需要降级格式 (假设 versionCode < 11 时只能使用 EYF v1.0)
        val useLegacyFormat = targetVersionCode < 11

        // 创建临时目录
        val exportDir = File(context.cacheDir, "eyf_export_${System.currentTimeMillis()}")
        if (exportDir.exists()) exportDir.deleteRecursively()

        val mediaDir = File(exportDir, "media")
        mediaDir.mkdirs()

        // 收集数据
        val settingsToExport = mutableMapOf<String, Any?>()
        val booksToExport = mutableListOf<ExportAgendaBook>()
        val eventsToExport = mutableListOf<ExportEvent>()

        val allPrefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).all

        // 遍历节点，提取选中的数据
        rootNodes.filter { it.isChecked }.forEach { node ->
            val includeColor = node.children.find { it.subOptionType == SubOptionType.COLOR }?.isChecked ?: true
            val includeCover = node.children.find { it.subOptionType == SubOptionType.COVER }?.isChecked ?: true
            val includeAlpha = node.children.find { it.subOptionType == SubOptionType.ALPHA }?.isChecked ?: true

            when (node.type) {
                NodeType.SETTING -> {
                    if (!node.id.startsWith("ctrl_")) {
                        val key = node.id.removePrefix("set_")
                        if (allPrefs.containsKey(key)) {
                            var value = allPrefs[key]

                            // 拦截包含 URI 路径的设置，将其打包为实体图片
                            if (key == "key_homepage_wallpaper" || key == "cover_book_all" || key == "cover_book_important") {
                                val uriStr = value as? String
                                if (!uriStr.isNullOrBlank()) {
                                    val fileName = "setting_${key}.png"
                                    copyUriToFile(context, Uri.parse(uriStr), File(mediaDir, fileName))
                                    value = fileName // 保存为相对文件名
                                }
                            }

                            settingsToExport[key] = value
                        }
                    }
                }
                NodeType.BOOK -> {
                    node.rawBook?.let { raw ->
                        val book = repository.getBookById(raw.originalId)
                        if (book != null) {
                            var coverName: String? = null
                            if (includeCover && book.coverImageUri != null) {
                                coverName = "book_${book.id}_cover.png"
                                copyUriToFile(context, Uri.parse(book.coverImageUri), File(mediaDir, coverName))
                            }
                            booksToExport.add(ExportAgendaBook(
                                book.id, book.name,
                                if (includeColor) book.colorHex else null,
                                coverName,
                                if (includeAlpha) book.cardAlpha else null,
                                book.sortOrder
                            ))
                        }
                    }
                }
                NodeType.EVENT -> {
                    node.rawEvent?.let { ev ->
                        var bgName: String? = null
                        if (includeCover && ev.backgroundFileName != null) {
                            bgName = "event_${System.nanoTime()}.png"
                            copyUriToFile(context, Uri.parse(ev.backgroundFileName), File(mediaDir, bgName))
                        }
                        eventsToExport.add(ev.copy(
                            backgroundFileName = bgName,
                            colorHex = if(includeColor) ev.colorHex else null,
                            cardAlpha = if(includeAlpha) ev.cardAlpha else null
                        ))
                    }
                }
                else -> {}
            }
        }

        // --- 核心区别：根据目标版本写出不同结构的文件 ---
        if (useLegacyFormat) {
            // == 降级至 EYF v1.0 ==
            // V1 只有一个 manifest.json 和一个 data.json，不具备拆分的 configs/ 和 data/ 目录

            // 写入 Manifest (V1 结构)
            File(exportDir, "manifest.json").writeText(gson.toJson(
                EyfManifest(
                    eyfVersion = "1.0", // 强制标为 1.0
                    appVersionCode = targetVersionCode // 伪装成目标版本导出的
                )
            ))

            // 写入 Data (V1 结构：全部塞进一个文件)
            File(exportDir, "data.json").writeText(gson.toJson(
                EyfData(settingsToExport, booksToExport, eventsToExport)
            ))

        } else {
            // == 正常输出 EYF v2.0 ==
            val configsDir = File(exportDir, "configs").apply { mkdirs() }
            val dataDir = File(exportDir, "data").apply { mkdirs() }
            val manifestList = mutableListOf<Map<String, String>>()

            // DocumentInfo.json
            val docInfo = mapOf(
                "eyf_version" to "2.0",
                "app_info" to mapOf("app_id" to BuildConfig.APPLICATION_ID, "app_version_code" to BuildConfig.VERSION_CODE, "app_name" to "ZakoCountdown"),
                "export_meta" to mapOf("export_time" to System.currentTimeMillis())
            )
            File(exportDir, "DocumentInfo.json").writeText(gson.toJson(docInfo))

            if (settingsToExport.isNotEmpty()) {
                File(configsDir, "settings.json").writeText(gson.toJson(settingsToExport))
                manifestList.add(mapOf("file_path" to "configs/settings.json", "type" to "CONFIG"))
            }
            if (booksToExport.isNotEmpty()) {
                File(dataDir, "AgendaBook.json").writeText(gson.toJson(booksToExport))
                manifestList.add(mapOf("file_path" to "data/AgendaBook.json", "type" to "DATABASE_TABLE"))
            }
            if (eventsToExport.isNotEmpty()) {
                File(dataDir, "Events.json").writeText(gson.toJson(eventsToExport))
                manifestList.add(mapOf("file_path" to "data/Events.json", "type" to "DATABASE_TABLE"))
            }

            File(exportDir, "DocumentManifest.json").writeText(gson.toJson(mapOf("manifest" to manifestList)))

            // 对于 v2.0，媒体文件应该移动到 assets/Images 下，这里简单起见，仍保留在 media/ 中并依赖 DocumentManifest
            // 在实际应用中，如果要严格遵守前面的 v2 规范，这里应做目录调整
        }

        // 打包并写入到传入的 OutputStream (适配 SAF)
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            exportDir.walkTopDown().filter { it.isFile }.forEach { file ->
                zos.putNextEntry(ZipEntry(exportDir.toPath().relativize(file.toPath()).toString().replace("\\", "/")))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        exportDir.deleteRecursively()
    }

    // =========================================================================
    // 内存数据包装类
    // =========================================================================
    data class ParsedEyfPackage(
        val version: Float,
        val appVersionCode: Int,
        val appId: String,
        val data: EyfData,
        val tempDir: File
    )

    // =========================================================================
    // 解析 (PARSE) - 兼容 v1.0 与 v2.0
    // =========================================================================
    suspend fun parseEyf(context: Context, uri: Uri): ParsedEyfPackage = withContext(Dispatchers.IO) {
        val importDir = File(context.cacheDir, "eyf_temp_${System.currentTimeMillis()}")
        importDir.mkdirs()
        unzip(context, uri, importDir)

        val docInfoFile = File(importDir, "DocumentInfo.json")
        val manifestFileV1 = File(importDir, "manifest.json")

        if (docInfoFile.exists()) {
            // === V2 解析 ===
            val docInfoMap = gson.fromJson(docInfoFile.readText(), Map::class.java)
            val versionStr = docInfoMap["eyf_version"] as? String ?: "2.0"
            val version = versionStr.toFloatOrNull() ?: 2.0f

            if (version > SUPPORTED_EYF_VERSION) {
                importDir.deleteRecursively()
                throw Exception("版本不兼容: 备份版本 v$version > 核心支持 v$SUPPORTED_EYF_VERSION")
            }

            val appInfo = docInfoMap["app_info"] as? Map<*, *>
            val appId = appInfo?.get("app_id") as? String ?: ""
            val appVersionCode = (appInfo?.get("app_version_code") as? Double)?.toInt() ?: 1

            val settingsFile = File(importDir, "configs/settings.json")
            val booksFile = File(importDir, "data/AgendaBook.json")
            val eventsFile = File(importDir, "data/Events.json")

            val settings: Map<String, Any?>? = if (settingsFile.exists()) gson.fromJson(settingsFile.readText(), object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type) else null
            val books: List<ExportAgendaBook>? = if (booksFile.exists()) gson.fromJson(booksFile.readText(), object : com.google.gson.reflect.TypeToken<List<ExportAgendaBook>>() {}.type) else null
            val events: List<ExportEvent>? = if (eventsFile.exists()) gson.fromJson(eventsFile.readText(), object : com.google.gson.reflect.TypeToken<List<ExportEvent>>() {}.type) else null

            return@withContext ParsedEyfPackage(version, appVersionCode, appId, EyfData(settings, books, events), importDir)

        } else if (manifestFileV1.exists()) {
            // === V1 解析 (旧版兼容) ===
            val manifest = gson.fromJson(manifestFileV1.readText(), EyfManifest::class.java)
            val version = manifest.eyfVersion.toFloatOrNull() ?: 1.0f

            val dataFile = File(importDir, "data.json")
            if (!dataFile.exists()) {
                importDir.deleteRecursively()
                throw Exception("损坏的 v1 文件：缺失 data.json")
            }
            val data = gson.fromJson(dataFile.readText(), EyfData::class.java)

            return@withContext ParsedEyfPackage(version, manifest.appVersionCode, manifest.appId, data, importDir)

        } else {
            importDir.deleteRecursively()
            throw Exception("无法识别的打包格式，缺少元数据清单文件")
        }
    }

    // =========================================================================
    // 导入执行 (IMPORT)
    // =========================================================================
    suspend fun executeImport(
        context: Context,
        repository: EventRepository,
        rootNodes: List<SelectableNode>,
        parsedPackage: ParsedEyfPackage
    ) = withContext(Dispatchers.IO) {
        val importDir = parsedPackage.tempDir
        if (!importDir.exists()) throw Exception("缓存文件已丢失")

        val isV2 = parsedPackage.version >= 2.0f
        val mediaSearchDir = File(importDir, if (isV2) "assets/Images" else "media")
        // 为了提高兼容性，如果在 assets/Images 里找不到，退回 media/ 寻找
        val fallbackMediaDir = File(importDir, "media")

        val prefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).edit()
        val bookIdMapping = mutableMapOf<Long, Long>()

        // 1. 恢复设置
        for (node in rootNodes.filter { it.isChecked && it.type == NodeType.SETTING }) {
            if (!node.id.startsWith("ctrl_")) {
                val key = node.id.removePrefix("set_")
                var v = node.rawSettingValue

                // 拦截媒体路径配置，从备份包的媒体库恢复文件，并生成本地新 URI
                if (key == "key_homepage_wallpaper" || key == "cover_book_all" || key == "cover_book_important") {
                    val fileName = v as? String
                    if (!fileName.isNullOrBlank()) {
                        var src = File(mediaSearchDir, fileName)
                        if (!src.exists()) src = File(fallbackMediaDir, fileName)

                        if (src.exists()) {
                            val dest = File(context.filesDir, "setting_${key}_${System.nanoTime()}.png")
                            src.copyTo(dest, true)
                            v = Uri.fromFile(dest).toString()
                        } else {
                            v = null // 找不到文件，清除设置
                        }
                    }
                }

                // 恢复到 SharedPreferences (注意：GSON 在解析 JSON 时会将所有数字读为 Double)
                when (v) {
                    is Boolean -> prefs.putBoolean(key, v)
                    is String -> prefs.putString(key, v)
                    is Double -> {
                        // 我们必须精准还原 Int 和 Float，否则应用读取时会抛出类型转换异常 (ClassCastException)
                        if (key == "key_scrim_alpha" || key == "key_popup_duration" || key == "key_popup_skip_delay") {
                            prefs.putInt(key, v.toInt())
                        } else {
                            prefs.putFloat(key, v.toFloat())
                        }
                    }
                    null -> prefs.remove(key)
                }
            }
        }
        prefs.apply()

        // 2. 恢复日程本
        for (node in rootNodes.filter { it.isChecked && it.type == NodeType.BOOK }) {
            val eb = node.rawBook!!
            val includeColor = node.children.find { it.subOptionType == SubOptionType.COLOR }?.isChecked ?: true
            val includeCover = node.children.find { it.subOptionType == SubOptionType.COVER }?.isChecked ?: true
            val includeAlpha = node.children.find { it.subOptionType == SubOptionType.ALPHA }?.isChecked ?: true

            var newCoverUri: String? = null
            if (includeCover && eb.coverImageFileName != null) {
                var src = File(mediaSearchDir, eb.coverImageFileName.substringAfterLast('/'))
                if (!src.exists()) src = File(fallbackMediaDir, eb.coverImageFileName.substringAfterLast('/'))

                if (src.exists()) {
                    val dest = File(context.filesDir, "book_cover_${System.nanoTime()}.png")
                    src.copyTo(dest, true)
                    newCoverUri = Uri.fromFile(dest).toString()
                }
            }
            val newId = repository.insertBookAndGetId(AgendaBook(
                0, eb.name, if(includeColor) eb.colorHex ?: "#000" else "#000",
                System.currentTimeMillis(), newCoverUri, if(includeAlpha) eb.cardAlpha ?: 1f else 1f, eb.sortOrder
            ))
            bookIdMapping[eb.originalId] = newId
        }

        // 3. 恢复日程
        for (node in rootNodes.filter { it.isChecked && it.type == NodeType.EVENT }) {
            val ev = node.rawEvent!!
            val includeColor = node.children.find { it.subOptionType == SubOptionType.COLOR }?.isChecked ?: true
            val includeCover = node.children.find { it.subOptionType == SubOptionType.COVER }?.isChecked ?: true
            val includeAlpha = node.children.find { it.subOptionType == SubOptionType.ALPHA }?.isChecked ?: true

            var newBgUri: String? = null
            if (includeCover && ev.backgroundFileName != null) {
                var src = File(mediaSearchDir, ev.backgroundFileName.substringAfterLast('/'))
                if (!src.exists()) src = File(fallbackMediaDir, ev.backgroundFileName.substringAfterLast('/'))

                if (src.exists()) {
                    val dest = File(context.filesDir, "bg_${System.nanoTime()}.png")
                    src.copyTo(dest, true)
                    newBgUri = Uri.fromFile(dest).toString()
                }
            }
            val mappedBookId = if (ev.originalBookId != null) bookIdMapping[ev.originalBookId] else null

            repository.insert(CountdownEvent(
                0, ev.title, Date(ev.targetDate), ev.isImportant, Date(),
                if(includeColor) ev.colorHex else null, newBgUri, ev.isPinned, ev.displayMode,
                if(includeAlpha) ev.cardAlpha else null, mappedBookId
            ))
        }

        importDir.deleteRecursively()
    }

    private fun copyUriToFile(context: Context, uri: Uri, destFile: File) {
        try { context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(destFile).use { input.copyTo(it) } } } catch (e: Exception) {}
    }

    private fun unzip(context: Context, zipUri: Uri, targetDir: File) {
        context.contentResolver.openInputStream(zipUri)?.let { ZipInputStream(BufferedInputStream(it)) }?.use { zis ->
            generateSequence { zis.nextEntry }.forEach { entry ->
                val file = File(targetDir, entry.name)
                // 核心安全：防止 Zip Slip
                val canonicalDestPath = file.canonicalPath
                val canonicalDirPath = targetDir.canonicalPath
                if (!canonicalDestPath.startsWith(canonicalDirPath + File.separator)) {
                    throw SecurityException("发现恶意文件路径: ${entry.name}")
                }

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { zis.copyTo(it) }
                }
            }
        }
    }
}