// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/home/HomeViewModel.kt
package com.errorsiayusulif.zakocountdown.ui.home

import android.app.Application
import androidx.lifecycle.*
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.EventRepository
import com.errorsiayusulif.zakocountdown.utils.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val repository: EventRepository,
    application: Application
) : AndroidViewModel(application) {

    val allEvents: LiveData<List<CountdownEvent>> = repository.allEvents

    fun insert(event: CountdownEvent) = viewModelScope.launch {
        repository.insert(event)
    }

    fun delete(event: CountdownEvent) = viewModelScope.launch {
        repository.delete(event)
        AlarmScheduler.cancelReminder(getApplication(), event)
    }

    fun update(event: CountdownEvent) = viewModelScope.launch {
        if (event.isPinned) {
            val currentPinned = repository.findPinnedEvent()
            currentPinned?.let {
                if (it.id != event.id) {
                    repository.update(it.copy(isPinned = false))
                }
            }
        }
        repository.update(event)
        AlarmScheduler.scheduleReminder(getApplication(), event)
    }

    fun deleteDevModeEvents() = viewModelScope.launch {
        val eventsToDelete = withContext(Dispatchers.IO) {
            repository.getEventsByTitle("EnterDevMode")
        }
        eventsToDelete.forEach {
            repository.delete(it)
            AlarmScheduler.cancelReminder(getApplication(), it)
        }
    }

    // --- 【修复】明确地在这里声明这个方法 ---
    suspend fun getEventById(eventId: Long): CountdownEvent? {
        return repository.getEventById(eventId)
    }
}

class HomeViewModelFactory(
    private val repository: EventRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}