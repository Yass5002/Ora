/*
 *
 * ███╗   ██╗ ██████╗ ████████╗██╗███████╗██╗ ██████╗ █████╗ ████████╗██╗ ██████╗ ███╗   ██╗
 * ████╗  ██║██╔═══██╗╚══██╔══╝██║██╔════╝██║██╔════╝██╔══██╗╚══██╔══╝██║██╔═══██╗████╗  ██║
 * ██╔██╗ ██║██║   ██║   ██║   ██║█████╗  ██║██║     ███████║   ██║   ██║██║   ██║██╔██╗ ██║
 * ██║╚██╗██║██║   ██║   ██║   ██║██╔══╝  ██║██║     ██╔══██║   ██║   ██║██║   ██║██║╚██╗██║
 * ██║ ╚████║╚██████╔╝   ██║   ██║██║     ██║╚██████╗██║  ██║   ██║   ██║╚██████╔╝██║ ╚████║
 * ╚═╝  ╚═══╝ ╚═════╝    ╚═╝   ╚═╝╚═╝     ╚═╝ ╚═════╝╚═╝  ╚═╝   ╚═╝   ╚═╝ ╚═════╝ ╚═╝  ╚═══╝
 *
 * NotificationHelper.kt - The Digital Messenger 📬
 * I'm your personal notification wizard! Making sure you never miss
 * those important moments with style and persistence!
 *
 * Notification sorcery crafted by Yassine 🧙‍♂️
 * "Ding! Your moment is here!" - The sound of time arriving
 */

package com.dev.ora.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dev.ora.MainActivity
import com.dev.ora.R
import com.dev.ora.data.CountdownEvent
import com.dev.ora.data.PreferencesManager
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Helper class for creating and managing notifications
 * Follows Android notification best practices
 */
object NotificationHelper {

    // Notification channels
    const val CHANNEL_ID_PERSISTENT = "countdown_persistent"
    const val CHANNEL_ID_REMINDERS = "countdown_reminders"

    // Notification IDs
    const val NOTIFICATION_ID_PERSISTENT = 1001
    const val NOTIFICATION_ID_REMINDER_BASE = 2000 // Reminder IDs = 2000 + event.id

    /**
     * Create notification channels (required for Android 8.0+)
     * Call this once when app starts
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Persistent countdown channel (Low priority - silent, always visible)
            val persistentChannel = NotificationChannel(
                CHANNEL_ID_PERSISTENT,
                "Countdown Timer",
                NotificationManager.IMPORTANCE_LOW // No sound, no vibration
            ).apply {
                description = "Shows countdown to your next event"
                setShowBadge(false) // Don't show badge on app icon
                enableVibration(false)
                setSound(null, null) // Silent
            }

            // Reminders channel (High priority - sound, vibration, pop-up)
            val remindersChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Event Reminders",
                NotificationManager.IMPORTANCE_HIGH // Sound, vibration, heads-up
            ).apply {
                description = "Alerts you before events start"
                setShowBadge(true)
                enableVibration(true)
                // Use system default notification sound
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    null
                )
            }

            notificationManager.createNotificationChannel(persistentChannel)
            notificationManager.createNotificationChannel(remindersChannel)
        }
    }

    /**
     * Create/Update the persistent countdown notification
     * This shows the countdown to the nearest or pinned event, or multiple events if enabled
     */
    fun createPersistentNotification(
        context: Context,
        event: CountdownEvent,
        allEvents: List<CountdownEvent> = emptyList()
    ): NotificationCompat.Builder {
        val preferencesManager = PreferencesManager(context)
        val showMultipleEvents = preferencesManager.getMultipleEventsNotification()
        val maxEvents = preferencesManager.getMaxEventsInNotification()

        // Intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (showMultipleEvents && allEvents.isNotEmpty()) {
            createMultipleEventsNotification(context, allEvents, maxEvents, pendingIntent)
        } else {
            createSingleEventNotification(context, event, pendingIntent)
        }
    }

    /**
     * Create notification showing a single event (default behavior)
     */
    private fun createSingleEventNotification(
        context: Context,
        event: CountdownEvent,
        pendingIntent: PendingIntent
    ): NotificationCompat.Builder {
        val countdownText = formatCountdown(event.targetDate)
        val eventTitle = if (event.isPinned) "📌 ${event.title}" else event.title

        return NotificationCompat.Builder(context, CHANNEL_ID_PERSISTENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(eventTitle)
            .setContentText(countdownText)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Can't be dismissed by user
            .setOnlyAlertOnce(true) // Don't alert on updates
            .setSilent(true) // No sound
            .setShowWhen(false) // Don't show time
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
    }

    /**
     * Create notification showing multiple events using InboxStyle
     */
    private fun createMultipleEventsNotification(
        context: Context,
        allEvents: List<CountdownEvent>,
        maxEvents: Int,
        pendingIntent: PendingIntent
    ): NotificationCompat.Builder {
        val now = System.currentTimeMillis()

        // Filter and sort events: upcoming events first, then by priority (pinned first), then by date
        val upcomingEvents = allEvents
            .filter { it.targetDate.time > now }
            .sortedWith(compareBy<CountdownEvent> { !it.isPinned }.thenBy { it.targetDate.time })
            .take(maxEvents)

        if (upcomingEvents.isEmpty()) {
            // Fallback to single event behavior if no upcoming events
            val fallbackEvent = allEvents.firstOrNull() ?: CountdownEvent(
                id = 0,
                title = "No upcoming events",
                description = "",
                targetDate = Date(System.currentTimeMillis() + 60000),
                color = "#DC143C",
                isPinned = false
            )
            return createSingleEventNotification(context, fallbackEvent, pendingIntent)
        }

        // Use the first event for the main title
        val primaryEvent = upcomingEvents.first()
        val primaryCountdown = formatCountdown(primaryEvent.targetDate)
        val primaryTitle = if (primaryEvent.isPinned) "📌 ${primaryEvent.title}" else primaryEvent.title

        // Create inbox style for multiple events
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Upcoming Events (${upcomingEvents.size})")

        // Add each event as a line
        upcomingEvents.forEach { event ->
            val eventCountdown = formatCountdown(event.targetDate)
            val eventTitle = if (event.isPinned) "📌 ${event.title}" else event.title
            inboxStyle.addLine("$eventTitle • $eventCountdown")
        }

        // Add summary if there are more events
        if (allEvents.filter { it.targetDate.time > now }.size > maxEvents) {
            val additionalCount = allEvents.filter { it.targetDate.time > now }.size - maxEvents
            inboxStyle.setSummaryText("+ $additionalCount more events")
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_PERSISTENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(primaryTitle)
            .setContentText(primaryCountdown)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Can't be dismissed by user
            .setOnlyAlertOnce(true) // Don't alert on updates
            .setSilent(true) // No sound
            .setShowWhen(false) // Don't show time
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
    }

    /**
     * Create a reminder notification (pop-up alert)
     * This alerts the user at specific times before an event
     */
    fun createReminderNotification(
        context: Context,
        event: CountdownEvent,
        reminderTime: String
    ): NotificationCompat.Builder {
        // Intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(), // Use hashCode() instead of toInt() to avoid overflow
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val eventTitle = if (event.isPinned) "📌 ${event.title}" else event.title
        val reminderText = reminderTime // Use the reminder message directly without adding extra text

        return NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(eventTitle)
            .setContentText(reminderText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Dismiss when tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 250, 250, 250)) // Vibration pattern
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Ensure all defaults including sound
            .setOnlyAlertOnce(false) // Allow sound on each reminder
    }

    /**
     * Show a reminder notification immediately
     */
    fun showReminderNotification(context: Context, event: CountdownEvent, reminderTime: String) {
        val notification = createReminderNotification(context, event, reminderTime).build()
        val notificationId = (NOTIFICATION_ID_REMINDER_BASE + event.id).toInt()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Update the persistent countdown notification with current event data
     */
    fun updateCountdownNotification(context: Context, event: CountdownEvent) {
        val pendingIntent = createMainActivityPendingIntent(context)
        val notification = createPersistentNotification(context, event).build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_PERSISTENT, notification)
    }

    /**
     * Show multiple events notification
     */
    fun showMultipleEventsNotification(context: Context, events: List<CountdownEvent>, maxEvents: Int) {
        val pendingIntent = createMainActivityPendingIntent(context)
        val notification = createMultipleEventsNotification(context, events, maxEvents, pendingIntent).build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_PERSISTENT, notification)
    }

    /**
     * Format countdown text based on time remaining
     * Examples: "2d 5h 30m", "1h 45m", "30m", "5m 30s", "30s"
     */
    fun formatCountdown(targetDate: Date): String {
        val now = System.currentTimeMillis()
        val target = targetDate.time
        val diff = target - now

        if (diff <= 0) {
            return "Event started!"
        }

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60

        return when {
            days > 0 -> {
                if (hours > 0) "${days}d ${hours}h" else "${days}d"
            }
            hours > 0 -> {
                if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            }
            minutes > 10 -> "${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Calculate update interval based on time remaining
     * Far events update less frequently to save battery
     */
    fun getUpdateIntervalMillis(targetDate: Date): Long {
        val now = System.currentTimeMillis()
        val diff = targetDate.time - now

        return when {
            diff <= 0 -> 0L // Event passed
            diff < TimeUnit.MINUTES.toMillis(10) -> TimeUnit.SECONDS.toMillis(1) // < 10 min: every second
            diff < TimeUnit.MINUTES.toMillis(30) -> TimeUnit.MINUTES.toMillis(1) // < 30 min: every minute
            diff < TimeUnit.HOURS.toMillis(1) -> TimeUnit.MINUTES.toMillis(5) // < 1 hour: every 5 min
            diff < TimeUnit.HOURS.toMillis(6) -> TimeUnit.MINUTES.toMillis(15) // < 6 hours: every 15 min
            diff < TimeUnit.DAYS.toMillis(1) -> TimeUnit.MINUTES.toMillis(30) // < 1 day: every 30 min
            diff < TimeUnit.DAYS.toMillis(7) -> TimeUnit.HOURS.toMillis(2) // < 1 week: every 2 hours
            diff < TimeUnit.DAYS.toMillis(30) -> TimeUnit.HOURS.toMillis(12) // < 1 month: every 12 hours
            else -> TimeUnit.DAYS.toMillis(1) // > 1 month: once a day
        }
    }

    /**
     * Check if we should show notification for this event
     * Don't show for events too far in the future (> 1 year)
     */
    fun shouldShowNotification(targetDate: Date): Boolean {
        val now = System.currentTimeMillis()
        val diff = targetDate.time - now
        val oneYear = TimeUnit.DAYS.toMillis(365)

        return diff > 0 && diff <= oneYear
    }

    /**
     * Create a pending intent to open the main activity
     */
    private fun createMainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
