// file: app/src/main/java/com/errorsiayusulif/zakocountdown/ui/agenda/AgendaViewModel.kt
package com.errorsiayusulif.zakocountdown.ui.agenda

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.errorsiayusulif.zakocountdown.ZakoCountdownApplication
import com.errorsiayusulif.zakocountdown.data.AgendaBook
import com.errorsiayusulif.zakocountdown.data.CountdownEvent
import com.errorsiayusulif.zakocountdown.data.EventRepository
import kotlinx.coroutines.launch

class AgendaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EventRepository = (application as ZakoCountdownApplication).repository

    val allBooks: LiveData<List<AgendaBook>> = repository.allBooks

    // 同时也暴露所有日程，供选择器和统计使用
    val allEvents: LiveData<List<CountdownEvent>> = repository.allEvents

    private val _currentFilterId = MutableLiveData<Long>(-1L)
    val currentFilterId: LiveData<Long> = _currentFilterId

    fun setFilter(id: Long) {
        _currentFilterId.value = id
    }

    // --- 核心逻辑：保存日程本并处理日程归属 ---
    fun saveBookWithEvents(
        id: Long,
        name: String,
        colorHex: String,
        coverUri: String?,
        cardAlpha: Float,
        checkedEventIds: List<Long>
    ) = viewModelScope.launch {

        val bookId: Long

        if (id == -1L) {
            // 1. 新建
            // 获取当前最大的 sortOrder，放在最后
            val currentBooks = repository.getAllBooksSuspend()
            val maxOrder = currentBooks.maxOfOrNull { it.sortOrder } ?: 0

            val newBook = AgendaBook(
                name = name,
                colorHex = colorHex,
                coverImageUri = coverUri,
                cardAlpha = cardAlpha,
                sortOrder = maxOrder + 1
            )
            bookId = repository.insertBookAndGetId(newBook)
        } else {
            // 2. 更新
            bookId = id
            val book = repository.getBookById(id) ?: return@launch
            repository.updateBook(book.copy(
                name = name,
                colorHex = colorHex,
                coverImageUri = coverUri,
                cardAlpha = cardAlpha
            ))
        }

        // 3. 处理日程归属
        // 策略：
        // A. 找出目前属于该 bookId 的所有日程
        // B. 找出 checkedEventIds 中没有包含的那些，将其设为默认 (移出)
        // C. 将 checkedEventIds 中的所有日程设为该 bookId (移入/保持)

        // 简化版策略：先全部移出，再重新加入 (性能稍低但逻辑绝对安全)
        repository.detachEventsFromBook(bookId)

        if (checkedEventIds.isNotEmpty()) {
            repository.updateEventsBookId(bookId, checkedEventIds)
        }
    }

    // --- 简单创建 (用于 Dialog 快速新建) ---
    fun createBook(name: String, colorHex: String, coverImageUri: String? = null) = viewModelScope.launch {
        val currentBooks = repository.getAllBooksSuspend()
        val maxOrder = currentBooks.maxOfOrNull { it.sortOrder } ?: 0

        repository.insertBook(
            AgendaBook(
                name = name,
                colorHex = colorHex,
                coverImageUri = coverImageUri,
                sortOrder = maxOrder + 1
            )
        )
    }

    fun updateBook(book: AgendaBook) = viewModelScope.launch {
        repository.updateBook(book)
    }

    // --- 排序更新 ---
    fun updateBookOrders(books: List<AgendaBook>) = viewModelScope.launch {
        // 重新分配 sortOrder
        val updatedBooks = books.mapIndexed { index, book ->
            book.copy(sortOrder = index)
        }
        repository.updateBooks(updatedBooks)
    }

    fun deleteBook(book: AgendaBook) = viewModelScope.launch {
        if (_currentFilterId.value == book.id) {
            _currentFilterId.value = -1L
        }
        repository.deleteBook(book)
    }
}