package com.example.hibernator.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ExcludedAppEntity
 * Persisted representation of a whitelisted app.
 */
@Entity(tableName = "excluded_apps")
data class ExcludedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val reason: String,         // Stored as string of ExclusionReason enum name
    val addedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)

/**
 * HibernateScheduleEntity
 * Persisted representation of a scheduled hibernation job.
 */
@Entity(tableName = "schedules")
data class HibernateScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: String,           // ScheduleType enum name
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String = "", // Comma-separated e.g. "1,3,5"
    val isEnabled: Boolean = true,
    val packageNames: String = "", // Comma-separated package names, empty = all
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * HibernateLogEntity
 * Persisted record of one hibernation attempt.
 */
@Entity(tableName = "hibernate_logs")
data class HibernateLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val result: String,         // HibernateResult enum name
    val reason: String = "",
    val durationMs: Long = 0
)

/**
 * SelectedAppEntity
 * Stores which apps the user has selected for hibernation.
 */
@Entity(tableName = "selected_apps")
data class SelectedAppEntity(
    @PrimaryKey val packageName: String
)
