package com.example.hibernator.data.repository

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.hibernator.data.database.dao.*
import com.example.hibernator.data.database.entity.*
import com.example.hibernator.domain.model.*
import com.example.hibernator.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================
// AppRepository Implementation
// ============================================================

@Singleton
class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppRepository {

    /**
     * Fetches all installed apps via PackageManager.
     * On API 30+ we need QUERY_ALL_PACKAGES permission declared in manifest.
     * Merges usage stats for each app.
     *
     * Privacy note: We only read metadata (name, icon, package name).
     * We do NOT read app data, files, or content.
     */
    override suspend fun getInstalledApps(includeSystem: Boolean): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong().toInt()
            } else {
                PackageManager.GET_META_DATA
            }

            val installedPackages = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(flags.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                }
            } catch (e: Exception) {
                emptyList()
            }

            // Get usage stats for today
            val usageMap = getUsageStats(
                beginTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000,
                endTime = System.currentTimeMillis()
            )

            installedPackages
                .filter { appInfo ->
                    // Filter system apps if needed
                    if (!includeSystem) {
                        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    } else {
                        true
                    }
                }
                .mapNotNull { appInfo ->
                    try {
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        AppInfo(
                            packageName = appInfo.packageName,
                            appName = appName,
                            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            lastUsed = usageMap[appInfo.packageName] ?: 0L,
                            usageDuration = usageMap[appInfo.packageName + "_duration"] ?: 0L
                        )
                    } catch (e: Exception) {
                        null // Skip apps we can't read
                    }
                }
        }

    /**
     * Reads usage statistics from UsageStatsManager.
     * Requires PACKAGE_USAGE_STATS permission (special permission).
     *
     * Privacy note: We only read last-used timestamps and session durations.
     * We do NOT read what the user did inside the app.
     */
    override suspend fun getUsageStats(beginTime: Long, endTime: Long): Map<String, Long> =
        withContext(Dispatchers.IO) {
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                        as UsageStatsManager

                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    beginTime,
                    endTime
                )

                val result = mutableMapOf<String, Long>()
                stats?.forEach { stat ->
                    result[stat.packageName] = stat.lastTimeUsed
                    result[stat.packageName + "_duration"] = stat.totalTimeInForeground
                }
                result
            } catch (e: Exception) {
                // UsageStats not granted — return empty map
                emptyMap()
            }
        }
}

// ============================================================
// ExclusionRepository Implementation
// ============================================================

@Singleton
class ExclusionRepositoryImpl @Inject constructor(
    private val dao: ExcludedAppDao
) : ExclusionRepository {

    override fun getAllExclusions(): Flow<List<ExcludedApp>> =
        dao.getAllExclusions().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun isExcluded(packageName: String): Boolean =
        dao.isExcluded(packageName)

    override suspend fun addExclusion(app: ExcludedApp) =
        dao.insert(app.toEntity())

    override suspend fun removeExclusion(packageName: String) =
        dao.deleteByPackage(packageName)

    override suspend fun clearAllExclusions() =
        dao.clearAll()

    // Mappers
    private fun ExcludedAppEntity.toDomain() = ExcludedApp(
        id = id,
        packageName = packageName,
        appName = appName,
        reason = try { ExclusionReason.valueOf(reason) } catch (e: Exception) { ExclusionReason.USER_ADDED },
        addedAt = addedAt,
        expiresAt = expiresAt
    )

    private fun ExcludedApp.toEntity() = ExcludedAppEntity(
        id = id,
        packageName = packageName,
        appName = appName,
        reason = reason.name,
        addedAt = addedAt,
        expiresAt = expiresAt
    )
}

// ============================================================
// ScheduleRepository Implementation
// ============================================================

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val dao: ScheduleDao
) : ScheduleRepository {

    override fun getAllSchedules(): Flow<List<HibernateSchedule>> =
        dao.getAllSchedules().map { it.map { e -> e.toDomain() } }

    override suspend fun getScheduleById(id: Long): HibernateSchedule? =
        dao.getById(id)?.toDomain()

    override suspend fun addSchedule(schedule: HibernateSchedule): Long =
        dao.insert(schedule.toEntity())

    override suspend fun updateSchedule(schedule: HibernateSchedule) =
        dao.update(schedule.toEntity())

    override suspend fun deleteSchedule(id: Long) =
        dao.deleteById(id)

    override suspend fun getEnabledSchedules(): List<HibernateSchedule> =
        dao.getEnabledSchedules().map { it.toDomain() }

    private fun HibernateScheduleEntity.toDomain() = HibernateSchedule(
        id = id, label = label,
        type = try { ScheduleType.valueOf(type) } catch (e: Exception) { ScheduleType.DAILY },
        hour = hour, minute = minute,
        daysOfWeek = if (daysOfWeek.isBlank()) emptySet()
        else daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
        isEnabled = isEnabled,
        packageNames = if (packageNames.isBlank()) emptyList()
        else packageNames.split(",").map { it.trim() },
        createdAt = createdAt
    )

    private fun HibernateSchedule.toEntity() = HibernateScheduleEntity(
        id = id, label = label, type = type.name,
        hour = hour, minute = minute,
        daysOfWeek = daysOfWeek.joinToString(","),
        isEnabled = isEnabled,
        packageNames = packageNames.joinToString(","),
        createdAt = createdAt
    )
}

// ============================================================
// LogRepository Implementation
// ============================================================

@Singleton
class LogRepositoryImpl @Inject constructor(
    private val dao: HibernateLogDao
) : LogRepository {

    override fun getAllLogs(): Flow<List<HibernateLog>> =
        dao.getAllLogs().map { it.map { e -> e.toDomain() } }

    override fun getLogsForPackage(packageName: String): Flow<List<HibernateLog>> =
        dao.getLogsForPackage(packageName).map { it.map { e -> e.toDomain() } }

    override suspend fun addLog(log: HibernateLog) {
        dao.insert(log.toEntity())
        dao.pruneOldLogs() // Keep DB from growing unbounded
    }

    override suspend fun clearAllLogs() = dao.clearAll()

    override suspend fun getRecentLogs(limit: Int) =
        dao.getRecent(limit).map { it.toDomain() }

    private fun HibernateLogEntity.toDomain() = HibernateLog(
        id = id, packageName = packageName, appName = appName,
        timestamp = timestamp,
        result = try { HibernateResult.valueOf(result) } catch (e: Exception) { HibernateResult.FAILED },
        reason = reason, durationMs = durationMs
    )

    private fun HibernateLog.toEntity() = HibernateLogEntity(
        id = id, packageName = packageName, appName = appName,
        timestamp = timestamp, result = result.name,
        reason = reason, durationMs = durationMs
    )
}

// ============================================================
// SelectedAppsRepository Implementation
// ============================================================

@Singleton
class SelectedAppsRepositoryImpl @Inject constructor(
    private val dao: SelectedAppDao
) : SelectedAppsRepository {

    override fun getSelectedPackageNames(): Flow<Set<String>> =
        dao.getAllSelected().map { it.toSet() }

    override suspend fun addSelected(packageName: String) =
        dao.insert(SelectedAppEntity(packageName))

    override suspend fun removeSelected(packageName: String) =
        dao.delete(packageName)

    override suspend fun setSelected(packageNames: Set<String>) {
        dao.clearAll()
        packageNames.forEach { dao.insert(SelectedAppEntity(it)) }
    }

    override suspend fun clearSelected() = dao.clearAll()

    override suspend fun isSelected(packageName: String) =
        dao.isSelected(packageName)
}
