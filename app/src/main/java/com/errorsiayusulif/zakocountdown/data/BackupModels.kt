// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/BackupModels.kt
package com.errorsiayusulif.zakocountdown.data

import com.google.gson.annotations.SerializedName

// --- 版本支持定义 ---
enum class AppVersion(val versionName: String, val internalCode: Int, val eyfFormat: Float) {
    V_0_8_9("V0.8.9-debug", 89, 1.0f),
    V_0_8_10("V0.8.10-nightly", 810, 1.0f),
    V_0_8_11("V0.8.11-nightly", 811, 1.0f),
    V_0_9_0("V0.9.0", 900, 2.0f); // 我们即将发布的新版

    companion object {
        fun fromVersionName(name: String): AppVersion {
            return values().find { it.versionName == name } ?: V_0_9_0
        }
        fun fromInternalCode(code: Int): AppVersion {
            return values().find { it.internalCode == code } ?: V_0_9_0
        }
    }
}

// ... EYF Manifest 等实体保持不变 ...
data class EyfManifest(
    @SerializedName("eyf_version") val eyfVersion: String = "2.0", // 默认 2.0
    @SerializedName("app_id") val appId: String = "com.errorsiayusulif.zakocountdown",
    @SerializedName("app_version_code") val appVersionCode: Int = AppVersion.V_0_9_0.internalCode, // 使用内部代号
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

enum class NodeType { HEADER, SETTING, BOOK, EVENT, SUB_OPTION }

enum class ConflictLevel { NONE, WARNING, ERROR }

data class SelectableNode(
    val type: NodeType,
    val id: String,
    val title: String,
    val subtitle: String? = null,
    var isChecked: Boolean = true,
    var isExpanded: Boolean = false,
    val children: MutableList<SelectableNode> = mutableListOf(),
    val conflictLevel: ConflictLevel = ConflictLevel.NONE,
    val conflictMessage: String? = null,
    val rawSettingValue: Any? = null,
    val rawBook: ExportAgendaBook? = null,
    val rawEvent: ExportEvent? = null,
    val subOptionType: SubOptionType? = null
)

enum class SubOptionType { COLOR, COVER, ALPHA }

