package com.dev.ora.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownEventDao {

    @Query("SELECT * FROM countdown_events ORDER BY " +
           "isPinned DESC, " +
           "CASE WHEN targetDate <= strftime('%s', 'now') * 1000 THEN 1 ELSE 0 END, " +
           "targetDate ASC")
    fun getAllEventsWithExpiredAtBottom(): Flow<List<CountdownEvent>>

    @Query("SELECT * FROM countdown_events WHERE isActive = 1 ORDER BY targetDate ASC")
    fun getAllActiveEvents(): Flow<List<CountdownEvent>>

    @Query("SELECT * FROM countdown_events ORDER BY createdAt DESC")
    fun getAllEvents(): Flow<List<CountdownEvent>>

    @Query("SELECT * FROM countdown_events WHERE id = :id")
    suspend fun getEventById(id: Long): CountdownEvent?

    @Insert
    suspend fun insertEvent(event: CountdownEvent): Long

    @Update
    suspend fun updateEvent(event: CountdownEvent)

    @Delete
    suspend fun deleteEvent(event: CountdownEvent)

    @Query("UPDATE countdown_events SET isActive = 0 WHERE id = :id")
    suspend fun deactivateEvent(id: Long)

    @Query("SELECT * FROM countdown_events WHERE targetDate > strftime('%s', 'now') * 1000 AND isActive = 1")
    suspend fun getUpcomingEvents(): List<CountdownEvent>

    @Query("SELECT * FROM countdown_events WHERE targetDate <= strftime('%s', 'now') * 1000 AND isActive = 1")
    suspend fun getExpiredEvents(): List<CountdownEvent>

    @Query("UPDATE countdown_events SET isActive = 0 WHERE targetDate <= strftime('%s', 'now') * 1000 AND isActive = 1")
    suspend fun markExpiredEventsAsEnded(): Int

    @Query("SELECT * FROM countdown_events WHERE isActive = 0 ORDER BY targetDate DESC")
    fun getAllEndedEvents(): Flow<List<CountdownEvent>>

    @Query("UPDATE countdown_events SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: Long, isPinned: Boolean)

    @Query("SELECT COUNT(*) FROM countdown_events WHERE isPinned = 1")
    suspend fun getPinnedCount(): Int

    @Query("SELECT * FROM countdown_events WHERE isPinned = 1 ORDER BY targetDate ASC")
    suspend fun getPinnedEvents(): List<CountdownEvent>
}
