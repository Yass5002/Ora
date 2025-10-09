package com.dev.ora.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dev.ora.data.CountdownDatabase
import com.dev.ora.notification.CountdownNotificationService
import com.dev.ora.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that handles reminder notifications for events.
 * It also triggers an update for the persistent notification service.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val reminderType = intent.getStringExtra(EXTRA_REMINDER_TYPE) ?: return

        if (eventId == -1L) return

        /**
         * === ✨ THE FINAL FIX ✨ ===
         * At the exact moment a reminder is triggered, we also tell the
         * persistent notification service to wake up and update its own schedule.
         * This ensures the real-time countdown starts immediately when a critical
         * reminder (like the 5-minute one) fires, even if the app is closed.
         */
        CountdownNotificationService.startOrUpdateService(context)

        // Use goAsync to allow for background work (database access).
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = CountdownDatabase.getDatabase(context)
                val event = database.countdownEventDao().getEventById(eventId)

                // We still need the event to show the correct title in the reminder.
                if (event != null && event.hasReminders) {
                    // This shows the actual pop-up reminder notification.
                    NotificationHelper.showReminderNotification(context, event, reminderType)
                }
            } finally {
                // Important: finish the async operation.
                pendingResult.finish()
            }
        }
    }

    // Your original showReminderNotification was creating a fake event object.
    // It's cleaner and more accurate to just pass the real event object to the NotificationHelper.
    // I've removed that helper function and will call NotificationHelper.showReminderNotification directly
    // with the real event object we fetched from the database.

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_REMINDER_TYPE = "reminder_type"
    }
}