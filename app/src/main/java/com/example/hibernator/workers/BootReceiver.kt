package com.example.hibernator.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.hibernator.domain.repository.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BootReceiver
 * =============
 * Restores all enabled WorkManager schedules after device reboot.
 *
 * WHY NEEDED:
 * On many OEMs (MIUI, One UI, ColorOS), WorkManager's pending jobs
 * can be cleared by the OS on reboot. This receiver re-enqueues all
 * enabled schedules so they fire reliably after boot.
 *
 * PERMISSION: android.permission.RECEIVE_BOOT_COMPLETED (declared in manifest)
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    override fun onReceive(context: Context, intent: Intent) {
        // Only act on boot events
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) return

        Log.d("BootReceiver", "Device booted — restoring hibernate schedules")

        // goAsync allows us to do short async work in a BroadcastReceiver
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val schedules = scheduleRepository.getEnabledSchedules()
                schedules.forEach { schedule ->
                    HibernationWorker.enqueueSchedule(context, schedule)
                }
                Log.d("BootReceiver", "Restored ${schedules.size} schedules")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to restore schedules: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
