// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/BackupManager.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.errorsiayusulif.zakocountdown.data.PreferenceKeys
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.R
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

    private const val TAG = "BackupManager"

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
                            if (key == PreferenceKeys.HOME_WALLPAPER_URI || key == PreferenceKeys.DRAWER_HEADER_IMAGE_URI || key == PreferenceKeys.DEFAULT_BOOK_COVER_ALL || key == PreferenceKeys.DEFAULT_BOOK_COVER_IMPORTANT) {
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
        val targetVersion = AppVersion.fromInternalCode(targetVersionCode)
        val zhNames = isChineseLocale()

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

            // 功能清单：旧包里也放一份，便于回溯「这个包带了什么」
            File(exportDir, "DocumentFeatures.json").writeText(
                gson.toJson(buildFeatureDocument(targetVersion, zhNames))
            )

        } else {
            // == 正常输出 EYF v2.0 ==
            val configsDir = File(exportDir, "configs").apply { mkdirs() }
            val dataDir = File(exportDir, "data").apply { mkdirs() }
            val manifestList = mutableListOf<Map<String, String>>()

            // DocumentInfo.json
            // 注意：app_version_code 写的是**目标兼容版本**的内部代号（这个包模拟的是哪一版），
            // 而 upgrade_code / source_build 记录的是**真实导出者**，两者不能混为一谈。
            val docInfo = mapOf(
                "eyf_version" to "2.0",
                "app_info" to mapOf(
                    "app_id" to BuildConfig.APPLICATION_ID,
                    "app_version_code" to targetVersion.internalCode,
                    "app_version_name" to targetVersion.versionName,
                    "app_name" to "ZakoCountdown"
                ),
                "upgrade_code" to BuildConfig.UPGRADE_CODE,
                "upgrade_code_label" to BuildConfig.UPGRADE_CODE_LABEL,
                "source_build" to mapOf(
                    "version_name" to BuildConfig.VERSION_NAME,
                    "version_code" to BuildConfig.VERSION_CODE,
                    "build_id" to BuildConfig.BUILD_ID
                ),
                "export_meta" to mapOf("export_time" to System.currentTimeMillis())
            )
            File(exportDir, "DocumentInfo.json").writeText(gson.toJson(docInfo))

            // 功能清单：记录该目标版本下所有可用的功能
            File(exportDir, "DocumentFeatures.json").writeText(
                gson.toJson(buildFeatureDocument(targetVersion, zhNames))
            )
            manifestList.add(mapOf("file_path" to "DocumentFeatures.json", "type" to "FEATURE_MANIFEST"))

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

    /**
     * 生成写进 DocumentFeatures.json 的功能清单。
     * 功能名跟随导出时的界面语言，中英各一份太啰嗦，只留当前语言。
     */
    private fun buildFeatureDocument(target: AppVersion, zh: Boolean) =
        FeatureManifestDocument.build(
            target = target,
            upgradeCode = BuildConfig.UPGRADE_CODE,
            upgradeCodeLabel = BuildConfig.UPGRADE_CODE_LABEL,
            zh = zh
        )

    /** 当前界面语言是不是中文（用于决定功能清单里的显示名）。 */
    private fun isChineseLocale(): Boolean {
        return java.util.Locale.getDefault().language.equals("zh", ignoreCase = true)
    }

    // =========================================================================
    // 内存数据包装类
    // =========================================================================
    data class ParsedEyfPackage(
        val version: Float,
        val appVersionCode: Int,
        val appId: String,
        val data: EyfData,
        val tempDir: File,
        /** 备份包里记录的功能清单（旧包没有这个文件时为 null）。 */
        val features: FeatureManifestDocument? = null,
        /** 导出该备份时使用的升级代号（旧包为 null）。 */
        val upgradeCode: Int? = null,
        /** 导出该备份时的升级代号标签。 */
        val upgradeCodeLabel: String? = null
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

        // 功能清单：v2 放在根目录，v1 的旧包可能没有
        val featuresFile = File(importDir, "DocumentFeatures.json")
        val features: FeatureManifestDocument? =
            if (featuresFile.exists()) {
                try {
                    gson.fromJson(featuresFile.readText(), FeatureManifestDocument::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "DocumentFeatures.json 解析失败，忽略", e)
                    null
                }
            } else null

        if (docInfoFile.exists()) {
            // === V2 解析 ===
            val docInfoMap = gson.fromJson(docInfoFile.readText(), Map::class.java)
            val versionStr = docInfoMap["eyf_version"] as? String ?: "2.0"
            val version = versionStr.toFloatOrNull() ?: 2.0f

            if (version > SUPPORTED_EYF_VERSION) {
                importDir.deleteRecursively()
                throw Exception(context.getString(R.string.backup_error_version_incompatible, version.toString(), SUPPORTED_EYF_VERSION.toString()))
            }

            val appInfo = docInfoMap["app_info"] as? Map<*, *>
            val appId = appInfo?.get("app_id") as? String ?: ""
            val appVersionCode = (appInfo?.get("app_version_code") as? Double)?.toInt() ?: 1

            val upgradeCode = (docInfoMap["upgrade_code"] as? Double)?.toInt()
            val upgradeCodeLabel = docInfoMap["upgrade_code_label"] as? String

            val settingsFile = File(importDir, "configs/settings.json")
            val booksFile = File(importDir, "data/AgendaBook.json")
            val eventsFile = File(importDir, "data/Events.json")

            val settings: Map<String, Any?>? = if (settingsFile.exists()) gson.fromJson(settingsFile.readText(), object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type) else null
            val books: List<ExportAgendaBook>? = if (booksFile.exists()) gson.fromJson(booksFile.readText(), object : com.google.gson.reflect.TypeToken<List<ExportAgendaBook>>() {}.type) else null
            val events: List<ExportEvent>? = if (eventsFile.exists()) gson.fromJson(eventsFile.readText(), object : com.google.gson.reflect.TypeToken<List<ExportEvent>>() {}.type) else null

            return@withContext ParsedEyfPackage(
                version, appVersionCode, appId, EyfData(settings, books, events), importDir,
                features = features,
                upgradeCode = upgradeCode,
                upgradeCodeLabel = upgradeCodeLabel
            )

        } else if (manifestFileV1.exists()) {
            // === V1 解析 (旧版兼容) ===
            val manifest = gson.fromJson(manifestFileV1.readText(), EyfManifest::class.java)
            val version = manifest.eyfVersion.toFloatOrNull() ?: 1.0f

            val dataFile = File(importDir, "data.json")
            if (!dataFile.exists()) {
                importDir.deleteRecursively()
                throw Exception(context.getString(R.string.backup_error_corrupt_v1))
            }
            val data = gson.fromJson(dataFile.readText(), EyfData::class.java)

            return@withContext ParsedEyfPackage(
                version, manifest.appVersionCode, manifest.appId, data, importDir,
                features = features
            )

        } else {
            importDir.deleteRecursively()
            throw Exception(context.getString(R.string.backup_error_unknown_format))
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
        if (!importDir.exists()) throw Exception(context.getString(R.string.backup_error_cache_lost))

        val isV2 = parsedPackage.version >= 2.0f
        val mediaSearchDir = File(importDir, if (isV2) "assets/Images" else "media")
        // 为了提高兼容性，如果在 assets/Images 里找不到，退回 media/ 寻找
        val fallbackMediaDir = File(importDir, "media")

        val prefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).edit()
        val bookIdMapping = mutableMapOf<Long, Long>()

        // 1. 恢复设置
        for (node in rootNodes.filter { it.isChecked && it.type == NodeType.SETTING }) {
            if (!node.id.startsWith("ctrl_")) {
                // 跨版本兼容：旧备份里的键名（key_xxx / enable_xxx …）在这里翻译成新的语义化键名
                val key = PreferenceKeys.migrateKeyName(node.id.removePrefix("set_"))
                var v = node.rawSettingValue

                // 拦截媒体路径配置，从备份包的媒体库恢复文件，并生成本地新 URI
                if (key == PreferenceKeys.HOME_WALLPAPER_URI || key == PreferenceKeys.DRAWER_HEADER_IMAGE_URI || key == PreferenceKeys.DEFAULT_BOOK_COVER_ALL || key == PreferenceKeys.DEFAULT_BOOK_COVER_IMPORTANT) {
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
                        // 必须精准还原 Int / Float，否则读取时会抛 ClassCastException
                        if (PreferenceKeys.isIntSetting(key)) {
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
                    throw SecurityException(context.getString(R.string.backup_error_malicious_path, entry.name))
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
