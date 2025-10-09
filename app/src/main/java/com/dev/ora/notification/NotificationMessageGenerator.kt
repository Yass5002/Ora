package com.dev.ora.notification

import com.dev.ora.data.CountdownEvent

/**
 * Generates smart, contextual notification messages for events
 */
object NotificationMessageGenerator {

    /**
     * Generate a fun message for when the event time arrives
     */
    fun getEventStartedMessage(event: CountdownEvent): String {
        return "⏰ Time's up! Your event is here!"
    }

    /**
     * Generate a fun message for pre-event reminders
     */
    fun getPreEventMessage(event: CountdownEvent, reminderType: String): String {
        // Extract time from reminder type (e.g. "5m before" -> "5m")
        val timeText = when {
            reminderType.contains("5m") -> "5m"
            reminderType.contains("15m") -> "15m"
            reminderType.contains("30m") -> "30m"
            reminderType.contains("1h") -> "1h"
            reminderType.contains("1d") -> "1d"
            reminderType.contains("before") -> reminderType.replace(" before", "")
            else -> reminderType
        }

        return "🎯 Almost there - $timeText countdown!"
    }
}
