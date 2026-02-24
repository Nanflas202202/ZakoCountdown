// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/BackupModels.kt
package com.errorsiayusulif.zakocountdown.data

import com.google.gson.annotations.SerializedName

// ... (EyfManifest, EyfData, ExportAgendaBook, ExportEvent 保持不变) ...
data class EyfManifest(
    @SerializedName("eyf_version") val eyfVersion: String = "1.0",
    @SerializedName("app_id") val appId: String = "com.errorsiayusulif.zakocountdown",
    @SerializedName("app_version_code") val appVersionCode: Int = 1,
    @SerializedName("export_time") val exportTime: Long = System.currentTimeMillis()
)

data class EyfData(
    @SerializedName("settings") val settings: Map<String, Any?>?,
    @SerializedName("agenda_books") val agendaBooks: List<ExportAgendaBook>?,
    @SerializedName("events") val events: List<ExportEvent>?
)

data class ExportAgendaBook(
    val originalId: Long,
    val name: String,
    val colorHex: String?,
    val coverImageFileName: String?,
    val cardAlpha: Float?,
    val sortOrder: Int
)

data class ExportEvent(
    val title: String,
    val targetDate: Long,
    val isImportant: Boolean,
    val originalBookId: Long?,
    val colorHex: String?,
    val backgroundFileName: String?,
    val isPinned: Boolean,
    val displayMode: String,
    val cardAlpha: Float?
)

// --- UI 节点实体 (树形结构升级) ---
enum class NodeType { HEADER, SETTING, BOOK, EVENT, SUB_OPTION }

enum class ConflictLevel { NONE, WARNING, ERROR }

data class SelectableNode(
    val type: NodeType,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    var isChecked: Boolean = true,

    // 树形结构支持
    var isExpanded: Boolean = false,
    val children: MutableList<SelectableNode> = mutableListOf(),

    // 冲突信息
    val conflictLevel: ConflictLevel = ConflictLevel.NONE,
    val conflictMessage: String? = null,

    // 原始数据
    val rawSettingValue: Any? = null,
    val rawBook: ExportAgendaBook? = null,
    val rawEvent: ExportEvent? = null,

    // 子选项类型 (用于 SUB_OPTION)
    val subOptionType: SubOptionType? = null
)

enum class SubOptionType { COLOR, COVER, ALPHA }