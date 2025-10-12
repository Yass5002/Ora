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
import android.util.TypedValue
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
import kotlin.math.max
import kotlin.math.sqrt

class CountdownWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "CountdownWidget"
        private const val ACTION_UPDATE = "com.dev.ora.widget.UPDATE"
        private const val ACTION_FORCE_UPDATE = "com.dev.ora.widget.FORCE_UPDATE"
        private const val UPDATE_INTERVAL_MILLIS = 60000L // Update every minute
        private const val UPDATE_INTERVAL_URGENT = 1000L  // Update every second when urgent

        // Widget size thresholds (dp)
        private const val WIDGET_HEIGHT_2_UNITS = 100  // Minimum height for 2x2 widgets

        // Baseline used for responsive text scaling (dp)
        private const val BASE_WIDTH_DP = 250
        private const val BASE_HEIGHT_DP = 110

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
                        minWidth = BASE_WIDTH_DP  // default
                        minHeight = BASE_HEIGHT_DP // default
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

        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, BASE_WIDTH_DP)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, BASE_HEIGHT_DP)

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
        width: Int = BASE_WIDTH_DP,
        height: Int = BASE_HEIGHT_DP
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
        val raw = ((elapsed.toFloat() / total) * 100).toInt()
        return raw.coerceIn(0, 100)
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

        // Set status indicator visibility based on urgency
        updateStatusIndicator(views, countdownTime, event.color)

        // Format and display date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        views.setTextViewText(R.id.widget_date, dateFormat.format(event.targetDate))

        Log.d(TAG, "Widget size: ${widgetWidth}x${widgetHeight}dp")

        when {
            countdownTime.isExpired -> {
                showExpiredState(views)
            }
            else -> {
                // Always show standard layout with card
                showStandardLayout(views, countdownTime)
            }
        }

        // Apply responsive text sizes
        applyResponsiveTextSizes(views, widgetWidth, widgetHeight)

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
        views.setViewVisibility(R.id.layout_expired, View.GONE)
        views.setViewVisibility(R.id.layout_empty, View.VISIBLE)
    }

    private fun showExpiredState(views: RemoteViews) {
        views.setViewVisibility(R.id.layout_standard, View.GONE)
        views.setViewVisibility(R.id.layout_expired, View.VISIBLE)
        views.setViewVisibility(R.id.layout_empty, View.GONE)
    }

    private fun showStandardLayout(views: RemoteViews, countdown: CountdownTime) {
        // Always show standard layout for all widgets
        views.setViewVisibility(R.id.layout_standard, View.VISIBLE)
        views.setViewVisibility(R.id.layout_expired, View.GONE)
        views.setViewVisibility(R.id.layout_empty, View.GONE)

        Log.d(TAG, "Showing standard layout with card background")

        // Format main time display - shows seconds when appropriate
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

        // Format readable time
        val readableTime = when {
            countdown.days > 30 -> "in ${countdown.days / 30} month${if (countdown.days / 30 > 1) "s" else ""}"
            countdown.days > 7 -> "in ${countdown.days / 7} week${if (countdown.days / 7 > 1) "s" else ""}"
            countdown.days > 1 -> "in ${countdown.days} days"
            countdown.days == 1L -> "tomorrow"
            countdown.days == 0L -> {
                when {
                    countdown.hours >= 12 -> "later today"
                    countdown.hours >= 6 -> "today"
                    countdown.hours >= 3 -> "this afternoon"
                    countdown.hours >= 1 -> "very soon"
                    countdown.hours == 0L && countdown.minutes >= 30 -> "within the hour"
                    countdown.hours == 0L && countdown.minutes >= 10 -> "coming up"
                    countdown.hours == 0L && countdown.minutes >= 5 -> "in moments"
                    countdown.hours == 0L && countdown.minutes >= 1 -> "imminent"
                    countdown.seconds > 30 -> "less than a minute"
                    countdown.seconds > 10 -> "seconds away"
                    countdown.seconds > 0 -> "happening now"
                    else -> "now"
                }
            }
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
        val now = System.currentTimeMillis()
        // Align 1s updates to the next second for smoother ticking
        val nextUpdate = if (updateInterval == UPDATE_INTERVAL_URGENT) {
            ((now / 1000L) + 1L) * 1000L + 50L
        } else {
            now + updateInterval
        }

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

    // Responsive text sizing based on widget size
    private fun applyResponsiveTextSizes(views: RemoteViews, width: Int, height: Int) {
        // Check if this is a 2x2 or larger widget
        val isHeight2Units = height >= WIDGET_HEIGHT_2_UNITS

        // Compute a scale factor based on area
        val scaleW = width.toFloat() / BASE_WIDTH_DP
        val scaleH = height.toFloat() / BASE_HEIGHT_DP
        val areaScale = sqrt(max(0.5f, scaleW) * max(0.5f, scaleH))

        // Use a conservative scale to preserve card aesthetics
        val scale = areaScale.coerceIn(0.85f, 1.30f)

        // Title sizing
        val titleBase = 20f
        val titleSize = (titleBase * scale).coerceIn(14f, 22f)
        views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, titleSize)

        // Allow 2 lines on 2x2 widgets for better title display
        val maxLines = if (isHeight2Units) 2 else 1
        views.setInt(R.id.widget_title, "setMaxLines", maxLines)

        // Footer date
        val dateBase = 10f
        val dateSize = (dateBase * scale).coerceIn(8f, 12f)
        views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, dateSize)

        // Standard layout text sizes (always used)
        val mainBase = if (isHeight2Units) 26f else 24f
        val mainSize = (mainBase * scale).coerceIn(18f, 34f)
        views.setTextViewTextSize(R.id.widget_time_main, TypedValue.COMPLEX_UNIT_SP, mainSize)

        // Readable time
        val readableBase = 11f
        val readableSize = (readableBase * scale).coerceIn(9f, 14f)
        views.setTextViewTextSize(R.id.widget_time_readable, TypedValue.COMPLEX_UNIT_SP, readableSize)
    }
}