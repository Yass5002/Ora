/*
 *
 * ██████╗ ██████╗ ██╗   ██╗███╗   ██╗████████╗██████╗  ██████╗ ██╗    ██╗███╗   ██╗    ██╗   ██╗████████╗██╗██╗     ███████╗
 * ██╔════╝██╔═══██╗██║   ██║████╗  ██║╚══██╔══╝██╔══██╗██╔═══██╗██║    ██║████╗  ██║    ██║   ██║╚══██╔══╝██║██║     ██╔════╝
 * ██║     ██║   ██║██║   ██║██╔██╗ ██║   ██║   ██║  ██║██║   ██║██║ █╗ ██║██╔██╗ ██║    ██║   ██║   ██║   ██║██║     ███████╗
 * ██║     ██║   ██║██║   ██║██║╚██╗██║   ██║   ██║  ██║██║   ██║██║███╗██║██║╚██╗██║    ██║   ██║   ██║   ██║██║     ╚════██║
 * ╚██████╗╚██████╔╝╚██████╔╝██║ ╚████║   ██║   ██████╔╝╚██████╔╝╚███╔███╔╝██║ ╚████║    ╚██████╔╝   ██║   ██║███████╗███████║
 *  ╚═════╝ ╚═════╝  ╚═════╝ ╚═╝  ╚═══╝   ╚═╝   ╚═════╝  ╚═════╝  ╚══╝╚══╝ ╚═╝  ╚═══╝     ╚═════╝    ╚═╝   ╚═╝╚══════╝╚══════╝
 *
 * CountdownUtils.kt - The Mathematical Genius 🧮
 * I calculate time like Einstein calculated relativity!
 * Turning milliseconds into meaningful moments since day one.
 *
 * Time calculation wizardry by Yassine 🕰️
 * "Time is an illusion, but my calculations are real!" - Temporal mathematician
 */

package com.dev.ora.utils

import com.dev.ora.data.CountdownTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object CountdownUtils {

    fun calculateTimeRemaining(targetDate: Date): CountdownTime {
        val currentTime = System.currentTimeMillis()
        val targetTime = targetDate.time
        val difference = targetTime - currentTime

        if (difference <= 0) {
            return CountdownTime(0, 0, 0, 0, isExpired = true)
        }

        val days = TimeUnit.MILLISECONDS.toDays(difference)
        val hours = TimeUnit.MILLISECONDS.toHours(difference) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(difference) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(difference) % 60

        return CountdownTime(days, hours, minutes, seconds, isExpired = false)
    }

    fun formatCountdownDisplay(countdownTime: CountdownTime): String {
        // ┌─────────────────────────────┐
        // │ ⏰ Time Magic by Yassine ⏰ │
        // └─────────────────────────────┘
        return if (countdownTime.isExpired) {
            "Event has ended!"
        } else {
            "${countdownTime.days}d ${countdownTime.hours}h ${countdownTime.minutes}m ${countdownTime.seconds}s"
        }
    }

    fun getTimeUntilDisplay(countdownTime: CountdownTime): String {
        return when {
            countdownTime.isExpired -> "Ended"
            countdownTime.days > 0 -> "${countdownTime.days} days"
            countdownTime.hours > 0 -> "${countdownTime.hours} hours"
            countdownTime.minutes > 0 -> "${countdownTime.minutes} minutes"
            else -> "${countdownTime.seconds} seconds"
        }
    }

    /**
     * Generates a default event name based on the target date
     * Format: "Event - MMM dd, yyyy" or "Event - MMM dd, yyyy h:mm a" if time is significant
     */
    fun generateDefaultEventName(targetDate: Date, existingNames: List<String> = emptyList()): String {
        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val dateStr = dateFormat.format(targetDate)
        val timeStr = timeFormat.format(targetDate)

        // Check if time is significant (not midnight or noon exactly)
        val calendar = java.util.Calendar.getInstance()
        calendar.time = targetDate
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)

        // Base name with date
        val baseName = "Event - $dateStr"

        // Check if this exact name already exists
        var finalName = baseName
        var counter = 2

        // If base name exists, try with time first
        if (existingNames.contains(baseName)) {
            // Try adding time if it's not midnight (00:00)
            if (hour != 0 || minute != 0) {
                finalName = "$baseName $timeStr"
            }
        }

        // If name still exists, add counter
        while (existingNames.contains(finalName)) {
            finalName = "$baseName ($counter)"
            counter++
        }

        return finalName
    }
}
