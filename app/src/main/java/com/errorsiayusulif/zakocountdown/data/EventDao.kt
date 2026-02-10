// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/EventDao.kt
package com.errorsiayusulif.zakocountdown.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface EventDao {

    // --- Events ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CountdownEvent)

    @Update
    suspend fun updateEvent(event: CountdownEvent)

    @Delete
    suspend fun deleteEvent(event: CountdownEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventAndGetId(event: CountdownEvent): Long

    @Query("SELECT * FROM countdown_events ORDER BY isPinned DESC, isImportant DESC, targetDate ASC")
    fun getAllEvents(): LiveData<List<CountdownEvent>>

    @Query("SELECT * FROM countdown_events ORDER BY isPinned DESC, isImportant DESC, targetDate ASC")
    suspend fun getAllEventsSuspend(): List<CountdownEvent>

    @Query("SELECT * FROM countdown_events WHERE id = :id")
    suspend fun getEventById(id: Long): CountdownEvent?

    @Query("SELECT * FROM countdown_events WHERE title = :title")
    suspend fun getEventsByTitle(title: String): List<CountdownEvent>

    @Query("SELECT * FROM countdown_events WHERE isImportant = 1 ORDER BY targetDate ASC")
    suspend fun getImportantEvents(): List<CountdownEvent>

    @Query("SELECT * FROM countdown_events WHERE targetDate >= :currentDate ORDER BY isPinned DESC, targetDate ASC LIMIT 1")
    suspend fun getWidgetEvent(currentDate: Long = System.currentTimeMillis()): CountdownEvent?

    @Query("SELECT * FROM countdown_events WHERE isPinned = 1 LIMIT 1")
    suspend fun findPinnedEvent(): CountdownEvent?

    @Query("SELECT * FROM countdown_events WHERE id IN (:ids)")
    suspend fun getEventsByIds(ids: List<Long>): List<CountdownEvent>

    // --- 日程本关联查询 ---
    @Query("SELECT * FROM countdown_events WHERE bookId = :bookId ORDER BY targetDate ASC")
    fun getEventsByBookId(bookId: Long): LiveData<List<CountdownEvent>>

    // --- 【补全】批量操作 ---

    // 将指定 IDs 的日程移动到 bookId
    @Query("UPDATE countdown_events SET bookId = :bookId WHERE id IN (:eventIds)")
    suspend fun updateEventsBookId(bookId: Long, eventIds: List<Long>)

    // 将指定 IDs 的日程移出 (设为默认)
    @Query("UPDATE countdown_events SET bookId = NULL WHERE id IN (:eventIds)")
    suspend fun clearBookIdForEvents(eventIds: List<Long>)

    // 当删除日程本时，将其下的日程重置为默认
    @Query("UPDATE countdown_events SET bookId = NULL WHERE bookId = :bookId")
    suspend fun detachEventsFromBook(bookId: Long)

    // --- Agenda Books ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: AgendaBook)

    // 【补全】插入并返回 ID
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookAndGetId(book: AgendaBook): Long

    @Update
    suspend fun updateBook(book: AgendaBook)

    // 【补全】批量更新日程本 (用于排序)
    @Update
    suspend fun updateBooks(books: List<AgendaBook>)

    @Delete
    suspend fun deleteBook(book: AgendaBook)

    // 【修改】改为按 sortOrder 排序，其次按创建时间
    @Query("SELECT * FROM agenda_books ORDER BY sortOrder ASC, createTime DESC")
    fun getAllBooks(): LiveData<List<AgendaBook>>

    @Query("SELECT * FROM agenda_books ORDER BY sortOrder ASC, createTime DESC")
    suspend fun getAllBooksSuspend(): List<AgendaBook>

    @Query("SELECT * FROM agenda_books WHERE id = :id")
    suspend fun getBookById(id: Long): AgendaBook?
}