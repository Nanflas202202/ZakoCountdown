// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/BackupModels.kt
package com.errorsiayusulif.zakocountdown.data

import com.google.gson.annotations.SerializedName

// --- .eyf 文件内各 JSON 映射实体 ---

data class EyfManifest(
    @SerializedName("eyf_version") val eyfVersion: String = "1.0",
    @SerializedName("app_id") val appId: String = "com.errorsiayusulif.zakocountdown",
    @SerializedName("app_version_code") val appVersionCode: Int = 1,
    @SerializedName("export_time") val exportTime: Long = System.currentTimeMillis()
)

data class EyfData(
    @SerializedName("agenda_books") val agendaBooks: List<ExportAgendaBook>?,
    @SerializedName("events") val events: List<ExportEvent>?
)

data class ExportAgendaBook(
    val originalId: Long,
    val name: String,
    val colorHex: String?,           // 若配置不导出颜色，则为 null
    val coverImageFileName: String?, // 相对路径，如 "cover_123.png"，若不导出封面则为 null
    val cardAlpha: Float?,
    val sortOrder: Int
)

data class ExportEvent(
    val title: String,
    val targetDate: Long,
    val isImportant: Boolean,
    val originalBookId: Long?,       // 关联的日程本原始 ID
    val colorHex: String?,
    val backgroundFileName: String?, // 相对路径
    val isPinned: Boolean,
    val displayMode: String,
    val cardAlpha: Float?
)

// --- 控制配置实体 ---

data class ExportConfig(
    val includeEvents: Boolean = true,
    val includeEventColors: Boolean = true,
    val includeEventCovers: Boolean = true,
    val includeEventAlphas: Boolean = true,

    val includeBooks: Boolean = true,
    val includeBookColors: Boolean = true,
    val includeBookCovers: Boolean = true,
    val includeBookAlphas: Boolean = true,

    val includeSettings: Boolean = true
)

data class ImportConfig(
    val importEvents: Boolean = true,
    val importEventColors: Boolean = true,
    val importEventCovers: Boolean = true,
    val importEventAlphas: Boolean = true,

    val importBooks: Boolean = true,
    val importBookColors: Boolean = true,
    val importBookCovers: Boolean = true,
    val importBookAlphas: Boolean = true,

    val importSettings: Boolean = true
)