package com.example.hibernator.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore — creates a single instance per process
private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "hibernator_prefs")

/**
 * AppPreferences
 * ================
 * Persists user settings using Jetpack DataStore (replaces SharedPreferences).
 * All settings are stored locally — no sync, no cloud.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val KEY_ACTION_DELAY_MS = longPreferencesKey("action_delay_ms")
        val KEY_MAX_RETRIES = intPreferencesKey("max_retries")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    }

    // ── Flows (reactive reads) ──

    val showSystemApps: Flow<Boolean> = context.dataStore.data
        .catchIOException()
        .map { prefs -> prefs[KEY_SHOW_SYSTEM_APPS] ?: false }

    val isDebugMode: Flow<Boolean> = context.dataStore.data
        .catchIOException()
        .map { prefs -> prefs[KEY_DEBUG_MODE] ?: false }

    val actionDelayMs: Flow<Long> = context.dataStore.data
        .catchIOException()
        .map { prefs -> prefs[KEY_ACTION_DELAY_MS] ?: 800L }

    val maxRetries: Flow<Int> = context.dataStore.data
        .catchIOException()
        .map { prefs -> prefs[KEY_MAX_RETRIES] ?: 3 }

    val sortOrder: Flow<String> = context.dataStore.data
        .catchIOException()
        .map { prefs -> prefs[KEY_SORT_ORDER] ?: "NAME_ASC" }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .catchIOException()
        .map { prefs -> prefs[KEY_FIRST_LAUNCH] ?: true }

    // ── Writes ──

    suspend fun setShowSystemApps(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SHOW_SYSTEM_APPS] = value }
    }

    suspend fun setDebugMode(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DEBUG_MODE] = value }
    }

    suspend fun setActionDelayMs(value: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_ACTION_DELAY_MS] = value }
    }

    suspend fun setMaxRetries(value: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_MAX_RETRIES] = value }
    }

    suspend fun setSortOrder(value: String) {
        context.dataStore.edit { prefs -> prefs[KEY_SORT_ORDER] = value }
    }

    suspend fun setFirstLaunchDone() {
        context.dataStore.edit { prefs -> prefs[KEY_FIRST_LAUNCH] = false }
    }

    // ── Helper: catches IOException from DataStore reads ──
    private fun Flow<Preferences>.catchIOException(): Flow<Preferences> =
        catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
}
