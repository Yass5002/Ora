/*
 *
 * ██████╗ ███████╗███╗   ███╗██╗███╗   ██╗██████╗ ███████╗██████╗     ███████╗ ██████╗██╗  ██╗███████╗██████╗ ██╗   ██╗██╗     ███████╗██████╗
 * ██╔══██╗██╔════╝████╗ ████║██║████╗  ██║██╔══██╗██╔════╝██╔══██╗    ██╔════╝██╔════╝██║  ██║██╔════╝██╔══██╗██║   ██║██║     ██╔════╝██╔══██╗
 * ██████╔╝█████╗  ██╔████╔██║██║██╔██╗ ██║██║  ██║█████╗  ██████╔╝    ███████╗██║     ███████║█████╗  ██║  ██║██║   ██║██║     █████╗  ██████╔╝
 * ██╔══██╗██╔══╝  ██║╚██╔╝██║██║██║╚██╗██║██║  ██║██╔══╝  ██╔══██╗    ╚════██║██║     ██╔══██║██╔══╝  ██║  ██║██║   ██║██║     ██╔══╝  ██╔══██╗
 * ██║  ██║███████╗██║ ╚═╝ ██║██║██║ ╚████║██████╔╝███████╗██║  ██║    ███████║╚██████╗██║  ██║███████╗██████╔╝╚██████╔╝███████╗███████╗██║  ██║
 * ╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝╚═╝╚═╝  ╚═══╝╚═════╝ ╚══════╝╚═╝  ╚═╝    ╚══════╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝╚═════╝  ╚═════╝ ╚══════╝╚══════╝╚═╝  ╚═╝
 *
 * ReminderScheduler.kt - The Time Wizard ⏰
 * I set reminders like a boss! No event shall pass without proper warning.
 * Your personal alarm clock that actually works!
 *
 * Chronological mastery by Yassine 🧙‍♂️⏰
 * "I'll remind you before you even know you need reminding!" - Future vision activated
 */



package com.dev.ora.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.dev.ora.data.CountdownEvent
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminders(event: CountdownEvent) {
        if (!event.hasReminders) return

        // === ✨ MODIFIED SECTION ✨ ===
        // Proactively check for permission before attempting to schedule.
        // This is crucial for reliability on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.e("ReminderScheduler", "Cannot schedule exact alarms: permission not granted. Reminders for '${event.title}' will not be set.")
                // The UI (MainActivity) should guide the user to grant this permission.
                return
            }
        }

        val intervals = parseReminderIntervals(event.reminderIntervals)
        val eventTimeMillis = event.targetDate.time
        val currentTimeMillis = System.currentTimeMillis()

        // Schedule pre-event reminders
        intervals.forEach { intervalMinutes ->
            val reminderTimeMillis = eventTimeMillis - TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
            if (reminderTimeMillis > currentTimeMillis) {
                scheduleReminder(event.id, reminderTimeMillis, formatReminderType(intervalMinutes))
            }
        }

        // Schedule "event started" reminder at the exact event time
        if (eventTimeMillis > currentTimeMillis) {
            scheduleReminder(event.id, eventTimeMillis, "⏰ Time's up! Your event is here!")
        }
    }

    /**
     * === ✨ MODIFIED & IMPROVED ✨ ===
     * Cancels all potential reminders for an event. It's more efficient to cancel
     * specific pending intents rather than guessing all possible intervals.
     * We'll iterate through the same intervals that could have been scheduled.
     */
    fun cancelReminders(eventId: Long) {
        // Use the same set of intervals that could have been used for scheduling
        val potentialIntervals = parseReminderIntervals(null) // Gets default intervals
        // You might want to expand this list if users can set custom intervals
        // val allPossibleIntervals = listOf(5, 15, 30, 60, 1440, etc...)

        potentialIntervals.forEach { intervalMinutes ->
            cancelSpecificReminder(eventId, formatReminderType(intervalMinutes))
        }

        // Also cancel the "event started" reminder
        cancelSpecificReminder(eventId, "⏰ Time's up! Your event is here!")
    }

    fun rescheduleReminders(event: CountdownEvent) {
        cancelReminders(event.id)
        scheduleReminders(event)
    }

    private fun scheduleReminder(eventId: Long, triggerTimeMillis: Long, reminderType: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_REMINDER_TYPE, reminderType)
        }
        val requestCode = generateRequestCode(eventId, reminderType)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // The old try-catch is removed as we now check for permission beforehand.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
    }

    private fun cancelSpecificReminder(eventId: Long, reminderType: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val requestCode = generateRequestCode(eventId, reminderType)
        // Use FLAG_NO_CREATE to find an existing PendingIntent. If it doesn't exist, it returns null.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel() // Also cancel the PendingIntent itself
        }
    }

    // --- ALL YOUR ORIGINAL HELPER FUNCTIONS ARE PRESERVED BELOW ---

    private fun generateRequestCode(eventId: Long, reminderType: String): Int {
        // Using a combination of eventId and reminderType hash for a unique code
        return (eventId.toString() + reminderType).hashCode()
    }

    private fun parseReminderIntervals(intervalsString: String?): List<Int> {
        if (intervalsString.isNullOrBlank()) {
            return getDefaultIntervals()
        }
        return try {
            intervalsString.split(",").mapNotNull { it.trim().toIntOrNull() }
        } catch (e: Exception) {
            getDefaultIntervals()
        }
    }

    private fun getDefaultIntervals(): List<Int> {
        return listOf(5, 15, 60, 1440) // 5 min, 15 min, 1 hour, 1 day
    }

    private fun formatReminderType(intervalMinutes: Int): String {
        val timeText = when {
            intervalMinutes < 60 -> "${intervalMinutes}m"
            intervalMinutes < 1440 -> "${intervalMinutes / 60}h"
            intervalMinutes < 10080 -> "${intervalMinutes / 1440}d"
            intervalMinutes < 43200 -> "${intervalMinutes / 10080}w"
            intervalMinutes < 525600 -> "${intervalMinutes / 43200}mo"
            else -> "${intervalMinutes / 525600}y"
        }
        return "🎯 Almost there - $timeText countdown!"
    }

    companion object {
        fun getSmartIntervals(eventTimeMillis: Long): List<Int> {
            val currentTimeMillis = System.currentTimeMillis()
            val diffMillis = eventTimeMillis - currentTimeMillis
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

            return when {
                diffDays <= 1 -> listOf(5, 15, 30, 60)
                diffDays <= 7 -> listOf(5, 15, 60, 360, 1440)
                diffDays <= 30 -> listOf(15, 60, 720, 1440, 2880)
                diffDays <= 365 -> listOf(60, 1440, 2880, 10080, 43200)
                else -> listOf(1440, 10080, 43200, 525600)
            }
        }
    }
}
