// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/BackupManager.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import android.net.Uri
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

    /**
     * 导出为 .eyf 文件 (存放在 Cache 目录)
     * 返回生成文件的 File 对象
     */
    suspend fun exportToEyf(
        context: Context,
        repository: EventRepository,
        prefManager: PreferenceManager,
        config: ExportConfig
    ): File = withContext(Dispatchers.IO) {

        val exportDir = File(context.cacheDir, "eyf_export_${System.currentTimeMillis()}")
        if (!exportDir.exists()) exportDir.mkdirs()

        val mediaDir = File(exportDir, "media")
        if (!mediaDir.exists()) mediaDir.mkdirs()

        // 1. 生成 Manifest
        val manifestFile = File(exportDir, "manifest.json")
        manifestFile.writeText(gson.toJson(EyfManifest()))

        // 2. 生成 Preferences (如果允许)
        if (config.includeSettings) {
            val prefsMap = getExportablePreferences(context)
            val prefsFile = File(exportDir, "preferences.json")
            prefsFile.writeText(gson.toJson(prefsMap))
        }

        // 3. 处理业务数据 (Data)
        val exportBooks = mutableListOf<ExportAgendaBook>()
        val exportEvents = mutableListOf<ExportEvent>()

        if (config.includeBooks) {
            val books = repository.getAllBooksSuspend()
            books.forEach { book ->
                var coverFileName: String? = null
                // 如果需要导出封面，且封面存在，复制文件到 media 目录
                if (config.includeBookCovers && book.coverImageUri != null) {
                    coverFileName = "book_${book.id}_cover.png"
                    copyUriToFile(context, Uri.parse(book.coverImageUri), File(mediaDir, coverFileName))
                }

                exportBooks.add(ExportAgendaBook(
                    originalId = book.id,
                    name = book.name,
                    colorHex = if (config.includeBookColors) book.colorHex else null,
                    coverImageFileName = coverFileName,
                    cardAlpha = if (config.includeBookAlphas) book.cardAlpha else null,
                    sortOrder = book.sortOrder
                ))
            }
        }

        if (config.includeEvents) {
            val events = repository.getAllEventsSuspend()
            events.forEach { event ->
                var bgFileName: String? = null
                if (config.includeEventCovers && event.backgroundUri != null) {
                    bgFileName = "event_${event.id}_bg.png"
                    copyUriToFile(context, Uri.parse(event.backgroundUri), File(mediaDir, bgFileName))
                }

                exportEvents.add(ExportEvent(
                    title = event.title,
                    targetDate = event.targetDate.time,
                    isImportant = event.isImportant,
                    originalBookId = event.bookId,
                    colorHex = if (config.includeEventColors) event.colorHex else null,
                    backgroundFileName = bgFileName,
                    isPinned = event.isPinned,
                    displayMode = event.displayMode,
                    cardAlpha = if (config.includeEventAlphas) event.cardAlpha else null
                ))
            }
        }

        val dataFile = File(exportDir, "data.json")
        dataFile.writeText(gson.toJson(EyfData(exportBooks, exportEvents)))

        // 4. 将整个目录打包为 ZIP (.eyf)
        val zipFile = File(context.cacheDir, "ZakoBackup_${System.currentTimeMillis()}.eyf")
        zipDirectory(exportDir, zipFile)

        // 清理缓存目录
        exportDir.deleteRecursively()

        return@withContext zipFile
    }

    /**
     * 从 .eyf 文件导入数据
     */
    suspend fun importFromEyf(
        context: Context,
        uri: Uri,
        repository: EventRepository,
        prefManager: PreferenceManager,
        config: ImportConfig
    ) = withContext(Dispatchers.IO) {

        val importDir = File(context.cacheDir, "eyf_import_${System.currentTimeMillis()}")
        if (!importDir.exists()) importDir.mkdirs()

        // 1. 解压文件
        unzip(context, uri, importDir)

        // 2. 检查 Manifest (合法性校验)
        val manifestFile = File(importDir, "manifest.json")
        if (!manifestFile.exists()) throw Exception("非法的 EYF 格式：缺失 manifest.json")
        val manifest = gson.fromJson(manifestFile.readText(), EyfManifest::class.java)
        if (manifest.appId != "com.errorsiayusulif.zakocountdown") {
            // 可在此处作拦截或警告处理
        }

        // 3. 恢复设置项
        if (config.importSettings) {
            val prefsFile = File(importDir, "preferences.json")
            if (prefsFile.exists()) {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                val prefsMap: Map<String, Any> = gson.fromJson(prefsFile.readText(), type)
                restorePreferences(context, prefsMap)
            }
        }

        // 4. 恢复业务数据
        val dataFile = File(importDir, "data.json")
        if (dataFile.exists()) {
            val eyfData = gson.fromJson(dataFile.readText(), EyfData::class.java)
            val mediaDir = File(importDir, "media")

            // 【核心】：记录旧 ID 到 新生成的数据库 ID 的映射
            val bookIdMapping = mutableMapOf<Long, Long>()

            // A. 先恢复日程本 (因为日程需要关联它的 ID)
            if (config.importBooks && eyfData.agendaBooks != null) {
                for (eb in eyfData.agendaBooks) {
                    var newCoverUri: String? = null
                    if (config.importBookCovers && eb.coverImageFileName != null) {
                        val srcFile = File(mediaDir, eb.coverImageFileName)
                        if (srcFile.exists()) {
                            val destFile = File(context.filesDir, "book_cover_${System.currentTimeMillis()}_${srcFile.name}")
                            srcFile.copyTo(destFile, overwrite = true)
                            newCoverUri = Uri.fromFile(destFile).toString()
                        }
                    }

                    val newBook = AgendaBook(
                        name = eb.name,
                        colorHex = if (config.importBookColors) eb.colorHex ?: "#212121" else "#212121",
                        coverImageUri = newCoverUri,
                        cardAlpha = if (config.importBookAlphas) eb.cardAlpha ?: 1.0f else 1.0f,
                        sortOrder = eb.sortOrder
                    )
                    // 插入数据库并获取新 ID
                    val newId = repository.insertBookAndGetId(newBook)
                    bookIdMapping[eb.originalId] = newId
                }
            }

            // B. 恢复日程
            if (config.importEvents && eyfData.events != null) {
                for (ev in eyfData.events) {
                    var newBgUri: String? = null
                    if (config.importEventCovers && ev.backgroundFileName != null) {
                        val srcFile = File(mediaDir, ev.backgroundFileName)
                        if (srcFile.exists()) {
                            val destFile = File(context.filesDir, "event_bg_${System.currentTimeMillis()}_${srcFile.name}")
                            srcFile.copyTo(destFile, overwrite = true)
                            newBgUri = Uri.fromFile(destFile).toString()
                        }
                    }

                    // 映射新的 Book ID。如果旧的没找到或者没导入，归入默认分类 (null)
                    val remappedBookId = if (ev.originalBookId != null) bookIdMapping[ev.originalBookId] else null

                    val newEvent = CountdownEvent(
                        title = ev.title,
                        targetDate = Date(ev.targetDate),
                        isImportant = ev.isImportant,
                        bookId = remappedBookId,
                        colorHex = if (config.importEventColors) ev.colorHex else null,
                        backgroundUri = newBgUri,
                        isPinned = ev.isPinned,
                        displayMode = ev.displayMode,
                        cardAlpha = if (config.importEventAlphas) ev.cardAlpha else null
                    )
                    repository.insert(newEvent)
                }
            }
        }

        // 清理缓存
        importDir.deleteRecursively()
    }

    // --- 辅助方法 ---

    private fun copyUriToFile(context: Context, uri: Uri, destFile: File) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val outputStream = FileOutputStream(destFile)
            inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val zipEntry = ZipEntry(sourceDir.toPath().relativize(file.toPath()).toString().replace("\\", "/"))
                zos.putNextEntry(zipEntry)
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun unzip(context: Context, zipUri: Uri, targetDir: File) {
        val inputStream = context.contentResolver.openInputStream(zipUri) ?: throw Exception("Cannot open ZIP")
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                // 防御 ZIP 目录穿越漏洞
                val canonicalDestPath = file.canonicalPath
                val canonicalDirPath = targetDir.canonicalPath
                if (!canonicalDestPath.startsWith(canonicalDirPath + File.separator)) {
                    throw SecurityException("Entry is outside of the target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { out ->
                        zis.copyTo(out)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    // 获取并过滤我们真正需要的 Preference，以防导出系统缓存垃圾
    private fun getExportablePreferences(context: Context): Map<String, Any?> {
        val prefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE)
        return prefs.all.filterKeys {
            // 这里可以过滤掉不需要备份的 key，比如 Widget IDs (由于跨设备，旧的微件ID无效)
            !it.startsWith("widget_")
        }
    }

    private fun restorePreferences(context: Context, map: Map<String, Any>) {
        val prefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).edit()
        for ((key, value) in map) {
            when (value) {
                is Boolean -> prefs.putBoolean(key, value)
                is String -> prefs.putString(key, value)
                // Gson 解析数字默认转为 Double，根据类型需要强转
                is Double -> {
                    // 判断这个key我们原本存的是 Int 还是 Float (根据您的系统设定)
                    // 为了简化，由于 Android Prefs 常用 Int 和 Float，这里通过名称猜测或存为 Float
                    if (key.contains("duration") || key.contains("delay") || key.contains("alpha")) {
                        prefs.putInt(key, value.toInt())
                    } else {
                        prefs.putFloat(key, value.toFloat())
                    }
                }
                is Float -> prefs.putFloat(key, value)
                is Int -> prefs.putInt(key, value)
            }
        }
        prefs.apply()
    }
}