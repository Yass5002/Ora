package com.dev.ora.adapter

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dev.ora.data.CountdownEvent
import com.dev.ora.data.CountdownTime
import com.dev.ora.databinding.ItemCountdownEventBinding
import com.dev.ora.utils.CountdownUtils
import java.text.SimpleDateFormat
import java.util.*

class CountdownAdapter(
    private val onItemClick: (CountdownEvent) -> Unit,
    private val onMenuClick: (CountdownEvent, View) -> Unit
) : ListAdapter<CountdownEvent, CountdownAdapter.CountdownViewHolder>(CountdownDiffCallback()) {

    private val animatedPositions = mutableSetOf<Int>()

    inner class CountdownViewHolder(private val binding: ItemCountdownEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val countdownHandler = Handler(Looper.getMainLooper())
        private var countdownRunnable: Runnable? = null
        private var isCurrentlyExpired = false
        private var hasAppliedInitialState = false

        private val contentViews: List<View> = listOf(
            binding.eventTitle,
            binding.eventDescription,
            binding.countdownContainer,
            binding.targetDate,
            binding.statusIndicator
        )

        fun bind(event: CountdownEvent) {
            // Reset state for recycled views
            resetViewState()

            // Bind all the static data
            binding.apply {
                eventTitle.text = event.title
                eventDescription.text = event.description
                eventDescription.visibility = if (event.description.isNotEmpty()) View.VISIBLE else View.GONE
                pinIcon.visibility = if (event.isPinned) View.VISIBLE else View.GONE

                try {
                    colorIndicator.setBackgroundColor(Color.parseColor(event.color))
                } catch (e: IllegalArgumentException) {
                    colorIndicator.setBackgroundColor(Color.parseColor("#DC143C"))
                }

                val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                targetDate.text = dateFormat.format(event.targetDate)

                root.setOnClickListener { onItemClick(event) }
                optionsMenu.setOnClickListener { onMenuClick(event, it) }
            }

            // Check initial expired state
            val initialCountdown = CountdownUtils.calculateTimeRemaining(event.targetDate)
            isCurrentlyExpired = initialCountdown.isExpired

            // Apply initial state immediately (no animation for initial state)
            applyDimming(isCurrentlyExpired, animate = false)
            updateCountdownDisplay(initialCountdown)

            // Stop any previous countdown
            stopCountdown()

            // Start the countdown timer
            if (!isCurrentlyExpired) {
                startCountdownTimer(event)
            } else {
                // For expired events, we still check periodically in case the app is open during expiration
                startExpiredCheckTimer(event)
            }

            // Handle entry animations
            handleEntryAnimation()

            hasAppliedInitialState = true
        }

        private fun startCountdownTimer(event: CountdownEvent) {
            countdownRunnable = object : Runnable {
                override fun run() {
                    val countdownTime = CountdownUtils.calculateTimeRemaining(event.targetDate)

                    // Check if the event just expired
                    if (countdownTime.isExpired && !isCurrentlyExpired) {
                        isCurrentlyExpired = true
                        applyDimming(true, animate = true)
                    }

                    updateCountdownDisplay(countdownTime)

                    if (!countdownTime.isExpired) {
                        countdownHandler.postDelayed(this, 1000)
                    }
                }
            }
            countdownHandler.post(countdownRunnable!!)
        }

        private fun startExpiredCheckTimer(event: CountdownEvent) {
            // For expired events, check less frequently
            countdownRunnable = object : Runnable {
                override fun run() {
                    val countdownTime = CountdownUtils.calculateTimeRemaining(event.targetDate)
                    updateCountdownDisplay(countdownTime)

                    // Keep checking in case of any updates
                    countdownHandler.postDelayed(this, 5000) // Check every 5 seconds
                }
            }
            countdownHandler.post(countdownRunnable!!)
        }

        private fun applyDimming(isExpired: Boolean, animate: Boolean = true) {
            binding.apply {
                val duration = if (animate && hasAppliedInitialState) 500L else 0L

                if (isExpired) {
                    // Dim the main card
                    root.animate()
                        .alpha(0.65f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    // Dim the countdown container more
                    countdownContainer.animate()
                        .alpha(0.4f)
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    // Dim text elements
                    eventTitle.animate()
                        .alpha(0.6f)
                        .setDuration(duration)
                        .start()

                    eventDescription.animate()
                        .alpha(0.5f)
                        .setDuration(duration)
                        .start()

                    targetDate.animate()
                        .alpha(0.5f)
                        .setDuration(duration)
                        .start()

                    // Fade color indicator
                    colorIndicator.animate()
                        .alpha(0.3f)
                        .setDuration(duration)
                        .start()

                    // Dim interactive elements
                    optionsMenu.animate()
                        .alpha(0.4f)
                        .setDuration(duration)
                        .start()

                    if (pinIcon.visibility == View.VISIBLE) {
                        pinIcon.animate()
                            .alpha(0.4f)
                            .setDuration(duration)
                            .start()
                    }

                    // Show status indicator with animation
                    statusIndicator.apply {
                        text = "Event Ended"
                        visibility = View.VISIBLE
                        alpha = 0f
                        animate()
                            .alpha(0.8f)
                            .setDuration(duration)
                            .setStartDelay(if (animate) 200L else 0L)
                            .start()
                    }

                } else {
                    // Restore normal state
                    root.animate()
                        .alpha(1.0f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    countdownContainer.animate()
                        .alpha(1.0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    eventTitle.animate()
                        .alpha(1.0f)
                        .setDuration(duration)
                        .start()

                    eventDescription.animate()
                        .alpha(1.0f)
                        .setDuration(duration)
                        .start()

                    targetDate.animate()
                        .alpha(1.0f)
                        .setDuration(duration)
                        .start()

                    colorIndicator.animate()
                        .alpha(1.0f)
                        .setDuration(duration)
                        .start()

                    optionsMenu.animate()
                        .alpha(1.0f)
                        .setDuration(duration)
                        .start()

                    if (pinIcon.visibility == View.VISIBLE) {
                        pinIcon.animate()
                            .alpha(1.0f)
                            .setDuration(duration)
                            .start()
                    }

                    statusIndicator.animate()
                        .alpha(0f)
                        .setDuration(if (duration > 0) 200L else 0L)
                        .withEndAction {
                            statusIndicator.visibility = View.GONE
                        }
                        .start()
                }
            }
        }

        private fun updateCountdownDisplay(countdownTime: CountdownTime) {
            binding.apply {
                if (countdownTime.isExpired) {
                    // Display zeros for expired events
                    daysValue.text = "00"
                    hoursValue.text = "00"
                    minutesValue.text = "00"
                    secondsValue.text = "00"

                    // Apply grayed out color to countdown values
                    val grayColor = root.context.getColor(android.R.color.darker_gray)
                    daysValue.setTextColor(grayColor)
                    hoursValue.setTextColor(grayColor)
                    minutesValue.setTextColor(grayColor)
                    secondsValue.setTextColor(grayColor)

                } else {
                    // Display actual countdown
                    daysValue.text = String.format(Locale.getDefault(), "%02d", countdownTime.days)
                    hoursValue.text = String.format(Locale.getDefault(), "%02d", countdownTime.hours)
                    minutesValue.text = String.format(Locale.getDefault(), "%02d", countdownTime.minutes)
                    secondsValue.text = String.format(Locale.getDefault(), "%02d", countdownTime.seconds)

                    // Restore original colors (you may need to adjust these based on your color resources)
                    try {
                        daysValue.setTextColor(root.context.getColor(com.dev.ora.R.color.countdown_days))
                        hoursValue.setTextColor(root.context.getColor(com.dev.ora.R.color.countdown_hours))
                        minutesValue.setTextColor(root.context.getColor(com.dev.ora.R.color.countdown_minutes))
                        secondsValue.setTextColor(root.context.getColor(com.dev.ora.R.color.countdown_seconds))
                    } catch (e: Exception) {
                        // Fallback to default color if resources not found
                        val defaultColor = root.context.getColor(android.R.color.black)
                        daysValue.setTextColor(defaultColor)
                        hoursValue.setTextColor(defaultColor)
                        minutesValue.setTextColor(defaultColor)
                        secondsValue.setTextColor(defaultColor)
                    }
                }
            }
        }

        private fun handleEntryAnimation() {
            if (!animatedPositions.contains(adapterPosition) && !isCurrentlyExpired) {
                animateContentIn()
                animatedPositions.add(adapterPosition)
            } else {
                setContentVisible()
            }
        }

        private fun animateContentIn() {
            val initialDelay = 100L
            val staggerDelay = 30L

            contentViews.forEachIndexed { index, view ->
                if (view.visibility == View.VISIBLE) {
                    view.alpha = 0f
                    view.translationY = 20f
                    view.animate()
                        .alpha(if (isCurrentlyExpired) 0.6f else 1f)
                        .translationY(0f)
                        .setStartDelay(initialDelay + (index * staggerDelay))
                        .setDuration(300)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }

        private fun setContentVisible() {
            contentViews.forEach { view ->
                if (view.visibility == View.VISIBLE) {
                    view.alpha = if (isCurrentlyExpired) 0.6f else 1f
                    view.translationY = 0f
                }
            }
        }

        private fun resetViewState() {
            binding.apply {
                // Reset all alphas to default
                root.alpha = 1.0f
                countdownContainer.alpha = 1.0f
                countdownContainer.scaleX = 1.0f
                countdownContainer.scaleY = 1.0f
                eventTitle.alpha = 1.0f
                eventDescription.alpha = 1.0f
                targetDate.alpha = 1.0f
                colorIndicator.alpha = 1.0f
                optionsMenu.alpha = 1.0f
                pinIcon.alpha = 1.0f
                statusIndicator.alpha = 0f
                statusIndicator.visibility = View.GONE

                // Clear any ongoing animations
                root.clearAnimation()
                countdownContainer.clearAnimation()
                eventTitle.clearAnimation()
                eventDescription.clearAnimation()
                targetDate.clearAnimation()
                colorIndicator.clearAnimation()
                optionsMenu.clearAnimation()
                pinIcon.clearAnimation()
                statusIndicator.clearAnimation()
            }

            hasAppliedInitialState = false
            isCurrentlyExpired = false
        }

        fun clearAnimations() {
            contentViews.forEach { it.clearAnimation() }
        }

        fun stopCountdown() {
            countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
            countdownRunnable = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountdownViewHolder {
        val binding = ItemCountdownEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CountdownViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CountdownViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewDetachedFromWindow(holder: CountdownViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.clearAnimations()
        holder.stopCountdown()
    }

    override fun onViewRecycled(holder: CountdownViewHolder) {
        super.onViewRecycled(holder)
        holder.stopCountdown()
    }
}

class CountdownDiffCallback : DiffUtil.ItemCallback<CountdownEvent>() {
    override fun areItemsTheSame(oldItem: CountdownEvent, newItem: CountdownEvent): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CountdownEvent, newItem: CountdownEvent): Boolean {
        return oldItem == newItem
    }
}