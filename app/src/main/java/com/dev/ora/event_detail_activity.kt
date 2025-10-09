package com.dev.ora

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.TextViewCompat
import com.dev.ora.data.CountdownEvent
import com.dev.ora.databinding.ActivityEventDetailBinding
import com.dev.ora.notification.CountdownNotificationService
import com.dev.ora.reminder.ReminderScheduler
import com.dev.ora.utils.CountdownUtils
import com.dev.ora.viewmodel.MainViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventDetailBinding
    private lateinit var event: CountdownEvent
    private var wasScreenOnEnabled = false
    private val viewModel: MainViewModel by viewModels()
    private lateinit var reminderScheduler: ReminderScheduler

    private val countdownUpdateHandler = Handler(Looper.getMainLooper())
    private val countdownUpdateRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            countdownUpdateHandler.postDelayed(this, 1000)
        }
    }

    private var progressUpdateRunnable: Runnable? = null
    private var isCurrentlyExpired = false
    private var hasDimmingApplied = false
    private var countdownContentRevealed = false
    private var entranceAnimated = false
    private var cardEntranceAnimated = false

    private val ANIM_MASTER_DURATION = 320L
    private val CARD_BASE_DELAY = 40L
    private val CARD_STAGGER = 60L
    private val ROW_BASE_DELAY = 120L
    private val ROW_STAGGER = 50L
    private val NUMBER_STAGGER = 60L
    private val CONTAINER_FADE_DURATION = ANIM_MASTER_DURATION

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_EVENT_TITLE = "extra_event_title"
        const val EXTRA_EVENT_DESCRIPTION = "extra_event_description"
        const val EXTRA_EVENT_TARGET_DATE = "extra_event_target_date"
        const val EXTRA_EVENT_CREATED_DATE = "extra_event_created_date"
        const val EXTRA_EVENT_COLOR = "extra_event_color"
        const val EXTRA_EVENT_IS_PINNED = "extra_event_is_pinned"
        const val EXTRA_EVENT_HAS_REMINDERS = "extra_event_has_reminders"
        private const val TAG = "EventDetailActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Disable clipping on ALL parent containers to allow glow overflow
        binding.root.clipChildren = false
        binding.root.clipToPadding = false
        binding.countdownOuterContainer.clipChildren = false
        binding.countdownOuterContainer.clipToPadding = false
        binding.countdownCardContainer.clipChildren = false
        binding.countdownCardContainer.clipToPadding = false
        reminderScheduler = ReminderScheduler(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyNavigationBarAppearance()

        try {
            event = getEventFromIntent()
            setupWindowInsets()
            setupViews()
            setupClickListeners()

            viewModel.pinActionResult.observe(this) { message ->
                message?.let {
                    Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearPinActionResult()
                }
            }

            countdownUpdateHandler.post(countdownUpdateRunnable)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Error initializing activity", t)
            try {
                val stack = java.io.StringWriter().also { t.printStackTrace(java.io.PrintWriter(it)) }.toString()
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Error opening event")
                    .setMessage("${t::class.java.name}: ${t.message}\n\n$stack")
                    .setPositiveButton("Close") { _, _ -> finish() }
                    .setOnDismissListener { finish() }
                    .show()
            } catch (ignored: Throwable) {
                android.util.Log.e(TAG, "Failed to show error dialog", ignored)
                finish()
            }
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun getEventFromIntent(): CountdownEvent {
        return CountdownEvent(
            id = intent.getLongExtra(EXTRA_EVENT_ID, 0),
            title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "",
            description = intent.getStringExtra(EXTRA_EVENT_DESCRIPTION) ?: "",
            targetDate = Date(intent.getLongExtra(EXTRA_EVENT_TARGET_DATE, 0)),
            createdAt = Date(intent.getLongExtra(EXTRA_EVENT_CREATED_DATE, 0)),
            color = intent.getStringExtra(EXTRA_EVENT_COLOR) ?: "#DC143C",
            isPinned = intent.getBooleanExtra(EXTRA_EVENT_IS_PINNED, false),
            hasReminders = intent.getBooleanExtra(EXTRA_EVENT_HAS_REMINDERS, true)
        )
    }

    private fun setupViews() {
        binding.countdownContent.visibility = View.INVISIBLE
        binding.countdownContent.alpha = 0f
        binding.countdownPlaceholder.visibility = View.VISIBLE
        binding.countdownPlaceholder.alpha = 1f
        binding.eventTitle.text = event.title
        binding.eventDescription.visibility = if (event.description.isNotEmpty()) {
            binding.eventDescription.text = event.description
            View.VISIBLE
        } else {
            View.GONE
        }
        try {
            binding.colorIndicator.setBackgroundColor(event.color.toColorInt())
        } catch (e: IllegalArgumentException) {
            binding.colorIndicator.setBackgroundColor("#DC143C".toColorInt())
        }
        binding.pinIcon.visibility = if (event.isPinned) View.VISIBLE else View.GONE
        binding.targetDate.text = SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' h:mm a", Locale.getDefault()).format(event.targetDate)
        binding.createdDate.text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(event.createdAt)
        binding.remindersStatus.text = if (event.hasReminders) "Enabled" else "Disabled"
        setupProgressBorder()
        try {
            val digitMinSp = 4; val digitMaxSp = 72; val digitStepSp = 1
            listOf(binding.daysValue, binding.hoursValue, binding.minutesValue, binding.secondsValue).forEach { tv ->
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tv, digitMinSp, digitMaxSp, digitStepSp, TypedValue.COMPLEX_UNIT_SP)
            }
            val labelMinSp = 2; val labelMaxSp = 18; val labelStepSp = 1
            listOf(binding.daysLabel, binding.hoursLabel, binding.minutesLabel, binding.secondsLabel).forEach { tv ->
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tv, labelMinSp, labelMaxSp, labelStepSp, TypedValue.COMPLEX_UNIT_SP)
            }
        } catch (t: Throwable) { /* ignore */ }
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        val surfaceColor = typedValue.data
        binding.countdownCard.setCardBackgroundColor(surfaceColor)
        if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
            binding.countdownCard.cardElevation = 0f
        } else {
            val elevationInPixels = 2 * resources.displayMetrics.density
            binding.countdownCard.cardElevation = elevationInPixels
        }
        val countdownTime = CountdownUtils.calculateTimeRemaining(event.targetDate)
        isCurrentlyExpired = countdownTime.isExpired
        updateCountdown()
        if (isCurrentlyExpired) {
            applyDimmingEffects(animate = false)
            // Also set the progress border to dimmed state initially if expired
            binding.countdownProgressBorder.setDimmed(true, animate = false)
        }
        binding.root.post {
            try {
                animateCardPopups(binding.root, excludeIds = setOf(binding.countdownPlaceholder.id, binding.bottomCard.id))
                animateLinearLayoutsFromBottomScale(binding.root, excludeIds = setOf(binding.countdownPlaceholder.id))
            } catch (ignored: Exception) { /* Ignore */ }
        }
    }

    private fun setupProgressBorder() {
        try {
            val border = binding.countdownProgressBorder
            border.visibility = View.VISIBLE
            border.setBorderColor(event.color)
            border.setProgress(calculateEventProgress(), animate = true)
            startProgressUpdates()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to setup progress border", e)
            binding.countdownProgressBorder.visibility = View.GONE
        }
    }

    private fun calculateEventProgress(): Float {
        val now = System.currentTimeMillis()
        val created = event.createdAt.time
        val target = event.targetDate.time
        return when {
            now >= target -> 1f
            now <= created -> 0f
            else -> ((now - created).toFloat() / (target - created).toFloat()).coerceIn(0f, 1f)
        }
    }

    private fun startProgressUpdates() {
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                try {
                    binding.countdownProgressBorder.setProgress(calculateEventProgress(), animate = false)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to update progress", e)
                }
                countdownUpdateHandler.postDelayed(this, 1000)
            }
        }
        countdownUpdateHandler.postDelayed(progressUpdateRunnable!!, 1500)
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.shareButton.setOnClickListener { shareEvent() }

        binding.pinButton.setOnClickListener {
            viewModel.togglePinStatus(event)
            event = event.copy(isPinned = !event.isPinned)
            binding.pinIcon.visibility = if (event.isPinned) View.VISIBLE else View.GONE
            try {
                CountdownNotificationService.startOrUpdateService(this)
            } catch (ignored: Exception) { /* Ignore */ }
        }

        binding.deleteButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Event")
                .setMessage("Are you sure you want to delete \"${event.title}\"?")
                .setPositiveButton("Delete") { _, _ ->
                    if (event.hasReminders) reminderScheduler.cancelReminders(event.id)
                    viewModel.deleteEvent(event)
                    try {
                        CountdownNotificationService.startOrUpdateService(this)
                    } catch (ignored: Exception) { /* Ignore */ }
                    Toast.makeText(this, "Event deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.keepScreenOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) enableKeepScreenOn() else disableKeepScreenOn()
        }
    }

    private fun applyDimmingEffects(animate: Boolean) {
        hasDimmingApplied = true
        val duration = if (animate) 500L else 0L
        val interpolator = DecelerateInterpolator()
        val cards = listOf(
            binding.root.findViewById<MaterialCardView>(R.id.countdownCard),
        ).filterNotNull()
        cards.forEach { card ->
            card.animate().alpha(0.65f).setDuration(duration).setInterpolator(interpolator).start()
        }
        binding.countdownContainer.animate().alpha(0.4f).scaleX(0.95f).scaleY(0.95f).setDuration(duration).setInterpolator(interpolator).start()
        binding.eventTitle.animate().alpha(0.6f).setDuration(duration).start()
        if (binding.eventDescription.visibility == View.VISIBLE) {
            binding.eventDescription.animate().alpha(0.5f).setDuration(duration).start()
        }
        binding.targetDate.animate().alpha(0.5f).setDuration(duration).start()
        binding.createdDate.animate().alpha(0.5f).setDuration(duration).start()
        binding.remindersStatus.animate().alpha(0.5f).setDuration(duration).start()
        binding.colorIndicator.animate().alpha(0.3f).setDuration(duration).start()
        val buttons = listOf(binding.pinButton, binding.deleteButton, binding.shareButton)
        buttons.forEach { button -> button.animate().alpha(0.4f).setDuration(duration).start() }
        binding.backButton.animate().alpha(1f).setDuration(duration).start()
        binding.backButton.isEnabled = true
        if (binding.pinIcon.visibility == View.VISIBLE) {
            binding.pinIcon.animate().alpha(0.4f).setDuration(duration).start()
        }
        binding.statusIndicator.apply {
            if (visibility != View.VISIBLE) {
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(0.8f).setDuration(duration).setStartDelay(if (animate) 200L else 0L).start()
            }
        }
        binding.daysLabel.animate().alpha(0.5f).setDuration(duration).start()
        binding.hoursLabel.animate().alpha(0.5f).setDuration(duration).start()
        binding.minutesLabel.animate().alpha(0.5f).setDuration(duration).start()
        binding.secondsLabel.animate().alpha(0.5f).setDuration(duration).start()
    }

    private fun updateCountdown() {
        val countdownTime = CountdownUtils.calculateTimeRemaining(event.targetDate)
        val wasExpired = isCurrentlyExpired
        isCurrentlyExpired = countdownTime.isExpired

        // Sync the progress border dimming with event expiration
        binding.countdownProgressBorder.setDimmed(isCurrentlyExpired, animate = wasExpired != isCurrentlyExpired)

        if (isCurrentlyExpired && !wasExpired) {
            applyDimmingEffects(animate = true)
        } else if (isCurrentlyExpired && !hasDimmingApplied) {
            applyDimmingEffects(animate = false)
        }

        if (countdownTime.isExpired) {
            binding.statusIndicator.visibility = View.VISIBLE
            binding.statusIndicator.text = "Event Ended"
            binding.daysValue.text = "00"
            binding.hoursValue.text = "00"
            binding.minutesValue.text = "00"
            binding.secondsValue.text = "00"
            val grayColor = ContextCompat.getColor(this, android.R.color.darker_gray)
            binding.daysValue.setTextColor(grayColor)
            binding.hoursValue.setTextColor(grayColor)
            binding.minutesValue.setTextColor(grayColor)
            binding.secondsValue.setTextColor(grayColor)
            if (binding.bottomCard.visibility == View.VISIBLE) {
                binding.bottomCard.visibility = View.GONE
            }
            if (wasScreenOnEnabled) {
                disableKeepScreenOn()
                binding.keepScreenOnSwitch.isChecked = false
            }
        } else {
            if (binding.bottomCard.visibility == View.GONE) {
                binding.bottomCard.visibility = View.VISIBLE
            }
            binding.statusIndicator.visibility = View.GONE
            try {
                binding.daysValue.setTextColor(ContextCompat.getColor(this, R.color.countdown_days))
                binding.hoursValue.setTextColor(ContextCompat.getColor(this, R.color.countdown_hours))
                binding.minutesValue.setTextColor(ContextCompat.getColor(this, R.color.countdown_minutes))
                binding.secondsValue.setTextColor(ContextCompat.getColor(this, R.color.countdown_seconds))
            } catch (e: Exception) {
                val defaultColor = ContextCompat.getColor(this, android.R.color.black)
                binding.daysValue.setTextColor(defaultColor)
                binding.hoursValue.setTextColor(defaultColor)
                binding.minutesValue.setTextColor(defaultColor)
                binding.secondsValue.setTextColor(defaultColor)
            }
            if (countdownTime.days <= 0) {
                if (binding.daysContainer.visibility == View.VISIBLE) {
                    animateViewDisappear(binding.daysContainer)
                }
                adjustCountdownLayout(3)
            } else {
                if (binding.daysContainer.visibility != View.VISIBLE) {
                    animateViewAppear(binding.daysContainer)
                    if (countdownContentRevealed) {
                        try {
                            binding.daysValue.alpha = if (isCurrentlyExpired) 0.6f else 1f
                            binding.daysLabel.alpha = if (isCurrentlyExpired) 0.5f else 1f
                        } catch (ignored: Exception) { /* Ignore */ }
                    }
                }
                adjustCountdownLayout(4)
                binding.daysValue.text = String.format(Locale.getDefault(), "%02d", countdownTime.days)
            }
            binding.hoursValue.text = String.format(Locale.getDefault(), "%02d", countdownTime.hours)
            binding.minutesValue.text = String.format(Locale.getDefault(), "%02d", countdownTime.minutes)
            binding.secondsValue.text = String.format(Locale.getDefault(), "%02d", countdownTime.seconds)
        }
        if (!countdownContentRevealed) {
            countdownContentRevealed = true
            try {
                binding.countdownContent.alpha = 0f
                binding.countdownContent.visibility = View.VISIBLE
                val numberViews = listOf(binding.daysValue, binding.hoursValue, binding.minutesValue, binding.secondsValue)
                numberViews.forEach { it.alpha = 0f }
                if (!animationsEnabled()) {
                    binding.countdownContent.alpha = 1f
                    numberViews.forEach { it.alpha = 1f }
                    binding.countdownPlaceholder.visibility = View.GONE
                    return
                }
                binding.countdownContent.animate().alpha(1f).setDuration(CONTAINER_FADE_DURATION).start()
                numberViews.forEachIndexed { idx, v ->
                    if (v.visibility != View.GONE) {
                        v.animate().alpha(1f).setStartDelay(CONTAINER_FADE_DURATION / 2 + idx * NUMBER_STAGGER).setDuration(ANIM_MASTER_DURATION).start()
                    }
                }
                binding.countdownPlaceholder.animate().alpha(0f).setDuration(CONTAINER_FADE_DURATION).withEndAction {
                    binding.countdownPlaceholder.visibility = View.GONE
                    binding.countdownPlaceholder.alpha = 1f
                }.start()
            } catch (ignored: Exception) {
                binding.countdownPlaceholder.visibility = View.GONE
                binding.countdownContent.visibility = View.VISIBLE
                binding.countdownContent.alpha = 1f
            }
        }
    }

    private fun adjustCountdownLayout(itemCount: Int) {
        if (binding.countdownContainer is android.widget.LinearLayout) {
            binding.countdownContainer.weightSum = if (itemCount == 3) 3f else 4f
        }
    }

    private fun animateViewDisappear(view: View) {
        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 300
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(a: Animation?) {}
                override fun onAnimationEnd(a: Animation?) { view.visibility = View.GONE }
                override fun onAnimationRepeat(a: Animation?) {}
            })
        }
        view.startAnimation(fadeOut)
    }

    private fun animateViewAppear(view: View) {
        view.visibility = View.VISIBLE
        view.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 300
            fillAfter = true
        })
    }

    private fun animateCardPopups(root: View, excludeIds: Set<Int> = emptySet(), startDp: Int = 16, startScale: Float = 0.96f) {
        if (cardEntranceAnimated) return; cardEntranceAnimated = true
        root.post {
            val startPx = dpToPx(startDp).toFloat()
            val result = ArrayList<View>()
            val stack = ArrayDeque<View>().apply { add(root) }
            val useTags = containsEntranceTags(root)
            while (stack.isNotEmpty()) {
                val v = stack.removeFirst()
                if (v is MaterialCardView && v.id !in excludeIds) {
                    if (useTags) { if (v.tag == "entrance") result.add(v) } else { result.add(v) }
                }
                if (v is android.view.ViewGroup) { for (i in 0 until v.childCount) stack.add(v.getChildAt(i)) }
            }
            result.sortBy { it.top }
            if (!animationsEnabled()) {
                result.forEach { it.visibility = View.VISIBLE; it.alpha = 1f; it.translationY = 0f; it.scaleX = 1f; it.scaleY = 1f }
                return@post
            }
            result.forEachIndexed { index, view ->
                view.visibility = View.VISIBLE; view.alpha = 0f; view.translationY = startPx; view.scaleX = startScale; view.scaleY = startScale
                view.post { view.pivotX = view.width / 2f; view.pivotY = view.height / 2f }
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                view.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(CARD_BASE_DELAY + index * CARD_STAGGER)
                    .setDuration(ANIM_MASTER_DURATION).setInterpolator(DecelerateInterpolator())
                    .withEndAction { view.setLayerType(View.LAYER_TYPE_NONE, null) }.start()
            }
        }
    }

    private fun animateLinearLayoutsFromBottomScale(root: View, excludeIds: Set<Int> = emptySet(), startDp: Int = 20, startScale: Float = 0.92f) {
        if (entranceAnimated) return; entranceAnimated = true
        root.post {
            val startPx = dpToPx(startDp).toFloat()
            val views = collectLinearLayouts(root, excludeIds).let { all -> if (containsEntranceTags(root)) all.filter { it.tag == "entrance" } else all }.sortedBy { it.top }
            if (!animationsEnabled()) {
                views.forEach { it.visibility = View.VISIBLE; it.alpha = 1f; it.translationY = 0f; it.scaleX = 1f; it.scaleY = 1f }
                return@post
            }
            views.forEachIndexed { index, view ->
                view.visibility = View.VISIBLE; view.alpha = 0f; view.translationY = startPx; view.scaleX = startScale; view.scaleY = startScale
                view.post { view.pivotX = view.width / 2f; view.pivotY = view.height / 2f }
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                view.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(ROW_BASE_DELAY + index * ROW_STAGGER)
                    .setDuration(ANIM_MASTER_DURATION).setInterpolator(DecelerateInterpolator())
                    .withEndAction { view.setLayerType(View.LAYER_TYPE_NONE, null) }.start()
            }
        }
    }

    private fun animationsEnabled(): Boolean = try { android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE) != 0f } catch (e: Exception) { true }
    private fun containsEntranceTags(root: View): Boolean { val stack = ArrayDeque<View>().apply { add(root) }; while(stack.isNotEmpty()) { val v = stack.removeFirst(); if (v.tag == "entrance") return true; if (v is android.view.ViewGroup) for (i in 0 until v.childCount) stack.add(v.getChildAt(i)) }; return false }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
    private fun collectLinearLayouts(root: View, excludeIds: Set<Int> = emptySet()): List<View> { val r = ArrayList<View>(); val s = ArrayDeque<View>().apply{add(root)}; while(s.isNotEmpty()){ val v=s.removeFirst(); if(v is android.widget.LinearLayout&&v.id !in excludeIds)r.add(v); if(v is android.view.ViewGroup)for(i in 0 until v.childCount)s.add(v.getChildAt(i))}; return r }
    private fun enableKeepScreenOn() { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); wasScreenOnEnabled = true }
    private fun disableKeepScreenOn() { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); wasScreenOnEnabled = false }

    private fun shareEvent() {
        val countdownTime = CountdownUtils.calculateTimeRemaining(event.targetDate)
        val shareText = buildString {
            append("⏰ ${event.title}\n\n")
            if (event.description.isNotEmpty()) append("${event.description}\n\n")
            if (!countdownTime.isExpired) {
                append("Time Remaining:\n")
                if (countdownTime.days > 0) append("${countdownTime.days} days, ")
                append("${countdownTime.hours} hours, ${countdownTime.minutes} minutes, ${countdownTime.seconds} seconds\n\n")
            }
            append("Target: ${SimpleDateFormat("MMMM dd, yyyy 'at' h:mm a", Locale.getDefault()).format(event.targetDate)}\n\n")
            append("Shared from ORA - Countdown to What Matters")
        }
        startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText); putExtra(Intent.EXTRA_SUBJECT, event.title) }, "Share Event"))
    }

    private fun applyNavigationBarAppearance() {
        try {
            val surfaceColor = try { ContextCompat.getColor(this, R.color.surface) } catch (e: Exception) { try { val ta = theme.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorSurface)); val c = ta.getColor(0, -16777216); ta.recycle(); c } catch (ignored: Exception) { -16777216 } }
            window.navigationBarColor = surfaceColor
            if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = surfaceColor
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            val isLight = ColorUtils.calculateLuminance(surfaceColor) > 0.5
            controller.isAppearanceLightNavigationBars = isLight
            controller.isAppearanceLightStatusBars = isLight
            window.statusBarColor = surfaceColor
        } catch (e: Exception) { android.util.Log.w(TAG, "Failed to apply nav bar appearance", e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownUpdateHandler.removeCallbacks(countdownUpdateRunnable)
        progressUpdateRunnable?.let { countdownUpdateHandler.removeCallbacks(it) }
        if (wasScreenOnEnabled) disableKeepScreenOn()
    }

    override fun onPause() {
        super.onPause()
        countdownUpdateHandler.removeCallbacks(countdownUpdateRunnable)
        progressUpdateRunnable?.let { countdownUpdateHandler.removeCallbacks(it) }
    }

    override fun onResume() {
        super.onResume()
        countdownUpdateHandler.post(countdownUpdateRunnable)
        progressUpdateRunnable?.let { countdownUpdateHandler.post(it) }
        applyNavigationBarAppearance()
    }
}