/*
 *
 * ██████╗ ███████╗██████╗  ██████╗ ███████╗██╗████████╗ ██████╗ ██████╗ ██╗   ██╗
 * ██╔══██╗██╔════╝██╔══██╗██╔═══██╗██╔════╝██║╚══██╔══╝██╔═══██╗██╔══██╗╚██╗ ██╔╝
 * ██████╔╝█████╗  ██████╔╝██║   ██║███████╗██║   ██║   ██║   ██║██████╔╝ ╚████╔╝
 * ██╔══██╗██╔══╝  ██╔═══╝ ██║   ██║╚════██║██║   ██║   ██║   ██║██╔══██╗  ╚██╔╝
 * ██║  ██║███████╗██║     ╚██████╔╝███████║██║   ██║   ╚██████╔╝██║  ██║   ██║
 * ╚═╝  ╚═╝╚══════╝╚═╝      ╚═════╝ ╚══════╝╚═╝   ╚═╝    ╚═════╝ ╚═╝  ╚═╝   ╚═╝
 *
 * CountdownRepository.kt - The Data Traffic Controller 🎛️
 * I'm the middleman between your UI and database, making sure
 * everything flows smoothly like a well-oiled machine!
 *
 * Repository pattern mastery by Yassine 🚀
 * "Don't call the database, let me handle it!" - Clean Architecture 101
 */

package com.dev.ora.repository

import com.dev.ora.data.CountdownEvent
import com.dev.ora.data.CountdownEventDao
import kotlinx.coroutines.flow.Flow

class CountdownRepository(private val dao: CountdownEventDao) {

    fun getAllEventsWithExpiredAtBottom(): Flow<List<CountdownEvent>> =
        dao.getAllEventsWithExpiredAtBottom()

    fun getAllActiveEvents(): Flow<List<CountdownEvent>> = dao.getAllActiveEvents()

    fun getAllEvents(): Flow<List<CountdownEvent>> = dao.getAllEvents()

    suspend fun getEventById(id: Long): CountdownEvent? = dao.getEventById(id)

    suspend fun insertEvent(event: CountdownEvent): Long = dao.insertEvent(event)

    suspend fun updateEvent(event: CountdownEvent) = dao.updateEvent(event)

    suspend fun deleteEvent(event: CountdownEvent) = dao.deleteEvent(event)

    suspend fun deactivateEvent(id: Long) = dao.deactivateEvent(id)

    suspend fun getUpcomingEvents(): List<CountdownEvent> =
        dao.getUpcomingEvents()

    suspend fun getExpiredEvents(): List<CountdownEvent> =
        dao.getExpiredEvents()

    suspend fun markExpiredEventsAsEnded(): Int =
        dao.markExpiredEventsAsEnded()

    fun getAllEndedEvents(): Flow<List<CountdownEvent>> = dao.getAllEndedEvents()

    suspend fun updatePinStatus(id: Long, isPinned: Boolean) = dao.updatePinStatus(id, isPinned)

    suspend fun getPinnedCount(): Int = dao.getPinnedCount()

    suspend fun getPinnedEvents(): List<CountdownEvent> = dao.getPinnedEvents()
}
