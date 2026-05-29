package com.example.hibernator.domain.repository

import com.example.hibernator.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * AppRepository
 * Interface for accessing installed app information.
 * Implementations use PackageManager + UsageStatsManager.
 */
interface AppRepository {
    /**
     * Returns all installed apps. If includeSystem is true, system apps are included.
     * Uses UsageStats to populate lastUsed and usageDuration fields.
     */
    suspend fun getInstalledApps(includeSystem: Boolean): List<AppInfo>

    /**
     * Returns usage statistics for all apps in the given time range.
     */
    suspend fun getUsageStats(beginTime: Long, endTime: Long): Map<String, Long>
}

/**
 * ExclusionRepository
 * Interface for managing the whitelist / exclusion list.
 */
interface ExclusionRepository {
    fun getAllExclusions(): Flow<List<ExcludedApp>>
    suspend fun isExcluded(packageName: String): Boolean
    suspend fun addExclusion(app: ExcludedApp)
    suspend fun removeExclusion(packageName: String)
    suspend fun clearAllExclusions()
}

/**
 * ScheduleRepository
 * Interface for managing hibernation schedules.
 */
interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<HibernateSchedule>>
    suspend fun getScheduleById(id: Long): HibernateSchedule?
    suspend fun addSchedule(schedule: HibernateSchedule): Long
    suspend fun updateSchedule(schedule: HibernateSchedule)
    suspend fun deleteSchedule(id: Long)
    suspend fun getEnabledSchedules(): List<HibernateSchedule>
}

/**
 * LogRepository
 * Interface for reading and writing hibernation logs.
 */
interface LogRepository {
    fun getAllLogs(): Flow<List<HibernateLog>>
    fun getLogsForPackage(packageName: String): Flow<List<HibernateLog>>
    suspend fun addLog(log: HibernateLog)
    suspend fun clearAllLogs()
    suspend fun getRecentLogs(limit: Int): List<HibernateLog>
}

/**
 * SelectedAppsRepository
 * Interface for persisting which apps the user has selected to hibernate.
 */
interface SelectedAppsRepository {
    fun getSelectedPackageNames(): Flow<Set<String>>
    suspend fun addSelected(packageName: String)
    suspend fun removeSelected(packageName: String)
    suspend fun setSelected(packageNames: Set<String>)
    suspend fun clearSelected()
    suspend fun isSelected(packageName: String): Boolean
}
