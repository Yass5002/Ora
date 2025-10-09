package com.dev.ora.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class SwipeToActionCallback(
    private val context: Context,
    private val onEdit: (position: Int) -> Unit,
    private val onDelete: (position: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    // Colors with transparency
    private val editColor = Color.parseColor("#4003A9F4") // Sky Blue with 25% opacity
    private val deleteColor = Color.parseColor("#40F44336") // Red with 25% opacity

    // Thresholds
    private val swipeThreshold = 0.7f // 70% to prevent accidental triggers
    private val hapticThreshold = 0.5f // 50% for haptic feedback
    private var hasTriggeredHaptic = false

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false // We don't support drag and drop
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition

        when (direction) {
            ItemTouchHelper.RIGHT -> onEdit(position)
            ItemTouchHelper.LEFT -> onDelete(position)
        }

        // Reset haptic flag
        hasTriggeredHaptic = false
    }

    override fun onChildDraw(
        c: android.graphics.Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // Calculate swipe progress
            val swipeProgress = abs(dX) / itemView.width

            // Trigger haptic feedback at threshold
            if (isCurrentlyActive && swipeProgress >= hapticThreshold && !hasTriggeredHaptic) {
                itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                hasTriggeredHaptic = true
            } else if (swipeProgress < hapticThreshold) {
                hasTriggeredHaptic = false
            }

            // Apply translucent color overlay to the card itself
            if (abs(dX) > itemView.width * 0.01f) { // Only apply if there's meaningful movement
                val overlayColor = when {
                    dX > 0 -> editColor // Swiping right - edit (sky blue)
                    dX < 0 -> deleteColor // Swiping left - delete (red)
                    else -> Color.TRANSPARENT
                }

                // Apply the translucent overlay to the card
                val overlayDrawable = ColorDrawable(overlayColor)
                itemView.foreground = overlayDrawable

                // Increase card elevation during swipe
                val elevation = 4f + (swipeProgress * 4f)
                itemView.elevation = elevation * context.resources.displayMetrics.density
            } else {
                // Clear overlay when movement is minimal
                itemView.foreground = null
                itemView.elevation = 4f * context.resources.displayMetrics.density
            }
        }

        // Translate the card view
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return swipeThreshold
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue * 3 // Make it easier to trigger swipe
    }

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
        return defaultValue * 2
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        // Reset card to default state
        viewHolder.itemView.foreground = null
        viewHolder.itemView.elevation = 4f * context.resources.displayMetrics.density
        viewHolder.itemView.translationX = 0f
        hasTriggeredHaptic = false
    }
}
