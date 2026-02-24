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
    private const val SUPPORTED_EYF_VERSION = 1.0f

    suspend fun exportToStream(
        context: Context,
        outputStream: OutputStream,
        repository: EventRepository,
        rootNodes: List<SelectableNode>
    ) = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "eyf_export_temp_${System.currentTimeMillis()}")
        if (exportDir.exists()) exportDir.deleteRecursively()
        exportDir.mkdirs()
        val mediaDir = File(exportDir, "media")
        mediaDir.mkdirs()

        val settingsToExport = mutableMapOf<String, Any?>()
        val booksToExport = mutableListOf<ExportAgendaBook>()
        val eventsToExport = mutableListOf<ExportEvent>()

        val allPrefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).all

        rootNodes.filter { it.isChecked }.forEach { node ->
            val includeColor = node.children.find { it.subOptionType == SubOptionType.COLOR }?.isChecked ?: true
            val includeCover = node.children.find { it.subOptionType == SubOptionType.COVER }?.isChecked ?: true
            val includeAlpha = node.children.find { it.subOptionType == SubOptionType.ALPHA }?.isChecked ?: true

            when (node.type) {
                NodeType.SETTING -> {
                    if (!node.id.startsWith("ctrl_")) {
                        val key = node.id.removePrefix("set_")
                        if (allPrefs.containsKey(key)) {
                            settingsToExport[key] = allPrefs[key]
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
                                book.id,
                                book.name,
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

        File(exportDir, "manifest.json").writeText(gson.toJson(EyfManifest(appVersionCode = BuildConfig.VERSION_CODE)))
        File(exportDir, "data.json").writeText(gson.toJson(EyfData(settingsToExport, booksToExport, eventsToExport)))

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            exportDir.walkTopDown().filter { it.isFile }.forEach { file ->
                zos.putNextEntry(ZipEntry(exportDir.toPath().relativize(file.toPath()).toString().replace("\\", "/")))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        exportDir.deleteRecursively()
    }

    data class ParsedEyfPackage(val manifest: EyfManifest, val data: EyfData)

    suspend fun parseEyf(context: Context, uri: Uri): ParsedEyfPackage = withContext(Dispatchers.IO) {
        val importDir = File(context.cacheDir, "eyf_temp_${System.currentTimeMillis()}")
        importDir.mkdirs()
        unzip(context, uri, importDir)

        val manifestFile = File(importDir, "manifest.json")
        if (!manifestFile.exists()) {
            importDir.deleteRecursively()
            throw Exception("无效的 EYF 格式：缺失 manifest.json")
        }
        val manifest = gson.fromJson(manifestFile.readText(), EyfManifest::class.java)

        val fileEyfVersion = manifest.eyfVersion.toFloatOrNull() ?: 0f
        if (fileEyfVersion > SUPPORTED_EYF_VERSION) {
            importDir.deleteRecursively()
            throw Exception("版本不兼容：备份文件版本 ($fileEyfVersion) 高于当前支持版本 ($SUPPORTED_EYF_VERSION)。请更新应用。")
        }

        val dataFile = File(importDir, "data.json")
        if (!dataFile.exists()) {
            importDir.deleteRecursively()
            throw Exception("无效的 EYF 格式：缺失 data.json")
        }
        val data = gson.fromJson(dataFile.readText(), EyfData::class.java)

        return@withContext ParsedEyfPackage(manifest, data)
    }

    suspend fun executeImport(
        context: Context,
        repository: EventRepository,
        rootNodes: List<SelectableNode>
    ) = withContext(Dispatchers.IO) {
        val importDir = context.cacheDir.listFiles()?.filter { it.name.startsWith("eyf_temp_") }?.maxByOrNull { it.lastModified() }
            ?: throw Exception("找不到临时解压文件，请重新选择文件")
        val mediaDir = File(importDir, "media")

        val prefs = context.getSharedPreferences("zako_prefs", Context.MODE_PRIVATE).edit()
        val bookIdMapping = mutableMapOf<Long, Long>()

        for (node in rootNodes.filter { it.isChecked }) {
            val includeColor = node.children.find { it.subOptionType == SubOptionType.COLOR }?.isChecked ?: true
            val includeCover = node.children.find { it.subOptionType == SubOptionType.COVER }?.isChecked ?: true
            val includeAlpha = node.children.find { it.subOptionType == SubOptionType.ALPHA }?.isChecked ?: true

            when (node.type) {
                NodeType.SETTING -> {
                    if (!node.id.startsWith("ctrl_")) {
                        val key = node.id.removePrefix("set_")
                        when (val v = node.rawSettingValue) {
                            is Boolean -> prefs.putBoolean(key, v)
                            is String -> prefs.putString(key, v)
                            is Double -> {
                                if (key.contains("duration") || key.contains("delay") || key.contains("alpha")) {
                                    prefs.putInt(key, v.toInt())
                                } else {
                                    prefs.putFloat(key, v.toFloat())
                                }
                            }
                        }
                    }
                }
                NodeType.BOOK -> {
                    val eb = node.rawBook!!
                    var newCoverUri: String? = null
                    if (includeCover && eb.coverImageFileName != null) {
                        val src = File(mediaDir, eb.coverImageFileName)
                        if (src.exists()) {
                            val dest = File(context.filesDir, "book_cover_${System.nanoTime()}.png")
                            src.copyTo(dest, true)
                            newCoverUri = Uri.fromFile(dest).toString()
                        }
                    }
                    val newId = repository.insertBookAndGetId(AgendaBook(
                        0, eb.name,
                        if(includeColor) eb.colorHex ?: "#000" else "#000",
                        System.currentTimeMillis(), newCoverUri,
                        if(includeAlpha) eb.cardAlpha ?: 1f else 1f,
                        eb.sortOrder
                    ))
                    bookIdMapping[eb.originalId] = newId
                }
                NodeType.EVENT -> {
                    val ev = node.rawEvent!!
                    var newBgUri: String? = null
                    if (includeCover && ev.backgroundFileName != null) {
                        val src = File(mediaDir, ev.backgroundFileName)
                        if (src.exists()) {
                            val dest = File(context.filesDir, "bg_${System.nanoTime()}.png")
                            src.copyTo(dest, true)
                            newBgUri = Uri.fromFile(dest).toString()
                        }
                    }
                    val mappedBookId = if (ev.originalBookId != null) bookIdMapping[ev.originalBookId] else null

                    repository.insert(CountdownEvent(
                        0, ev.title, Date(ev.targetDate), ev.isImportant, Date(),
                        if(includeColor) ev.colorHex else null,
                        newBgUri, ev.isPinned, ev.displayMode,
                        if(includeAlpha) ev.cardAlpha else null,
                        mappedBookId
                    ))
                }
                else -> {}
            }
        }
        prefs.apply()
        importDir.deleteRecursively()
    }

    private fun copyUriToFile(context: Context, uri: Uri, destFile: File) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(destFile).use { input.copyTo(it) } }
        } catch (e: Exception) { e.printStackTrace() }
    }
    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                zos.putNextEntry(ZipEntry(sourceDir.toPath().relativize(file.toPath()).toString().replace("\\", "/")))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
    private fun unzip(context: Context, zipUri: Uri, targetDir: File) {
        context.contentResolver.openInputStream(zipUri)?.let { ZipInputStream(BufferedInputStream(it)) }?.use { zis ->
            generateSequence { zis.nextEntry }.forEach { entry ->
                val file = File(targetDir, entry.name)
                if (!file.canonicalPath.startsWith(targetDir.canonicalPath)) throw SecurityException("Zip Path Traversal")
                if (entry.isDirectory) file.mkdirs() else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { zis.copyTo(it) }
                }
            }
        }
    }
}