// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/EventDao.kt
package com.errorsiayusulif.zakocountdown.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface EventDao {

    // --- 写操作：必须是 suspend ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CountdownEvent)

    @Update
    suspend fun updateEvent(event: CountdownEvent)

    @Delete
    suspend fun deleteEvent(event: CountdownEvent)

    // --- 特殊的写操作：返回新生成的ID，也必须是 suspend ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventAndGetId(event: CountdownEvent): Long

    // --- 读操作 ---

    // 方案A: 返回 LiveData (自动在后台线程执行，所以不能是 suspend)
    // 【修改】确保排序优先级
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
}