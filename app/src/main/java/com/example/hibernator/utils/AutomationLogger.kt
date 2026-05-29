package com.example.hibernator.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AutomationLogger
 * =================
 * In-memory logger for accessibility automation events.
 * Stores the last N log entries for debugging.
 *
 * Privacy note: Logs contain only package names, button states,
 * and timing information. No user content is ever logged.
 */
object AutomationLogger {
    private const val TAG = "HibernatorAutomation"
    private const val MAX_LOG_ENTRIES = 200

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val message: String
    ) {
        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                .format(Date(timestamp))
    }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    var isDebugMode = false

    fun log(message: String) {
        if (isDebugMode) {
            Log.d(TAG, message)
        }
        val entry = LogEntry(message = message)
        val current = _logs.value.toMutableList()
        current.add(0, entry) // Newest first
        if (current.size > MAX_LOG_ENTRIES) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
