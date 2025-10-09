/*
 * ┌─┐┌─┐┬ ┬┌┐┌┌┬┐┌┬┐┌─┐┬ ┬┌┐┌  ┌─┐┬  ┬┌─┐┌┐┌┌┬┐┌─┐
 * │  │ ││ ││││ │  │ │ │││││││││  ├┤ └┐┌┘├┤ │││ │ └─┐
 * └─┘└─┘└─┘┘└┘ ┴ ─┴┘└─┘└┴┘┘└┘  └─┘ └┘ └─┘┘└┘ ┴ └─┘
 *
 * CountdownEvent.kt - The DNA of Time ⏰
 * Every moment matters, every second counts!
 * This little data class holds the essence of your precious moments.
 *
 * Data structures built with love by Yassine
 * "Time is the most valuable thing we have" - Einstein (probably)
 */

package com.dev.ora.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "countdown_events")
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val targetDate: Date,
    val createdAt: Date = Date(),
    val isActive: Boolean = true,
    val notificationEnabled: Boolean = true,
    val color: String = "#6750A4", // Material You primary color
    val isPinned: Boolean = false,
    val hasReminders: Boolean = true, // Reminders enabled by default
    val reminderIntervals: String? = null // JSON array of custom reminder times (null = use defaults)
)

data class CountdownTime(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val isExpired: Boolean = false
)
