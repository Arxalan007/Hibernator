package com.example.hibernator.domain.model

/**
 * AppInfo
 * Domain model representing an installed application.
 * Contains only what the UI and business logic need — no Android framework types.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isSelected: Boolean = false,
    val lastUsed: Long = 0L,           // Epoch millis from UsageStats
    val usageDuration: Long = 0L,      // Total usage in millis today
    val isWhitelisted: Boolean = false
)

/**
 * ExcludedApp
 * An app that is permanently or temporarily excluded from hibernation.
 */
data class ExcludedApp(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val reason: ExclusionReason,
    val addedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null         // null = permanent exclusion
)

enum class ExclusionReason {
    USER_ADDED,          // User manually added to whitelist
    SYSTEM_CRITICAL,     // System-detected as critical (launcher, accessibility, etc.)
    ACTIVE_NOTIFICATION, // Has ongoing notification at time of check
    MEDIA_PLAYING,       // Currently playing audio/video
    FOREGROUND_APP,      // Currently in foreground
    SCHEDULE_BASED       // Excluded only during certain time windows
}

/**
 * HibernateSchedule
 * A scheduled automatic hibernation job.
 */
data class HibernateSchedule(
    val id: Long = 0,
    val label: String,
    val type: ScheduleType,
    val hour: Int,          // 0-23
    val minute: Int,        // 0-59
    val daysOfWeek: Set<Int> = emptySet(),  // 1=Mon..7=Sun (for WEEKLY type)
    val isEnabled: Boolean = true,
    val packageNames: List<String> = emptyList(),  // empty = all selected apps
    val createdAt: Long = System.currentTimeMillis()
)

enum class ScheduleType {
    ONE_TIME,
    DAILY,
    WEEKLY,
    FOCUS_MODE,
    SLEEP_MODE
}

/**
 * HibernateLog
 * A record of one hibernation attempt for one app.
 */
data class HibernateLog(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val result: HibernateResult,
    val reason: String = "",
    val durationMs: Long = 0
)

enum class HibernateResult {
    SUCCESS,
    FAILED,
    SKIPPED,
    TIMEOUT,
    ALREADY_STOPPED
}

/**
 * AutomationState
 * Represents the current state of the accessibility automation engine.
 * Used to communicate state from the service to the UI via a shared flow.
 */
sealed class AutomationState {
    object Idle : AutomationState()
    data class Processing(val packageName: String, val appName: String, val index: Int, val total: Int) : AutomationState()
    data class Success(val packageName: String, val appName: String) : AutomationState()
    data class Failed(val packageName: String, val reason: String) : AutomationState()
    data class Skipped(val packageName: String, val reason: String) : AutomationState()
    object Completed : AutomationState()
}

/**
 * AppSortOrder
 * Sort options for the installed apps list.
 */
enum class AppSortOrder {
    NAME_ASC,
    NAME_DESC,
    USAGE_DESC,
    RECENTLY_USED
}
