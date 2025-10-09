/*
 * ▄· ▄▌ ▄▄▄· .▄▄ · .▄▄ · ▪   ▐ ▄ ▄▄▄ .
 * ▐█▪██▌▐█ ▀█ ▐█ ▀. ▐█ ▀. ██ •█▌▐█▀▄.▀·
 * ▐█▌▐█▪▄█▀▀█ ▄▀▀▀█▄▄▀▀▀█▄▐█·▐█▐▐▌▐▀▀▪▄
 *  ▐█▀·.▐█ ▪▐▌▐█▄▪▐█▐█▄▪▐█▐█▌██▐█▌▐█▄▄▌
 *   ▀ •  ▀  ▀  ▀▀▀▀  ▀▀▀▀ ▀▀▀▀▀ █▪ ▀▀▀
 *
 * BootReceiver.kt - The Phoenix of Notifications 🔥
 * When your phone dies and comes back to life, I make sure
 * your countdowns don't get lost in the digital afterlife!
 *
 * Crafted with ❤️ by Yassine
 * "Every reboot is a new beginning" - Ancient Android Proverb
 */

package com.dev.ora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.dev.ora.data.CountdownDatabase
import com.dev.ora.data.PreferencesManager
import com.dev.ora.notification.CountdownNotificationService
import com.dev.ora.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * BroadcastReceiver that handles device boot completion.
 * Restores all scheduled reminders and notifications after reboot.
 */
class BootReceiver : BroadcastReceiver() {

    private val TAG = "OraBootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        // === ✨ MODIFIED SECTION ✨ ===
        // The logic for ACTION_BOOT_COMPLETED and ACTION_USER_UNLOCKED is now clearer and more robust.

        when (action) {
            // These actions occur while the device is still locked. We can't access the database yet.
            // Our only job is to ensure the foreground service starts if it was running before.
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d(TAG, "Device has booted (locked state).")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Use device-protected storage to check if notifications were enabled.
                        val dpc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            context.createDeviceProtectedStorageContext()
                        } else {
                            context
                        }
                        val prefs = PreferencesManager(dpc)
                        if (prefs.getNotificationsEnabled()) {
                            Log.i(TAG, "Notifications are enabled. Starting service in foreground.")
                            // This will show the "Loading..." notification until the user unlocks.
                            CountdownNotificationService.startOrUpdateService(context)
                        } else {
                            Log.i(TAG, "Notifications are disabled. No action taken at boot.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during locked boot operations.", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            // This action occurs after the user unlocks their phone for the first time.
            // Now we can safely access the database and reschedule everything.
            Intent.ACTION_USER_UNLOCKED -> {
                Log.d(TAG, "User has unlocked the device.")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // The double-call and long delay are removed. We will make ONE robust attempt.
                        restoreNotificationSystem(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during notification system restoration.", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            // This handles app updates. It's safe to restore everything immediately.
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Application package was replaced (updated).")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Since the user is likely unlocked, we can run the full restore.
                        restoreNotificationSystem(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during post-update restoration.", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    /**
     * === ✨ MODIFIED SECTION ✨ ===
     * This function is now cleaner, more robust, and has better logging.
     * The unnecessary delays are gone, replaced with a single, more reliable flow.
     */
    private suspend fun restoreNotificationSystem(context: Context) {
        val preferencesManager = PreferencesManager(context)

        // First, check if notifications are supposed to be active.
        if (!preferencesManager.getNotificationsEnabled()) {
            Log.i(TAG, "Notifications are disabled in preferences. Aborting restore.")
            return
        }

        try {
            // A very short delay can sometimes help prevent race conditions if the system is busy.
            kotlinx.coroutines.delay(500)

            Log.d(TAG, "Restoration starting: Fetching all events from the database...")
            val database = CountdownDatabase.getDatabase(context)
            val allEvents = database.countdownEventDao().getAllEvents().first()
            Log.d(TAG, "Found ${allEvents.size} events.")

            val reminderScheduler = ReminderScheduler(context)
            Log.d(TAG, "Rescheduling reminders for all applicable events...")
            allEvents.forEach { event ->
                if (event.hasReminders) {
                    reminderScheduler.rescheduleReminders(event)
                }
            }
            Log.d(TAG, "All reminders have been rescheduled.")

            // Finally, command the service to start and update its view with the fresh data.
            Log.d(TAG, "Starting/updating the persistent notification service.")
            CountdownNotificationService.startOrUpdateService(context)

            Log.i(TAG, "Notification system restore complete.")
        } catch (e: Exception) {
            Log.e(TAG, "A critical error occurred during restoreNotificationSystem.", e)
            // If DB access fails, we should not start the service as it might show stale data.
            // The error log is crucial for debugging this.
        }
    }
}