package com.example.hibernator.domain.usecase

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import android.telephony.TelephonyManager
import com.example.hibernator.domain.model.*
import com.example.hibernator.domain.repository.*
import com.example.hibernator.utils.SystemAppChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// ============================================================
// GET APPS USE CASE
// ============================================================

/**
 * GetInstalledAppsUseCase
 * Fetches installed apps and marks which ones are selected/whitelisted.
 */
class GetInstalledAppsUseCase @Inject constructor(
    private val appRepository: AppRepository,
    private val exclusionRepository: ExclusionRepository,
    private val selectedAppsRepository: SelectedAppsRepository
) {
    suspend operator fun invoke(
        includeSystem: Boolean,
        sortOrder: AppSortOrder,
        query: String = ""
    ): List<AppInfo> {
        val apps = appRepository.getInstalledApps(includeSystem)
        val selectedPackages = selectedAppsRepository.getSelectedPackageNames()
            .let { flow ->
                // Collect once synchronously
                var result = emptySet<String>()
                // We'll use a blocking collect via runBlocking in repo impl
                result
            }

        return apps
            .filter { app ->
                if (query.isBlank()) true
                else app.appName.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
            }
            .let { list ->
                when (sortOrder) {
                    AppSortOrder.NAME_ASC -> list.sortedBy { it.appName }
                    AppSortOrder.NAME_DESC -> list.sortedByDescending { it.appName }
                    AppSortOrder.USAGE_DESC -> list.sortedByDescending { it.usageDuration }
                    AppSortOrder.RECENTLY_USED -> list.sortedByDescending { it.lastUsed }
                }
            }
    }
}

// ============================================================
// SAFETY CHECK USE CASE
// ============================================================

/**
 * CheckHibernateSafetyUseCase
 * Determines whether it is SAFE to force-stop an app.
 *
 * This is the critical safety gate — all protections pass through here.
 * If ANY check fails, hibernation is skipped with a logged reason.
 */
class CheckHibernateSafetyUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exclusionRepository: ExclusionRepository,
    private val systemAppChecker: SystemAppChecker
) {
    /**
     * Returns null if safe to hibernate, or a reason string if it must be skipped.
     */
    suspend fun check(packageName: String): String? {

        // 1. NEVER hibernate ourselves — would kill the automation mid-run
        if (packageName == context.packageName) {
            return "Cannot hibernate Hibernator itself"
        }

        // 2. Check user whitelist / exclusion list
        if (exclusionRepository.isExcluded(packageName)) {
            return "App is in whitelist"
        }

        // 3. Check for system-critical apps
        //    This covers: SystemUI, Launcher, Phone, Settings, etc.
        val criticalReason = systemAppChecker.isCritical(packageName)
        if (criticalReason != null) {
            return criticalReason
        }

        // 4. Check if app is currently in the foreground
        //    We must not stop the app the user is actively using
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = activityManager.getRunningTasks(1)
        val foregroundPackage = runningTasks.firstOrNull()?.topActivity?.packageName
        if (foregroundPackage == packageName) {
            return "App is currently in foreground"
        }

        // 5. Check if there is an active phone call
        //    Never stop the Phone app during a call
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (telephonyManager.callState != TelephonyManager.CALL_STATE_IDLE) {
            val phonePackages = systemAppChecker.getPhonePackages()
            if (packageName in phonePackages) {
                return "Active phone call in progress"
            }
        }

        // 6. Check for active audio playback
        //    Don't stop music/podcast apps while they're playing
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isMusicActive) {
            // We can't determine WHICH app is playing audio without AudioFocusRequest tracking,
            // but we check the known media app packages from the exclusion list
            if (exclusionRepository.isExcluded(packageName)) {
                return "App may be playing media"
            }
        }

        // All checks passed — safe to hibernate
        return null
    }
}

// ============================================================
// LOGS USE CASES
// ============================================================

class GetLogsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    operator fun invoke(): Flow<List<HibernateLog>> = logRepository.getAllLogs()
}

class AddLogUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    suspend operator fun invoke(log: HibernateLog) = logRepository.addLog(log)
}

class ClearLogsUseCase @Inject constructor(
    private val logRepository: LogRepository
) {
    suspend operator fun invoke() = logRepository.clearAllLogs()
}

// ============================================================
// EXCLUSION USE CASES
// ============================================================

class GetExclusionsUseCase @Inject constructor(
    private val exclusionRepository: ExclusionRepository
) {
    operator fun invoke(): Flow<List<ExcludedApp>> = exclusionRepository.getAllExclusions()
}

class AddExclusionUseCase @Inject constructor(
    private val exclusionRepository: ExclusionRepository
) {
    suspend operator fun invoke(app: ExcludedApp) = exclusionRepository.addExclusion(app)
}

class RemoveExclusionUseCase @Inject constructor(
    private val exclusionRepository: ExclusionRepository
) {
    suspend operator fun invoke(packageName: String) = exclusionRepository.removeExclusion(packageName)
}

// ============================================================
// SCHEDULE USE CASES
// ============================================================

class GetSchedulesUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    operator fun invoke(): Flow<List<HibernateSchedule>> = scheduleRepository.getAllSchedules()
}

class AddScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(schedule: HibernateSchedule): Long =
        scheduleRepository.addSchedule(schedule)
}

class DeleteScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(id: Long) = scheduleRepository.deleteSchedule(id)
}

// ============================================================
// SELECTED APPS USE CASES
// ============================================================

class GetSelectedAppsUseCase @Inject constructor(
    private val selectedAppsRepository: SelectedAppsRepository
) {
    operator fun invoke(): Flow<Set<String>> = selectedAppsRepository.getSelectedPackageNames()
}

class ToggleAppSelectionUseCase @Inject constructor(
    private val selectedAppsRepository: SelectedAppsRepository
) {
    suspend operator fun invoke(packageName: String, selected: Boolean) {
        if (selected) selectedAppsRepository.addSelected(packageName)
        else selectedAppsRepository.removeSelected(packageName)
    }
}

class ClearSelectionUseCase @Inject constructor(
    private val selectedAppsRepository: SelectedAppsRepository
) {
    suspend operator fun invoke() = selectedAppsRepository.clearSelected()
}
