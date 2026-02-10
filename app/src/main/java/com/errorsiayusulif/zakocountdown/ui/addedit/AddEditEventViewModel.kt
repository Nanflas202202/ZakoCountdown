package com.errorsiayusulif.zakocountdown.ui.addedit

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.EventRepository
import com.errorsiayusulif.zakocountdown.utils.AlarmScheduler
import com.errorsiayusulif.zakocountdown.widget.WidgetUpdateReceiver
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class AddEditEventViewModel(
    private val repository: EventRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _eventTitle = MutableLiveData<String?>()
    val eventTitle: LiveData<String?> = _eventTitle

    private val _selectedDateTime = MutableLiveData<Calendar>(Calendar.getInstance())
    val selectedDateTime: LiveData<Calendar> = _selectedDateTime

    private val _eventBookId = MutableLiveData<Long?>()
    val eventBookId: LiveData<Long?> = _eventBookId

    private var currentEventId: Long? = null
    private var isNewEvent: Boolean = true
    private var originalEvent: CountdownEvent? = null

    fun start(eventId: Long?, defaultBookId: Long = -1L) {
        currentEventId = eventId
        if (eventId == null) {
            isNewEvent = true
            _eventTitle.value = ""
            // 如果传入了有效的 defaultBookId (> 0)，则默认选中该本子
            if (defaultBookId > 0) {
                _eventBookId.value = defaultBookId
            } else {
                _eventBookId.value = null
            }
            return
        }
        isNewEvent = false
        viewModelScope.launch {
            val event = repository.getEventById(eventId)
            event?.let {
                originalEvent = it
                onEventLoaded(it)
            }
        }
    }

    private fun onEventLoaded(event: CountdownEvent) {
        _eventTitle.value = event.title
        val calendar = Calendar.getInstance().apply { time = event.targetDate }
        _selectedDateTime.value = calendar
        _eventBookId.value = event.bookId
    }

    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        val calendar = _selectedDateTime.value ?: Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        _selectedDateTime.value = calendar
    }

    fun updateTime(hourOfDay: Int, minute: Int) {
        val calendar = _selectedDateTime.value ?: Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        calendar.set(Calendar.MINUTE, minute)
        _selectedDateTime.value = calendar
    }

    fun saveEvent(title: String, bookId: Long?): Boolean {
        if (title.isBlank()) return false
        val targetDate = _selectedDateTime.value?.time ?: Date()

        viewModelScope.launch {
            val eventToSave: CountdownEvent
            if (isNewEvent) {
                eventToSave = CountdownEvent(
                    title = title,
                    targetDate = targetDate,
                    bookId = bookId
                )
                val newId = repository.insertAndGetId(eventToSave)
                eventToSave.id = newId
            } else {
                eventToSave = originalEvent!!.copy(
                    title = title,
                    targetDate = targetDate,
                    bookId = bookId
                )
                repository.update(eventToSave)
            }

            AlarmScheduler.scheduleReminder(getApplication(), eventToSave)

            getApplication<Application>().sendBroadcast(
                Intent(getApplication(), WidgetUpdateReceiver::class.java).apply {
                    action = WidgetUpdateReceiver.ACTION_UPDATE_WIDGET
                }
            )
        }
        return true
    }
}

// --- 核心修复：确保这个类存在 ---
class AddEditEventViewModelFactory(
    private val repository: EventRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddEditEventViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddEditEventViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}