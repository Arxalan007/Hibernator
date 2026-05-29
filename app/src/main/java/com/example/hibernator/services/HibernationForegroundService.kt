package com.example.hibernator.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.hibernator.HibernatorApp
import com.example.hibernator.R
import com.example.hibernator.accessibility.HibernatorAccessibilityService
import com.example.hibernator.domain.model.AutomationState
import com.example.hibernator.domain.model.HibernateLog
import com.example.hibernator.domain.model.HibernateResult
import com.example.hibernator.domain.repository.LogRepository
import com.example.hibernator.domain.repository.SelectedAppsRepository
import com.example.hibernator.domain.usecase.CheckHibernateSafetyUseCase
import com.example.hibernator.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class HibernationForegroundService : Service() {

    @Inject lateinit var logRepository: LogRepository
    @Inject lateinit var selectedAppsRepository: SelectedAppsRepository
    @Inject lateinit var checkHibernateSafetyUseCase: CheckHibernateSafetyUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val NOTIF_ID = 1001
        const val EXTRA_PACKAGES = "extra_packages"
        const val EXTRA_APP_NAMES = "extra_app_names"

        fun startService(context: Context, packages: List<String>, names: List<String>) {
            val intent = Intent(context, HibernationForegroundService::class.java).apply {
                putStringArrayListExtra(EXTRA_PACKAGES, ArrayList(packages))
                putStringArrayListExtra(EXTRA_APP_NAMES, ArrayList(names))
            }
            context.startForegroundService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packages = intent?.getStringArrayListExtra(EXTRA_PACKAGES) ?: emptyList<String>()
        val names = intent?.getStringArrayListExtra(EXTRA_APP_NAMES) ?: emptyList<String>()

        startForeground(NOTIF_ID, buildNotification("Starting hibernation…", 0, packages.size))

        serviceScope.launch {
            runHibernation(packages.zip(names))
        }

        return START_NOT_STICKY
    }

    private suspend fun runHibernation(appsToProcess: List<Pair<String, String>>) {
        if (!HibernatorAccessibilityService.isRunning()) {
            logResult("System", "Accessibility", HibernateResult.FAILED,
                "Accessibility service not enabled")
            stopSelf()
            return
        }

        val safeQueue = mutableListOf<Pair<String, String>>()
        appsToProcess.forEachIndexed { index, (pkg, name) ->
            updateNotification("Checking: $name", index, appsToProcess.size)
            val skipReason = checkHibernateSafetyUseCase.check(pkg)
            if (skipReason != null) {
                logResult(pkg, name, HibernateResult.SKIPPED, skipReason)
            } else {
                safeQueue.add(pkg to name)
            }
        }

        if (safeQueue.isEmpty()) {
            stopSelf()
            return
        }

        val observerJob = serviceScope.launch {
            HibernatorAccessibilityService.automationStateFlow.collect { state ->
                when (state) {
                    is AutomationState.Success ->
                        logResult(state.packageName, state.appName, HibernateResult.SUCCESS, "")
                    is AutomationState.Failed ->
                        logResult(state.packageName, state.packageName, HibernateResult.FAILED, state.reason)
                    is AutomationState.Skipped ->
                        logResult(state.packageName, state.packageName, HibernateResult.SKIPPED, state.reason)
                    is AutomationState.Completed -> {
                        this.cancel()
                        delay(1000)
                        stopSelf()
                    }
                    else -> {}
                }
            }
        }

        HibernatorAccessibilityService.startAutomation(safeQueue)

        val maxTotalMs = safeQueue.size * 15_000L
        delay(maxTotalMs)
        observerJob.cancel()
        stopSelf()
    }

    private suspend fun logResult(
        packageName: String, appName: String,
        result: HibernateResult, reason: String
    ) {
        try {
            logRepository.addLog(
                HibernateLog(
                    packageName = packageName,
                    appName = appName,
                    result = result,
                    reason = reason
                )
            )
        } catch (e: Exception) { }
    }

    private fun buildNotification(text: String, current: Int, total: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, HibernatorApp.NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_hibernate)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (total > 0) {
            builder.setProgress(total, current, false)
        }

        return builder.build()
    }

    private fun updateNotification(text: String, current: Int, total: Int) {
        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager.notify(NOTIF_ID, buildNotification(text, current, total))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}