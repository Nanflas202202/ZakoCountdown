// file: app/src/main/java/com/errorsiayusulif/zakocountdown/utils/ImportConflictAnalyzer.kt
package com.errorsiayusulif.zakocountdown.utils

import com.errorsiayusulif.zakocountdown.data.EventRepository
import com.errorsiayusulif.zakocountdown.data.ExportAgendaBook
import com.errorsiayusulif.zakocountdown.data.ExportEvent
import com.errorsiayusulif.zakocountdown.data.PreferenceManager

object ImportConflictAnalyzer {

    enum class ConflictType {
        NONE,               // 正常，无冲突
        NAME_CONFLICT,      // 命名冲突 (同名日程/日程本)
        ALPHA_WARNING       // 设置冲突 (如禁用了全局透明度但卡片有透明度)
    }

    data class EventImportItem(
        val data: ExportEvent,
        var isSelected: Boolean = true, // 用户是否勾选导入
        val conflictType: ConflictType,
        val conflictMessage: String?
    )

    data class BookImportItem(
        val data: ExportAgendaBook,
        var isSelected: Boolean = true,
        val conflictType: ConflictType,
        val conflictMessage: String?
    )

    data class AnalysisResult(
        val parsedEvents: List<EventImportItem>,
        val parsedBooks: List<BookImportItem>
    )

    /**
     * 对读取到的备份数据进行冲突分析
     */
    suspend fun analyze(
        repository: EventRepository,
        preferenceManager: PreferenceManager,
        importedEvents: List<ExportEvent>?,
        importedBooks: List<ExportAgendaBook>?
    ): AnalysisResult {

        val resultEvents = mutableListOf<EventImportItem>()
        val resultBooks = mutableListOf<BookImportItem>()

        // 1. 分析日程本
        if (importedBooks != null) {
            val localBooks = repository.getAllBooksSuspend()
            val localBookNames = localBooks.map { it.name }.toSet()

            for (book in importedBooks) {
                if (localBookNames.contains(book.name)) {
                    resultBooks.add(BookImportItem(book, true, ConflictType.NAME_CONFLICT, "存在同名日程本，导入将导致重复"))
                } else {
                    resultBooks.add(BookImportItem(book, true, ConflictType.NONE, null))
                }
            }
        }

        // 2. 分析日程
        if (importedEvents != null) {
            val localEvents = repository.getAllEventsSuspend()
            val localEventTitles = localEvents.map { it.title }.toSet()
            val isGlobalAlphaUnlocked = preferenceManager.isGlobalAlphaUnlocked()

            for (event in importedEvents) {
                var conflictType = ConflictType.NONE
                var message: String? = null

                // 检查命名冲突
                if (localEventTitles.contains(event.title)) {
                    conflictType = ConflictType.NAME_CONFLICT
                    message = "存在同名日程，导入将产生重复项"
                }
                // 检查透明度设置冲突
                else if (event.cardAlpha != null && event.cardAlpha < 1.0f && !event.isPinned && !isGlobalAlphaUnlocked) {
                    conflictType = ConflictType.ALPHA_WARNING
                    message = "该卡片带有半透明效果，但您当前系统未解锁全局透明度，效果可能失效"
                }

                resultEvents.add(EventImportItem(event, true, conflictType, message))
            }
        }

        return AnalysisResult(resultEvents, resultBooks)
    }
}