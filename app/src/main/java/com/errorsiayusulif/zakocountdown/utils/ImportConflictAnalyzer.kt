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
        // 修复：直接从 parsedPackage 中读取提取好的属性
        val appVersionCode = parsedPackage.appVersionCode
        val appId = parsedPackage.appId
        val eyfData = parsedPackage.data

        // --- 1. 版本跨越警告 ---
        if (appVersionCode > BuildConfig.VERSION_CODE) {
            nodes.add(
                SelectableNode(
                    type = NodeType.HEADER,
                    id = "hdr_downgrade_warning",
                    title = "跨版本恢复警告",
                    subtitle = "备份来源版本 (v${appVersionCode}) 高于当前应用版本 (v${BuildConfig.VERSION_CODE})",
                    isChecked = true,
                    conflictLevel = ConflictLevel.WARNING,
                    conflictMessage = "您可以继续导入，但由于您当前的版本较旧，某些新的设置项或个性化功能可能被忽略或失效。"
                )
            )
        } else if (appId != BuildConfig.APPLICATION_ID) {
            nodes.add(
                SelectableNode(
                    type = NodeType.HEADER,
                    id = "hdr_cross_app_warning",
                    title = "跨应用数据识别",
                    subtitle = "数据源: ${appId}",
                    isChecked = true,
                    conflictLevel = ConflictLevel.WARNING,
                    conflictMessage = "这似乎不是由 ZakoCountdown 生成的标准备份，强行导入可能导致崩溃。"
                )
            )
        }

        // --- 2. 分析设置项 ---
        if (!eyfData.settings.isNullOrEmpty()) {
            nodes.add(SelectableNode(NodeType.HEADER, "hdr_settings", "应用配置", isChecked = true))
            for ((key, value) in eyfData.settings) {
                // 如果是新版本的专有设置，也可以在这里做检测（假设低版本遇到高版本设置）
                // 但通常低版本的 SharedPreferences 遇到未知的 Key 会直接忽略，不会造成崩溃，所以这里直接列出。
                nodes.add(
                    SelectableNode(
                        type = NodeType.SETTING,
                        id = "set_$key",
                        title = "配置项: $key",
                        subtitle = "导入值: $value",
                        conflictLevel = ConflictLevel.NONE,
                        rawSettingValue = value
                    )
                )
            }
        }

        // --- 3. 分析日程本 ---
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

        // --- 4. 分析日程 ---
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
                    msg = "冲突：使用了卡片透明度，但当前系统未解锁全局透明度"
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