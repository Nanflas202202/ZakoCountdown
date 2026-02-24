// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/ImportConflictAnalyzer.kt
package com.errorsiayusulif.zakocountdown.utils

import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.data.*

object ImportConflictAnalyzer {

    suspend fun analyze(
        repository: EventRepository,
        preferenceManager: PreferenceManager,
        parsedPackage: BackupManager.ParsedEyfPackage
    ): List<SelectableNode> {

        val nodes = mutableListOf<SelectableNode>()
        val manifest = parsedPackage.manifest
        val eyfData = parsedPackage.data

        // 版本降级警告
        if (manifest.appVersionCode > BuildConfig.VERSION_CODE) {
            nodes.add(
                SelectableNode(
                    type = NodeType.HEADER,
                    id = "hdr_downgrade_warning",
                    title = "跨版本恢复警告",
                    subtitle = "备份来源版本 (v${manifest.appVersionCode}) 高于当前版本",
                    isChecked = true,
                    conflictLevel = ConflictLevel.WARNING,
                    conflictMessage = "可能会丢失部分新版特性数据。"
                )
            )
        } else if (manifest.appId != BuildConfig.APPLICATION_ID) {
            nodes.add(
                SelectableNode(
                    type = NodeType.HEADER,
                    id = "hdr_cross_app_warning",
                    title = "跨应用数据",
                    subtitle = "来源: ${manifest.appId}",
                    isChecked = true,
                    conflictLevel = ConflictLevel.WARNING,
                    conflictMessage = "这不是标准备份，可能无法完全兼容。"
                )
            )
        }

        // 1. 设置
        if (!eyfData.settings.isNullOrEmpty()) {
            nodes.add(SelectableNode(NodeType.HEADER, "hdr_settings", "应用配置", isChecked = true))
            for ((key, value) in eyfData.settings) {
                nodes.add(
                    SelectableNode(
                        type = NodeType.SETTING,
                        id = "set_$key",
                        title = "配置项: $key",
                        subtitle = "值: $value",
                        conflictLevel = ConflictLevel.NONE,
                        rawSettingValue = value
                    )
                )
            }
        }

        // 2. 日程本
        if (!eyfData.agendaBooks.isNullOrEmpty()) {
            nodes.add(SelectableNode(NodeType.HEADER, "hdr_books", "日程集", isChecked = true))
            val localBooks = repository.getAllBooksSuspend()
            val localBookNames = localBooks.map { it.name }.toSet()

            for (book in eyfData.agendaBooks) {
                var level = ConflictLevel.NONE
                var msg: String? = null

                if (localBookNames.contains(book.name)) {
                    level = ConflictLevel.WARNING
                    msg = "存在同名日程集，导入将导致重复"
                }

                nodes.add(
                    SelectableNode(
                        type = NodeType.BOOK,
                        id = "book_${book.originalId}",
                        title = book.name,
                        subtitle = "标识色: ${book.colorHex ?: "默认"}",
                        conflictLevel = level,
                        conflictMessage = msg,
                        rawBook = book
                    )
                )
            }
        }

        // 3. 日程
        if (!eyfData.events.isNullOrEmpty()) {
            nodes.add(SelectableNode(NodeType.HEADER, "hdr_events", "日程卡片", isChecked = true))
            val localEvents = repository.getAllEventsSuspend()
            val localEventTitles = localEvents.map { it.title }.toSet()
            val isGlobalAlphaUnlocked = preferenceManager.isGlobalAlphaUnlocked()

            for (event in eyfData.events) {
                var level = ConflictLevel.NONE
                var msg: String? = null

                if (localEventTitles.contains(event.title)) {
                    level = ConflictLevel.WARNING
                    msg = "存在同名日程，导入将产生重复项"
                } else if (event.cardAlpha != null && event.cardAlpha < 1.0f && !event.isPinned && !isGlobalAlphaUnlocked) {
                    level = ConflictLevel.ERROR
                    msg = "冲突：使用了透明度，但系统设置未解锁全局透明度"
                }

                nodes.add(
                    SelectableNode(
                        type = NodeType.EVENT,
                        id = "event_${event.title}_${event.targetDate}",
                        title = event.title,
                        subtitle = if (event.isImportant) "重点日程" else "普通日程",
                        conflictLevel = level,
                        conflictMessage = msg,
                        rawEvent = event
                    )
                )
            }
        }

        return nodes
    }
}