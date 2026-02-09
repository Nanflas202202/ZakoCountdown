// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/EventDao.kt
package com.errorsiayusulif.zakocountdown.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface EventDao {

    // --- Events (原有) ---
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

    // --- 【新增】日程本相关查询 ---

    // 获取某个日程本下的所有日程
    @Query("SELECT * FROM countdown_events WHERE bookId = :bookId ORDER BY targetDate ASC")
    fun getEventsByBookId(bookId: Long): LiveData<List<CountdownEvent>>

    // --- Agenda Books (新增) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: AgendaBook)

    @Update
    suspend fun updateBook(book: AgendaBook)

    @Delete
    suspend fun deleteBook(book: AgendaBook)

    @Query("SELECT * FROM agenda_books ORDER BY createTime DESC")
    fun getAllBooks(): LiveData<List<AgendaBook>>

    @Query("SELECT * FROM agenda_books ORDER BY createTime DESC")
    suspend fun getAllBooksSuspend(): List<AgendaBook>

    @Query("SELECT * FROM agenda_books WHERE id = :id")
    suspend fun getBookById(id: Long): AgendaBook?

    // 当删除一个日程本时，将该本子下的所有日程移回“默认”（bookId = null）
    @Query("UPDATE countdown_events SET bookId = NULL WHERE bookId = :bookId")
    suspend fun detachEventsFromBook(bookId: Long)
}