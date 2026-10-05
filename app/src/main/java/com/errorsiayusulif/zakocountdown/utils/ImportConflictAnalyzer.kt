// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/ImportConflictAnalyzer.kt
package com.errorsiayusulif.zakocountdown.utils

import android.content.Context
import com.errorsiayusulif.zakocountdown.BuildConfig
import com.errorsiayusulif.zakocountdown.R
import com.errorsiayusulif.zakocountdown.data.*

object ImportConflictAnalyzer {

    suspend fun analyze(
        context: Context,
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
                    title = context.getString(R.string.conflict_cross_version_title),
                    subtitle = context.getString(R.string.conflict_cross_version_subtitle, appVersionCode, BuildConfig.VERSION_CODE),
                    isChecked = true,
                    conflictLevel = ConflictLevel.WARNING,
                    conflictMessage = context.getString(R.string.conflict_cross_version_message)
                )
            )
        } else if (appId != BuildConfig.APPLICATION_ID) {
            nodes.add(
                SelectableNode(
                    type = NodeType.HEADER,
                    id = "hdr_cross_app_warning",
                    title = context.getString(R.string.conflict_cross_app_title),
                    subtitle = context.getString(R.string.conflict_cross_app_subtitle, appId),
                    isChecked = true,
                    conflictLevel = ConflictLevel.WARNING,
                    conflictMessage = context.getString(R.string.conflict_cross_app_message)
                )
            )
        }

        // --- 2. 分析设置项 ---
        if (!eyfData.settings.isNullOrEmpty()) {
            // 备份包里记录了导出时的功能清单 —— 直接展示给用户，比只报 internalCode 直观得多
            parsedPackage.features?.let { doc ->
                val names = doc.features.joinToString("、") { it.displayName }
                nodes.add(
                    SelectableNode(
                        type = NodeType.HEADER,
                        id = "hdr_features",
                        title = context.getString(R.string.import_features_title, doc.featureCount),
                        subtitle = context.getString(R.string.import_features_subtitle, doc.targetVersionName, names),
                        isChecked = true,
                        conflictLevel = ConflictLevel.NONE
                    )
                )
            }

            nodes.add(SelectableNode(NodeType.HEADER, "hdr_settings", context.getString(R.string.backup_header_settings), isChecked = true))
            for ((key, value) in eyfData.settings) {
                // 如果是新版本的专有设置，也可以在这里做检测（假设低版本遇到高版本设置）
                // 但通常低版本的 SharedPreferences 遇到未知的 Key 会直接忽略，不会造成崩溃，所以这里直接列出。
                nodes.add(
                    SelectableNode(
                        type = NodeType.SETTING,
                        id = "set_$key",
                        title = context.getString(R.string.conflict_setting_title, key),
                        subtitle = context.getString(R.string.conflict_setting_value, value),
                        conflictLevel = ConflictLevel.NONE,
                        rawSettingValue = value
                    )
                )
            }
        }

        // --- 3. 分析日程本 ---
        if (!eyfData.agendaBooks.isNullOrEmpty()) {
            nodes.add(SelectableNode(NodeType.HEADER, "hdr_books", context.getString(R.string.nav_agenda_books), isChecked = true))
            val localBooks = repository.getAllBooksSuspend()
            val localBookNames = localBooks.map { it.name }.toSet()

            for (book in eyfData.agendaBooks) {
                var level = ConflictLevel.NONE
                var msg: String? = null

                if (localBookNames.contains(book.name)) {
                    level = ConflictLevel.WARNING
                    msg = context.getString(R.string.conflict_duplicate_book)
                }

                nodes.add(
                    SelectableNode(
                        type = NodeType.BOOK,
                        id = "book_${book.originalId}",
                        title = book.name,
                        subtitle = context.getString(
                            R.string.conflict_book_subtitle,
                            book.colorHex ?: context.getString(R.string.conflict_default_color)
                        ),
                        conflictLevel = level,
                        conflictMessage = msg,
                        rawBook = book
                    )
                )
            }
        }

        // --- 4. 分析日程 ---
        if (!eyfData.events.isNullOrEmpty()) {
            nodes.add(SelectableNode(NodeType.HEADER, "hdr_events", context.getString(R.string.backup_header_events, eyfData.events.size), isChecked = true))
            val localEvents = repository.getAllEventsSuspend()
            val localEventTitles = localEvents.map { it.title }.toSet()
            val isGlobalAlphaUnlocked = preferenceManager.isGlobalAlphaUnlocked()

            for (event in eyfData.events) {
                var level = ConflictLevel.NONE
                var msg: String? = null

                if (localEventTitles.contains(event.title)) {
                    level = ConflictLevel.WARNING
                    msg = context.getString(R.string.conflict_duplicate_event)
                } else if (event.cardAlpha != null && event.cardAlpha < 1.0f && !event.isPinned && !isGlobalAlphaUnlocked) {
                    level = ConflictLevel.ERROR
                    msg = context.getString(R.string.conflict_alpha_locked)
                }

                nodes.add(
                    SelectableNode(
                        type = NodeType.EVENT,
                        id = "event_${event.title}_${event.targetDate}",
                        title = event.title,
                        subtitle = if (event.isImportant) context.getString(R.string.conflict_event_important) else context.getString(R.string.conflict_event_normal),
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