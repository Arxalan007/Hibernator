package com.example.hibernator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

/**
 * HibernatorApp
 * ==============
 * Application class annotated with @HiltAndroidApp to enable
 * Hilt dependency injection across the entire app.
 *
 * Also creates the notification channel required for the
 * foreground service (Android 8.0+).
 */
@HiltAndroidApp
class `HibernatorApp` : Application() {

    companion object {
        const val NOTIF_CHANNEL_ID = "hibernation_service_channel"
        const val NOTIF_CHANNEL_ID_GENERAL = "hibernation_general_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * Creates notification channels required for Android 8.0+ (API 26+).
     * Without this, foreground service notifications would fail silently.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Channel for foreground service shown during active hibernation
            val serviceChannel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW  // LOW = no sound, minimal interruption
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }

            // Channel for general notifications (schedule reminders, completion)
            val generalChannel = NotificationChannel(
                NOTIF_CHANNEL_ID_GENERAL,
                "Hibernator Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Scheduled hibernation reminders and results"
            }

            manager.createNotificationChannels(listOf(serviceChannel, generalChannel))
        }
    }
}
