/*
 *
 * ██████╗ ██████╗ ███████╗███████╗███████╗██████╗ ███████╗███╗   ██╗ ██████╗███████╗███████╗
 * ██╔══██╗██╔══██╗██╔════╝██╔════╝██╔════╝██╔══██╗██╔════╝████╗  ██║██╔════╝██╔════╝██╔════╝
 * ██████╔╝██████╔╝█████╗  █████╗  █████╗  ██████╔╝█████╗  ██╔██╗ ██║██║     █████╗  ███████╗
 * ██╔═══╝ ██╔══██╗██╔══╝  ██╔══╝  ██╔══╝  ██╔══██╗██╔══╝  ██║╚██╗██║██║     ██╔══╝  ╚════██║
 * ██║     ██║  ██║███████╗██║     ███████╗██║  ██║███████╗██║ ╚████║╚██████╗███████╗███████║
 * ╚═╝     ╚═╝  ╚═╝╚══════╝╚═╝     ╚══════╝╚═╝  ╚═╝╚══════╝╚═╝  ╚═══╝ ╚═════╝╚══════╝╚══════╝
 *
 * PreferencesManager.kt - The Memory Keeper 🧠
 * I remember everything you like and dislike! Your personal digital butler
 * that never forgets your preferences and settings.
 *
 * Persistent memory magic by Yassine 💾
 * "I never forget, unlike your goldfish!" - The eternal storage guardian
 */

package com.dev.ora.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

class PreferencesManager(context: Context) {

    private val appContext: Context = context.applicationContext

    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val TAG = "PreferencesManager"

    private fun getDeviceProtectedPreferences(): SharedPreferences? {
        return try {
            val dpc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appContext.createDeviceProtectedStorageContext()
            } else {
                appContext
            }
            dpc.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get device-protected prefs: ${e.message}")
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_THEME = "theme_preference"
        private const val KEY_AUTO_DELETE_EXPIRED = "auto_delete_expired"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_ASKED_FOR_NOTIFICATIONS = "asked_for_notifications"
        private const val KEY_MULTIPLE_EVENTS_NOTIFICATION = "multiple_events_notification"
        private const val KEY_MAX_EVENTS_IN_NOTIFICATION = "max_events_in_notification"
        private const val KEY_FIRST_LAUNCH = "first_launch"

        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
        const val THEME_SYSTEM = "SYSTEM"
    }

    /**
     * Save the theme preference
     * @param theme One of: THEME_LIGHT, THEME_DARK, or THEME_SYSTEM
     */
    fun saveThemePreference(theme: String) {
        preferences.edit().putString(KEY_THEME, theme).apply()
    }

    /**
     * Get the current theme preference
     * @return One of: THEME_LIGHT, THEME_DARK, or THEME_SYSTEM (default)
     */
    fun getThemePreference(): String {
        return preferences.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    /**
     * Save auto-delete expired events preference
     */
    fun saveAutoDeleteExpired(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_DELETE_EXPIRED, enabled).apply()
    }

    /**
     * Get auto-delete expired events preference
     */
    fun getAutoDeleteExpired(): Boolean {
        return preferences.getBoolean(KEY_AUTO_DELETE_EXPIRED, false)
    }

    /**
     * Save notifications enabled preference
     */
    fun saveNotificationsEnabled(enabled: Boolean) {
        // Save in normal prefs
        preferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        // Also mirror to device-protected prefs so boot receivers can read this flag before user unlock
        getDeviceProtectedPreferences()?.edit()?.putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)?.apply()
    }

    /**
     * Get notifications enabled preference
     */
    fun getNotificationsEnabled(): Boolean {
        // Prefer device-protected value when available (so BootReceiver sees the same state at direct boot)
        val d = getDeviceProtectedPreferences()
        return d?.getBoolean(KEY_NOTIFICATIONS_ENABLED, preferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false))
            ?: preferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
    }

    /**
     * Save that we've asked for notification permission
     */
    fun setAskedForNotifications(asked: Boolean) {
        preferences.edit().putBoolean(KEY_ASKED_FOR_NOTIFICATIONS, asked).apply()
    }

    /**
     * Check if we've already asked for notification permission
     */
    fun hasAskedForNotifications(): Boolean {
        return preferences.getBoolean(KEY_ASKED_FOR_NOTIFICATIONS, false)
    }

    /**
     * Save the preference for multiple events notification
     * @param enabled True if enabled, false otherwise
     */
    fun saveMultipleEventsNotification(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_MULTIPLE_EVENTS_NOTIFICATION, enabled).apply()
    }

    /**
     * Get the preference for multiple events notification
     * @return True if enabled, false otherwise
     */
    fun getMultipleEventsNotification(): Boolean {
        return preferences.getBoolean(KEY_MULTIPLE_EVENTS_NOTIFICATION, false)
    }

    /**
     * Save the maximum number of events in notification
     * @param maxEvents The maximum number of events
     */
    fun saveMaxEventsInNotification(maxEvents: Int) {
        preferences.edit().putInt(KEY_MAX_EVENTS_IN_NOTIFICATION, maxEvents).apply()
    }

    /**
     * Get the maximum number of events in notification
     * @return The maximum number of events
     */
    fun getMaxEventsInNotification(): Int {
        return preferences.getInt(KEY_MAX_EVENTS_IN_NOTIFICATION, 3)
    }

    /**
     * Check if this is the first app launch
     */
    fun isFirstLaunch(): Boolean {
        return preferences.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Mark that the app has been launched before
     */
    fun setFirstLaunchComplete() {
        preferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
}
