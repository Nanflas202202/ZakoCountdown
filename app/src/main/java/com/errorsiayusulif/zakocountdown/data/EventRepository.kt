// file: app/src/main/java/com/errorsiayusulif/zakocountdown/data/EventRepository.kt
package com.errorsiayusulif.zakocountdown.data

import androidx.lifecycle.LiveData

class EventRepository(private val eventDao: EventDao) {

    val allEvents: LiveData<List<CountdownEvent>> = eventDao.getAllEvents()

    suspend fun insert(event: CountdownEvent) {
        eventDao.insertEvent(event)
    }

    suspend fun update(event: CountdownEvent) {
        eventDao.updateEvent(event)
    }

    suspend fun delete(event: CountdownEvent) {
        eventDao.deleteEvent(event)
    }

    suspend fun insertAndGetId(event: CountdownEvent): Long {
        return eventDao.insertEventAndGetId(event)
    }

    suspend fun getAllEventsSuspend(): List<CountdownEvent> {
        return eventDao.getAllEventsSuspend()
    }

    suspend fun getEventById(id: Long): CountdownEvent? {
        return eventDao.getEventById(id)
    }

    suspend fun getEventsByTitle(title: String): List<CountdownEvent> {
        return eventDao.getEventsByTitle(title)
    }

    suspend fun getImportantEvents(): List<CountdownEvent> {
        return eventDao.getImportantEvents()
    }
    // --- 【新功能】添加这个方法 ---
    suspend fun findPinnedEvent(): CountdownEvent? {
        return eventDao.findPinnedEvent()
    }
    suspend fun getWidgetEvent(): CountdownEvent? {
        return eventDao.getWidgetEvent()
    }
}