// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/addedit/AddEditEventViewModel.kt

package com.errorsiayusulif.zakocountdown.ui.addedit

import android.app.Application
import android.content.Intent // <-- 【修复】导入
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.errorsiayusulif.zakocountdown.utils.AlarmScheduler
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.EventRepository
import com.errorsiayusulif.zakocountdown.widget.WidgetUpdateReceiver // <-- 【修复】导入
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

    private var currentEventId: Long? = null
    private var isNewEvent: Boolean = true
    private var originalEvent: CountdownEvent? = null

    // --- 【关键修复】补全这个缺失的方法 ---
    fun start(eventId: Long?) {
        currentEventId = eventId
        if (eventId == null) {
            isNewEvent = true
            // 为新事件设置一个空的标题初始值
            _eventTitle.value = ""
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

    // --- 【关键修复】补全这个缺失的方法 ---
    private fun onEventLoaded(event: CountdownEvent) {
        _eventTitle.value = event.title
        val calendar = Calendar.getInstance().apply { time = event.targetDate }
        _selectedDateTime.value = calendar
    }

    // --- 【关键修复】补全这个缺失的方法 ---
    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        val calendar = _selectedDateTime.value ?: Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        _selectedDateTime.value = calendar
    }

    // --- 【关键修复】补全这个缺失的方法 ---
    fun updateTime(hourOfDay: Int, minute: Int) {
        val calendar = _selectedDateTime.value ?: Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        calendar.set(Calendar.MINUTE, minute)
        _selectedDateTime.value = calendar
    }

    fun saveEvent(title: String): Boolean {
        if (title.isBlank()) return false
        val targetDate = _selectedDateTime.value?.time ?: Date()
        viewModelScope.launch {
            var eventToSave: CountdownEvent
            if (isNewEvent) {
                eventToSave = CountdownEvent(title = title, targetDate = targetDate)
                // insertAndGetId返回的是新生成的ID
                val newId = repository.insertAndGetId(eventToSave)
                eventToSave.id = newId
            } else {
                eventToSave = originalEvent!!.copy(title = title, targetDate = targetDate)
                repository.update(eventToSave)
            }
            // 使用 getApplication() 来获取 context
            AlarmScheduler.scheduleReminder(getApplication(), eventToSave)
            // 【优化】保存日程后，发送广播通知微件更新
            getApplication<Application>().sendBroadcast(
                Intent(getApplication(), WidgetUpdateReceiver::class.java).apply {
                    action = WidgetUpdateReceiver.ACTION_UPDATE_WIDGET // <-- 【修复】修正语法
                }
            )
        }
        return true
    }
}

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