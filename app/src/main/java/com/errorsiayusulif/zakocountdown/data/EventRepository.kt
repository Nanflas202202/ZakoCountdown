// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/EventRepository.kt
package com.errorsiayusulif.zakocountdown.data

import androidx.lifecycle.LiveData

class EventRepository(private val eventDao: EventDao) {

    // --- Events ---
    val allEvents: LiveData<List<CountdownEvent>> = eventDao.getAllEvents()

    suspend fun insert(event: CountdownEvent) = eventDao.insertEvent(event)
    suspend fun update(event: CountdownEvent) = eventDao.updateEvent(event)
    suspend fun delete(event: CountdownEvent) = eventDao.deleteEvent(event)
    suspend fun insertAndGetId(event: CountdownEvent): Long = eventDao.insertEventAndGetId(event)
    suspend fun getAllEventsSuspend() = eventDao.getAllEventsSuspend()
    suspend fun getEventById(id: Long) = eventDao.getEventById(id)
    suspend fun getEventsByTitle(title: String) = eventDao.getEventsByTitle(title)
    suspend fun getImportantEvents() = eventDao.getImportantEvents()
    suspend fun findPinnedEvent() = eventDao.findPinnedEvent()
    suspend fun getWidgetEvent() = eventDao.getWidgetEvent()
    suspend fun getEventsByIds(ids: List<Long>) = eventDao.getEventsByIds(ids)

    // --- 【补全】批量操作代理 ---
    suspend fun updateEventsBookId(bookId: Long, eventIds: List<Long>) = eventDao.updateEventsBookId(bookId, eventIds)
    suspend fun detachEventsFromBook(bookId: Long) = eventDao.detachEventsFromBook(bookId)

    // --- Agenda Books ---
    val allBooks: LiveData<List<AgendaBook>> = eventDao.getAllBooks()

    suspend fun insertBook(book: AgendaBook) = eventDao.insertBook(book)
    suspend fun insertBookAndGetId(book: AgendaBook): Long = eventDao.insertBookAndGetId(book) // 补全
    suspend fun updateBook(book: AgendaBook) = eventDao.updateBook(book)
    suspend fun updateBooks(books: List<AgendaBook>) = eventDao.updateBooks(books) // 补全

    suspend fun deleteBook(book: AgendaBook) {
        // 删除前先解绑日程
        eventDao.detachEventsFromBook(book.id)
        eventDao.deleteBook(book)
    }

    suspend fun getAllBooksSuspend() = eventDao.getAllBooksSuspend()
    suspend fun getBookById(id: Long) = eventDao.getBookById(id)
    fun getEventsByBookId(bookId: Long) = eventDao.getEventsByBookId(bookId)
}