package com.dev.ora.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.dev.ora.data.CountdownDatabase
import com.dev.ora.data.CountdownEvent
import com.dev.ora.repository.CountdownRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CountdownRepository

    init {
        val dao = CountdownDatabase.getDatabase(application).countdownEventDao()
        repository = CountdownRepository(dao)
    }

    private val timerFlow: Flow<Long> = flow {
        delay(200)
        emit(System.currentTimeMillis())
        while (true) {
            delay(1000)
            emit(System.currentTimeMillis())
        }
    }

    val allEvents: Flow<List<CountdownEvent>> = timerFlow.flatMapLatest {
        repository.getAllEventsWithExpiredAtBottom()
    }.map { it.toList() }

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _expiredEventsCount = MutableLiveData<Int?>()
    val expiredEventsCount: LiveData<Int?> = _expiredEventsCount

    private val _pinActionResult = MutableLiveData<String?>()
    val pinActionResult: LiveData<String?> = _pinActionResult

    /**
     * === ✨ NEW & IMPROVED FUNCTION ✨ ===
     * This is a suspend function that handles the database operation and returns the
     * complete, newly created event. It has NO delays and NO Android dependencies.
     * This is the key to removing the race condition in MainActivity.
     */
    suspend fun addEventAndReturn(title: String, description: String, targetDate: Date, color: String, hasReminders: Boolean = true): CountdownEvent {
        val newEvent = CountdownEvent(
            title = title.trim(),
            description = description.trim(),
            targetDate = targetDate,
            color = color,
            hasReminders = hasReminders
            // DAO will handle default values for createdAt, isActive, etc.
        )
        val eventId = repository.insertEvent(newEvent)
        // Return the full event object, now complete with its database-generated ID
        return newEvent.copy(id = eventId)
    }

    /**
     * === ✨ MODIFIED ✨ ===
     * The ViewModel now only worries about deleting the event from the repository.
     * The Activity will handle canceling reminders and updating the service.
     */
    fun deleteEvent(event: CountdownEvent) {
        viewModelScope.launch {
            try {
                repository.deleteEvent(event)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete event: ${e.message}"
            }
        }
    }

    /**
     * === ✨ MODIFIED ✨ ===
     * The ViewModel now only worries about updating the event in the repository.
     * The Activity will handle rescheduling and updating the service.
     */
    fun updateEvent(event: CountdownEvent) {
        viewModelScope.launch {
            try {
                repository.updateEvent(event)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update event: ${e.message}"
            }
        }
    }

    // Your other functions are perfect and remain unchanged.

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun checkAndMarkExpiredEvents() {
        viewModelScope.launch {
            try {
                val expiredCount = repository.markExpiredEventsAsEnded()
                if (expiredCount > 0) {
                    _expiredEventsCount.value = expiredCount
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update expired events: ${e.message}"
            }
        }
    }

    fun clearExpiredEventsCount() {
        _expiredEventsCount.value = null
    }

    fun togglePinStatus(event: CountdownEvent) {
        viewModelScope.launch {
            try {
                val newPinStatus = !event.isPinned
                if (newPinStatus) {
                    val currentPinnedCount = repository.getPinnedCount()
                    if (currentPinnedCount >= 3) {
                        _pinActionResult.value = "Maximum 3 events can be pinned"
                        return@launch
                    }
                    _pinActionResult.value = "Event pinned successfully"
                } else {
                    _pinActionResult.value = "Event unpinned successfully"
                }
                repository.updatePinStatus(event.id, newPinStatus)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update pin status: ${e.message}"
            }
        }
    }

    fun clearPinActionResult() {
        _pinActionResult.value = null
    }

    suspend fun getExpiredEventsList(): List<CountdownEvent> {
        return repository.getExpiredEvents()
    }

    suspend fun getAllEventsList(): List<CountdownEvent> {
        return repository.getAllEvents().first()
    }
}