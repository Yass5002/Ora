/*
 *     _    ,
 *   ' )  /
 *    /  / __.  _   _   o ____  _
 *   (__/_(_/|_/_)_/_)_<_/ / <_</_
 *    //
 *   (/
 *
 * CountdownNotificationService.kt - The Persistent Guardian 🛡️
 * I never sleep, I never stop, I keep your countdowns alive
 * even when you forget about them! 24/7 dedication at its finest.
 *
 * Engineered with precision by Yassine
 * "I am the notification that knocks!" - Breaking Bad reference? 😄
 */

package com.dev.ora.notification

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import com.dev.ora.data.CountdownDatabase
import com.dev.ora.data.CountdownEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * Foreground service that shows persistent countdown notification
 * Updates dynamically based on time remaining to event
 */
class CountdownNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var currentEvent: CountdownEvent? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        startForegroundWithInitialNotification()
        startEventObserver()
    }

    /**
     * === ✨ MODIFIED SECTION ✨ ===
     * Handles incoming commands (Intents) sent to the service.
     * Instead of just starting, we now look for a specific action.
     * This allows MainActivity to send an "update" command without restarting the whole service.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OR_UPDATE -> {
                // This is our main entry point. It ensures the service is running and
                // triggers an immediate data fetch and notification update.
                ensureNotificationIsActive()
            }
        }
        // Service will be restarted if killed by system, and the system will try to redeliver the last intent.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        updateRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun startForegroundWithInitialNotification() {
        val placeholderNotification = NotificationHelper.createPersistentNotification(
            this,
            CountdownEvent(
                id = 0,
                title = "Loading events...",
                description = "",
                targetDate = Date(System.currentTimeMillis() + 60000),
                color = "#DC143C",
                isPinned = false
            )
        ).build()
        startForeground(NotificationHelper.NOTIFICATION_ID_PERSISTENT, placeholderNotification)
    }

    private fun startEventObserver() {
        serviceScope.launch {
            val database = CountdownDatabase.getDatabase(applicationContext)
            val eventDao = database.countdownEventDao()

            // This reactive stream is the heart of the service. It automatically
            // re-evaluates the notification whenever the database changes.
            eventDao.getAllEvents().collect { events: List<CountdownEvent> ->
                val eventToShow = selectEventToShow(events)
                if (eventToShow != null && NotificationHelper.shouldShowNotification(eventToShow.targetDate)) {
                    currentEvent = eventToShow
                    // An immediate update is needed here to reflect the DB change instantly
                    updateNotificationImmediately(events)
                    // Then schedule the next timed update (for the countdown text)
                    scheduleNextUpdate(events)
                } else {
                    stopSelf()
                }
            }
        }
    }

    private fun selectEventToShow(events: List<CountdownEvent>): CountdownEvent? {
        val now = System.currentTimeMillis()
        val upcomingEvents = events.filter { it.targetDate.time > now }
        if (upcomingEvents.isEmpty()) return null

        val pinnedEvents = upcomingEvents.filter { it.isPinned }
        return pinnedEvents.minByOrNull { it.targetDate.time }
            ?: upcomingEvents.minByOrNull { it.targetDate.time }
    }

    private fun scheduleNextUpdate(events: List<CountdownEvent>) {
        updateRunnable?.let { handler.removeCallbacks(it) }

        val eventToSchedule = currentEvent ?: return
        val currentTimeMillis = System.currentTimeMillis()
        val timeRemaining = eventToSchedule.targetDate.time - currentTimeMillis

        // Your existing logic for real-time updates is preserved perfectly.
        val nextUpdateDelay = when {
            timeRemaining <= 0 -> 1000L // If expired, check again in 1s to switch events
            timeRemaining <= 5 * 60 * 1000 -> 1000L // Real-time updates in the last 5 minutes
            timeRemaining <= 60 * 60 * 1000 -> 60000L // Update every minute in the last hour
            else -> 3600000L // Update every hour otherwise
        }

        updateRunnable = Runnable {
            updateNotificationImmediately(events) // Update content
            scheduleNextUpdate(events) // Schedule the *next* update
        }
        handler.postDelayed(updateRunnable!!, nextUpdateDelay)
    }

    /**
     * Failsafe method to ensure notification is active and showing correct data.
     * Called when the service receives an update command.
     */
    private fun ensureNotificationIsActive() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val database = CountdownDatabase.getDatabase(applicationContext)
                val events = database.countdownEventDao().getAllEvents().first()
                withContext(Dispatchers.Main) {
                    updateNotificationImmediately(events)
                    scheduleNextUpdate(events)
                }
            } catch (e: Exception) {
                // If DB access fails, the service will keep its placeholder or last known state.
            }
        }
    }

    private fun updateNotificationImmediately(events: List<CountdownEvent>) {
        val eventToShow = selectEventToShow(events)
        if (eventToShow == null) {
            stopSelf()
            return
        }
        currentEvent = eventToShow

        val settings = com.dev.ora.data.PreferencesManager(this)
        val notificationBuilder = if (settings.getMultipleEventsNotification()) {
            // Pass all events for the multi-event style notification
            NotificationHelper.createPersistentNotification(this, eventToShow, events)
        } else {
            // Pass only the single focused event
            NotificationHelper.createPersistentNotification(this, eventToShow)
        }
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(NotificationHelper.NOTIFICATION_ID_PERSISTENT, notificationBuilder.build())
    }


    /**
     * === ✨ NEW/MODIFIED SECTION ✨ ===
     * The public interface for controlling the service.
     * We now have a specific method to request an update.
     */
    companion object {
        private const val ACTION_START_OR_UPDATE = "com.dev.ora.action.START_OR_UPDATE"

        /**
         * Starts the service if not running, or sends an update command if it is.
         * This is the ONLY method that should be called from outside to refresh the service.
         */
        fun startOrUpdateService(context: android.content.Context) {
            val intent = Intent(context, CountdownNotificationService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the countdown notification service.
         * (This function remains the same and is used when notifications are disabled).
         */
        fun stopService(context: android.content.Context) {
            val intent = Intent(context, CountdownNotificationService::class.java)
            context.stopService(intent)
        }
    }
}