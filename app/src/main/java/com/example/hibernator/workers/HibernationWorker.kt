package com.example.hibernator.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.hibernator.domain.model.HibernateSchedule
import com.example.hibernator.domain.model.ScheduleType
import com.example.hibernator.domain.repository.ScheduleRepository
import com.example.hibernator.domain.repository.SelectedAppsRepository
import com.example.hibernator.services.HibernationForegroundService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * HibernationWorker
 * ==================
 * WorkManager worker that triggers a scheduled hibernation run.
 *
 * Uses @HiltWorker + @AssistedInject so Hilt can inject dependencies
 * into WorkManager workers (which are constructed by WorkManager, not Hilt).
 *
 * On execution:
 * 1. Loads the schedule from Room DB by ID
 * 2. Gets the list of apps to hibernate
 * 3. Starts HibernationForegroundService with that list
 * 4. Re-enqueues the next occurrence for repeating schedules
 */
@HiltWorker
class HibernationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val selectedAppsRepository: SelectedAppsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_SCHEDULE_ID = "schedule_id"
        const val WORK_TAG_PREFIX = "hibernation_schedule_"

        /**
         * Enqueues a unique one-time work request for the given schedule.
         * The initial delay is calculated from "now" to the next occurrence
         * of the schedule's hour:minute time.
         */
        fun enqueueSchedule(context: Context, schedule: HibernateSchedule) {
            val delay = calculateInitialDelayMs(schedule)
            val workTag = "$WORK_TAG_PREFIX${schedule.id}"

            val request = OneTimeWorkRequestBuilder<HibernationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SCHEDULE_ID to schedule.id))
                .addTag(workTag)
                .setConstraints(
                    Constraints.Builder()
                        // Don't require network — this app is offline by design
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workTag,
                ExistingWorkPolicy.REPLACE,  // Replace if already scheduled
                request
            )

            Log.d("HibernationWorker", "Enqueued schedule ${schedule.id} in ${delay / 1000}s")
        }

        /**
         * Cancels a scheduled work request by schedule ID.
         */
        fun cancelSchedule(context: Context, scheduleId: Long) {
            WorkManager.getInstance(context)
                .cancelAllWorkByTag("$WORK_TAG_PREFIX$scheduleId")
        }

        /**
         * Calculates milliseconds until next trigger.
         * - If the target time today is still in the future: delay until today's occurrence
         * - If it has already passed today: delay until tomorrow's occurrence
         *
         * For WEEKLY schedules: also skips to the correct day of week.
         */
        private fun calculateInitialDelayMs(schedule: HibernateSchedule): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, schedule.hour)
                set(Calendar.MINUTE, schedule.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If target time already passed today, schedule for tomorrow
            if (!target.after(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            // For weekly schedules, advance to the next matching day
            if (schedule.type == ScheduleType.WEEKLY && schedule.daysOfWeek.isNotEmpty()) {
                var attempts = 0
                while (
                    (target.get(Calendar.DAY_OF_WEEK) - 1) !in schedule.daysOfWeek
                    && attempts < 8
                ) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                    attempts++
                }
            }

            val delay = target.timeInMillis - now.timeInMillis
            return maxOf(delay, 1000L) // Minimum 1 second delay
        }
    }

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1L)
        if (scheduleId == -1L) {
            Log.e("HibernationWorker", "Invalid schedule ID")
            return Result.failure()
        }

        val schedule = scheduleRepository.getScheduleById(scheduleId)
        if (schedule == null) {
            Log.w("HibernationWorker", "Schedule $scheduleId not found — may have been deleted")
            return Result.success() // Don't retry — schedule was intentionally deleted
        }

        if (!schedule.isEnabled) {
            Log.d("HibernationWorker", "Schedule $scheduleId is disabled — skipping")
            return Result.success()
        }

        // Determine which apps to hibernate:
        // If the schedule specifies explicit packages, use those.
        // Otherwise, use all currently user-selected apps.
        val packagesToHibernate: List<String> = if (schedule.packageNames.isNotEmpty()) {
            schedule.packageNames
        } else {
            selectedAppsRepository.getSelectedPackageNames().first().toList()
        }

        if (packagesToHibernate.isEmpty()) {
            Log.d("HibernationWorker", "No apps to hibernate for schedule $scheduleId")
            reEnqueueIfRepeating(schedule)
            return Result.success()
        }

        Log.d("HibernationWorker", "Triggering hibernation for ${packagesToHibernate.size} apps")

        // Delegate to the foreground service which orchestrates automation
        HibernationForegroundService.startService(
            context = context,
            packages = packagesToHibernate,
            names = packagesToHibernate // Service will resolve real names from PackageManager
        )

        // Re-enqueue for next run if this is a repeating schedule
        reEnqueueIfRepeating(schedule)

        return Result.success()
    }

    /**
     * For DAILY and WEEKLY schedules, automatically re-enqueue the next occurrence.
     * ONE_TIME schedules are not re-enqueued.
     */
    private fun reEnqueueIfRepeating(schedule: HibernateSchedule) {
        if (schedule.type == ScheduleType.DAILY ||
            schedule.type == ScheduleType.WEEKLY ||
            schedule.type == ScheduleType.SLEEP_MODE ||
            schedule.type == ScheduleType.FOCUS_MODE
        ) {
            enqueueSchedule(context, schedule)
        }
    }
}
