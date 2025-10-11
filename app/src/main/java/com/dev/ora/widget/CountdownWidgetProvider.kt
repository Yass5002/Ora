package com.dev.ora.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.dev.ora.MainActivity
import com.dev.ora.R
import com.dev.ora.data.CountdownDatabase
import com.dev.ora.data.CountdownEvent
import com.dev.ora.data.CountdownTime
import com.dev.ora.utils.CountdownUtils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class CountdownWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "CountdownWidget"
        private const val ACTION_UPDATE = "com.dev.ora.widget.UPDATE"
        private const val ACTION_FORCE_UPDATE = "com.dev.ora.widget.FORCE_UPDATE"
        private const val UPDATE_INTERVAL_MILLIS = 60000L // Update every minute
        private const val UPDATE_INTERVAL_URGENT = 1000L  // Update every second when urgent

        // Widget size thresholds (dp) - Fixed for 4x2 default widget
        private const val COMPACT_MAX_HEIGHT = 120  // If height is less than this, use compact
        private const val TINY_WIDTH = 150         // Very small width threshold
        private const val STANDARD_MIN_HEIGHT = 150 // Minimum height for standard layout

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, CountdownWidgetProvider::class.java).apply {
                action = ACTION_UPDATE
            }
            context.sendBroadcast(intent)

            // Also schedule next periodic update
            val provider = CountdownWidgetProvider()
            provider.scheduleNextUpdate(context)
        }

        fun forceUpdateAllWidgets(context: Context) {
            Log.d(TAG, "Force updating all widgets")

            // First, send a regular update broadcast
            val intent = Intent(context, CountdownWidgetProvider::class.java).apply {
                action = ACTION_FORCE_UPDATE
            }
            context.sendBroadcast(intent)

            // Also trigger system widget update
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CountdownWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            if (allWidgetIds.isNotEmpty()) {
                val updateIntent = Intent(context, CountdownWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
                }
                context.sendBroadcast(updateIntent)
            }

            // Ensure periodic updates are scheduled
            val provider = CountdownWidgetProvider()
            provider.scheduleNextUpdate(context)
        }
    }

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val updateJobs = mutableMapOf<Int, Job>()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        Log.d(TAG, "onReceive: ${intent.action}")

        when (intent.action) {
            ACTION_UPDATE, ACTION_FORCE_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, CountdownWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

                if (allWidgetIds.isNotEmpty()) {
                    onUpdate(context, appWidgetManager, allWidgetIds)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // Handle system widget updates
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    onUpdate(context, appWidgetManager, appWidgetIds)
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        for (appWidgetId in appWidgetIds) {
            // Cancel any existing update job for this widget
            updateJobs[appWidgetId]?.cancel()

            // Start new update job
            updateJobs[appWidgetId] = widgetScope.launch {
                try {
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    var minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    var minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

                    // If dimensions are 0 (first placement), use defaults from widget_info.xml
                    if (minWidth == 0 || minHeight == 0) {
                        minWidth = 250  // Your default from widget_info.xml
                        minHeight = 110 // Your default from widget_info.xml
                    }

                    updateWidget(context, appWidgetManager, appWidgetId, minWidth, minHeight)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget $appWidgetId", e)
                }
            }
        }

        // Schedule next update
        scheduleNextUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        Log.d(TAG, "Widget $appWidgetId options changed")

        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        updateWidget(context, appWidgetManager, appWidgetId, minWidth, minHeight)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First widget enabled, starting updates")
        scheduleNextUpdate(context)

        // Force immediate update
        forceUpdateAllWidgets(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Last widget disabled, stopping updates")
        cancelUpdates(context)
        widgetScope.cancel()
        updateJobs.clear()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Clean up jobs for deleted widgets
        appWidgetIds.forEach { id ->
            updateJobs[id]?.cancel()
            updateJobs.remove(id)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        width: Int = 250,
        height: Int = 110
    ) {
        widgetScope.launch {
            try {
                Log.d(TAG, "Updating widget $appWidgetId with size: ${width}x${height}")

                val event = getMostImportantEvent(context)
                val views = createRemoteViews(context, event, width, height)

                // Update widget
                appWidgetManager.updateAppWidget(appWidgetId, views)

                Log.d(TAG, "Widget $appWidgetId updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $appWidgetId", e)

                // Try to show error state
                try {
                    val errorViews = RemoteViews(context.packageName, R.layout.widget_countdown)
                    showEmptyState(errorViews)
                    appWidgetManager.updateAppWidget(appWidgetId, errorViews)
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "Failed to show error state", fallbackError)
                }
            }
        }
    }

    private suspend fun getMostImportantEvent(context: Context): CountdownEvent? =
        withContext(Dispatchers.IO) {
            try {
                val database = CountdownDatabase.getDatabase(context)
                val dao = database.countdownEventDao()
                val events = dao.getUpcomingEvents()

                if (events.isEmpty()) {
                    Log.d(TAG, "No events found")
                    return@withContext null
                }

                // Priority: Pinned events first, then closest deadline
                val selectedEvent = events.filter { it.isPinned }.minByOrNull { it.targetDate.time }
                    ?: events.minByOrNull { it.targetDate.time }

                Log.d(TAG, "Selected event: ${selectedEvent?.title}")
                selectedEvent
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching events from database", e)
                null
            }
        }

    private fun calculateProgress(event: CountdownEvent): Int {
        val now = System.currentTimeMillis()
        val start = event.createdAt.time
        val end = event.targetDate.time

        if (now >= end) return 100
        if (now <= start) return 0

        val elapsed = now - start
        val total = end - start
        return ((elapsed.toFloat() / total) * 100).toInt()
    }

    private fun shouldUseCompactLayout(width: Int, height: Int): Boolean {
        // Use compact layout for:
        // - Height less than 120dp (can't fit standard layout properly)
        // - Width less than 150dp (too narrow)
        // - 4x2 widgets and smaller (your default size)
        return height < COMPACT_MAX_HEIGHT || width < TINY_WIDTH
    }

    private fun createRemoteViews(
        context: Context,
        event: CountdownEvent?,
        widgetWidth: Int,
        widgetHeight: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_countdown)

        // Set click intent to open app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        if (event == null) {
            // Show empty state
            showEmptyState(views)
            return views
        }

        // Set event title
        views.setTextViewText(R.id.widget_title, event.title)

        // Show pin icon if pinned
        views.setViewVisibility(
            R.id.widget_pin_icon,
            if (event.isPinned) View.VISIBLE else View.GONE
        )

        // Calculate countdown
        val countdownTime = CountdownUtils.calculateTimeRemaining(event.targetDate)

        // Calculate and set actual progress
        val progress = calculateProgress(event)
        views.setProgressBar(R.id.widget_progress_bar, 100, progress, false)
        views.setViewVisibility(R.id.widget_progress_bar, View.VISIBLE)

        // Set status indicator color based on urgency
        updateStatusIndicator(views, countdownTime, event.color)

        // Format and display date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        views.setTextViewText(R.id.widget_date, dateFormat.format(event.targetDate))

        // Choose layout based on widget size and event state
        when {
            countdownTime.isExpired -> {
                showExpiredState(views)
            }
            shouldUseCompactLayout(widgetWidth, widgetHeight) -> {
                // This will be true for your default 4x2 widget (250x110)
                showCompactLayout(views, countdownTime)
            }
            else -> {
                // Only for larger widgets (4x3 and bigger)
                showStandardLayout(views, countdownTime)
            }
        }

        return views
    }

    private fun showEmptyState(views: RemoteViews) {
        views.setTextViewText(R.id.widget_title, "No upcoming events")
        views.setViewVisibility(R.id.widget_status_indicator, View.GONE)
        views.setViewVisibility(R.id.widget_progress_bar, View.GONE)
        views.setViewVisibility(R.id.widget_pin_icon, View.GONE)
        views.setTextViewText(R.id.widget_date, "Tap to add an event")

        // Hide all layouts except empty
        views.setViewVisibility(R.id.layout_standard, View.GONE)
        views.setViewVisibility(R.id.layout_compact, View.GONE)
        views.setViewVisibility(R.id.layout_expired, View.GONE)
        views.setViewVisibility(R.id.layout_empty, View.VISIBLE)
    }

    private fun showExpiredState(views: RemoteViews) {
        views.setViewVisibility(R.id.layout_standard, View.GONE)
        views.setViewVisibility(R.id.layout_compact, View.GONE)
        views.setViewVisibility(R.id.layout_expired, View.VISIBLE)
        views.setViewVisibility(R.id.layout_empty, View.GONE)
    }

    private fun showCompactLayout(views: RemoteViews, countdown: CountdownTime) {
        views.setViewVisibility(R.id.layout_standard, View.GONE)
        views.setViewVisibility(R.id.layout_compact, View.VISIBLE)
        views.setViewVisibility(R.id.layout_expired, View.GONE)
        views.setViewVisibility(R.id.layout_empty, View.GONE)

        // Format compact time - now includes seconds when appropriate
        val compactTime = when {
            countdown.days > 999 -> "${countdown.days}d"
            countdown.days > 0 -> "${countdown.days}d ${countdown.hours}h"
            countdown.hours > 0 -> "${countdown.hours}h ${countdown.minutes}m"
            countdown.minutes > 0 -> "${countdown.minutes}m ${countdown.seconds}s"
            else -> "${countdown.seconds}s"
        }
        views.setTextViewText(R.id.widget_time_compact, compactTime)
    }

    private fun showStandardLayout(views: RemoteViews, countdown: CountdownTime) {
        views.setViewVisibility(R.id.layout_standard, View.VISIBLE)
        views.setViewVisibility(R.id.layout_compact, View.GONE)
        views.setViewVisibility(R.id.layout_expired, View.GONE)
        views.setViewVisibility(R.id.layout_empty, View.GONE)

        // Format main time display - now shows seconds when no hours remain
        val mainTime = buildString {
            when {
                countdown.days > 0 -> {
                    append("${countdown.days}d ")
                    append("${String.format(Locale.getDefault(), "%02d", countdown.hours)}h ")
                    append("${String.format(Locale.getDefault(), "%02d", countdown.minutes)}m")
                }
                countdown.hours > 0 -> {
                    append("${countdown.hours}h ")
                    append("${String.format(Locale.getDefault(), "%02d", countdown.minutes)}m")
                }
                countdown.minutes > 0 -> {
                    append("${countdown.minutes}m ")
                    append("${String.format(Locale.getDefault(), "%02d", countdown.seconds)}s")
                }
                else -> {
                    append("${countdown.seconds}s")
                }
            }
        }
        views.setTextViewText(R.id.widget_time_main, mainTime)

        // Format readable time - updated for better accuracy with seconds
        val readableTime = when {
            countdown.days > 30 -> "in ${countdown.days / 30} month${if (countdown.days / 30 > 1) "s" else ""}"
            countdown.days > 7 -> "in ${countdown.days / 7} week${if (countdown.days / 7 > 1) "s" else ""}"
            countdown.days > 1 -> "in ${countdown.days} days"
            countdown.days == 1L -> "tomorrow"
            countdown.hours > 1 -> "in ${countdown.hours} hours"
            countdown.hours == 1L -> "in 1 hour"
            countdown.minutes > 30 -> "in ${countdown.minutes} minutes"
            countdown.minutes > 1 -> "in ${countdown.minutes} minutes"
            countdown.minutes == 1L -> "in 1 minute"
            countdown.seconds > 0 -> "in ${countdown.seconds} seconds"
            else -> "happening now"
        }
        views.setTextViewText(R.id.widget_time_readable, readableTime)
    }

    private fun updateStatusIndicator(views: RemoteViews, countdown: CountdownTime, eventColor: String) {
        // Status dot visibility based on urgency
        val isVeryUrgent = countdown.days == 0L && countdown.hours == 0L // Less than an hour
        val isUrgent = countdown.days == 0L && countdown.hours < 12

        views.setViewVisibility(
            R.id.widget_status_indicator,
            when {
                isVeryUrgent && !countdown.isExpired -> View.VISIBLE
                isUrgent && !countdown.isExpired -> View.VISIBLE
                else -> View.GONE
            }
        )
    }

    private fun getUpdateInterval(context: Context): Long {
        // Check if any event is happening within the next hour
        val hasUrgentEvent = runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    val database = CountdownDatabase.getDatabase(context)
                    val dao = database.countdownEventDao()
                    val events = dao.getUpcomingEvents()

                    events.any { event ->
                        val timeRemaining = event.targetDate.time - System.currentTimeMillis()
                        timeRemaining in 1..3600000 // Between 1ms and 1 hour
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }

        // Update every second if event is within an hour, otherwise every minute
        return if (hasUrgentEvent) UPDATE_INTERVAL_URGENT else UPDATE_INTERVAL_MILLIS
    }

    private fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CountdownWidgetProvider::class.java).apply {
            action = ACTION_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel any existing alarm first
        alarmManager.cancel(pendingIntent)

        val updateInterval = getUpdateInterval(context)
        val nextUpdate = System.currentTimeMillis() + updateInterval

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ - check if we can schedule exact alarms
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC,
                        nextUpdate,
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextUpdate,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC,
                    nextUpdate,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC,
                    nextUpdate,
                    pendingIntent
                )
            }

            Log.d(TAG, "Next update scheduled for ${Date(nextUpdate)} (interval: ${updateInterval}ms)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm, using inexact", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextUpdate,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule update", e)
        }
    }

    private fun cancelUpdates(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, CountdownWidgetProvider::class.java).apply {
                action = ACTION_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Updates cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling updates", e)
        }
    }
}