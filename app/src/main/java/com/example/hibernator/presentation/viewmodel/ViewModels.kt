package com.example.hibernator.presentation.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hibernator.accessibility.HibernatorAccessibilityService
import com.example.hibernator.domain.model.*
import com.example.hibernator.domain.repository.*
import com.example.hibernator.domain.usecase.*
import com.example.hibernator.utils.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ============================================================
// Apps Screen ViewModel
// ============================================================

data class AppUiItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val isSelected: Boolean,
    val isWhitelisted: Boolean,
    val lastUsed: Long,
    val usageDuration: Long,
    val icon: Drawable? = null
)

data class AppsUiState(
    val apps: List<AppUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val showSystemApps: Boolean = false,
    val sortOrder: AppSortOrder = AppSortOrder.NAME_ASC,
    val searchQuery: String = "",
    val selectedCount: Int = 0,
    val automationState: AutomationState = AutomationState.Idle,
    val error: String? = null
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val exclusionRepository: ExclusionRepository,
    private val selectedAppsRepository: SelectedAppsRepository,
    private val toggleAppSelectionUseCase: ToggleAppSelectionUseCase,
    private val clearSelectionUseCase: ClearSelectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState(isLoading = true))
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        // Observe automation state from accessibility service
        viewModelScope.launch {
            HibernatorAccessibilityService.automationStateFlow.collect { state ->
                _uiState.update { it.copy(automationState = state) }
            }
        }

        // Observe selected apps
        viewModelScope.launch {
            selectedAppsRepository.getSelectedPackageNames().collect { selected ->
                _uiState.update { state ->
                    state.copy(
                        selectedCount = selected.size,
                        apps = state.apps.map { app ->
                            app.copy(isSelected = app.packageName in selected)
                        }
                    )
                }
            }
        }

        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val includeSystem = _uiState.value.showSystemApps
                val exclusions = exclusionRepository.getAllExclusions().first()
                    .map { it.packageName }.toSet()

                val apps = withContext(Dispatchers.IO) {
                    appRepository.getInstalledApps(includeSystem)
                }

                val selected = selectedAppsRepository.getSelectedPackageNames().first()

                val uiItems = withContext(Dispatchers.IO) {
                    apps.map { app ->
                        val icon = try {
                            context.packageManager.getApplicationIcon(app.packageName)
                        } catch (e: PackageManager.NameNotFoundException) { null }

                        AppUiItem(
                            packageName = app.packageName,
                            appName = app.appName,
                            isSystemApp = app.isSystemApp,
                            isSelected = app.packageName in selected,
                            isWhitelisted = app.packageName in exclusions,
                            lastUsed = app.lastUsed,
                            usageDuration = app.usageDuration,
                            icon = icon
                        )
                    }.sortedBy { it.appName }
                }

                _uiState.update { it.copy(apps = uiItems, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleAppSelection(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            toggleAppSelectionUseCase(packageName, selected)
        }
    }

    fun toggleSystemApps() {
        _uiState.update { it.copy(showSystemApps = !it.showSystemApps) }
        loadApps()
    }

    fun setSortOrder(order: AppSortOrder) {
        _uiState.update { state ->
            val sorted = when (order) {
                AppSortOrder.NAME_ASC -> state.apps.sortedBy { it.appName }
                AppSortOrder.NAME_DESC -> state.apps.sortedByDescending { it.appName }
                AppSortOrder.USAGE_DESC -> state.apps.sortedByDescending { it.usageDuration }
                AppSortOrder.RECENTLY_USED -> state.apps.sortedByDescending { it.lastUsed }
            }
            state.copy(sortOrder = order, apps = sorted)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearSelection() {
        viewModelScope.launch { clearSelectionUseCase() }
    }

    fun selectAll() {
        viewModelScope.launch {
            val currentApps = _uiState.value.apps
            currentApps.forEach { app ->
                selectedAppsRepository.addSelected(app.packageName)
            }
        }
    }

    /** Returns the filtered + searched list from the full list */
    fun getFilteredApps(): List<AppUiItem> {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        return if (query.isBlank()) state.apps
        else state.apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
        }
    }
}

// ============================================================
// Logs Screen ViewModel
// ============================================================

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val getLogsUseCase: GetLogsUseCase,
    private val clearLogsUseCase: ClearLogsUseCase
) : ViewModel() {

    val logs: StateFlow<List<HibernateLog>> = getLogsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearLogs() {
        viewModelScope.launch { clearLogsUseCase() }
    }
}

// ============================================================
// Whitelist Screen ViewModel
// ============================================================

@HiltViewModel
class WhitelistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getExclusionsUseCase: GetExclusionsUseCase,
    private val addExclusionUseCase: AddExclusionUseCase,
    private val removeExclusionUseCase: RemoveExclusionUseCase,
    private val appRepository: AppRepository
) : ViewModel() {

    val exclusions: StateFlow<List<ExcludedApp>> = getExclusionsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeExclusion(packageName: String) {
        viewModelScope.launch { removeExclusionUseCase(packageName) }
    }

    fun addExclusion(packageName: String, appName: String) {
        viewModelScope.launch {
            addExclusionUseCase(ExcludedApp(
                packageName = packageName,
                appName = appName,
                reason = ExclusionReason.USER_ADDED
            ))
        }
    }
}

// ============================================================
// Schedule Screen ViewModel
// ============================================================

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val addScheduleUseCase: AddScheduleUseCase,
    private val deleteScheduleUseCase: DeleteScheduleUseCase
) : ViewModel() {

    val schedules: StateFlow<List<HibernateSchedule>> = getSchedulesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSchedule(schedule: HibernateSchedule) {
        viewModelScope.launch { addScheduleUseCase(schedule) }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch { deleteScheduleUseCase(id) }
    }
}

// ============================================================
// Settings ViewModel
// ============================================================

data class SettingsUiState(
    val isAccessibilityEnabled: Boolean = false,
    val isUsageStatsGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val showSystemApps: Boolean = false,
    val isDebugMode: Boolean = false,
    val actionDelayMs: Long = 800L,
    val maxRetries: Int = 3
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionStates()
    }

    fun refreshPermissionStates() {
        _uiState.update {
            it.copy(
                isAccessibilityEnabled = PermissionChecker.isAccessibilityServiceEnabled(
                    context,
                    HibernatorAccessibilityService::class.java
                ),
                isUsageStatsGranted = PermissionChecker.hasUsageStatsPermission(context),
                isBatteryOptimizationIgnored = PermissionChecker.isIgnoringBatteryOptimizations(context)
            )
        }
    }

    fun setDebugMode(enabled: Boolean) {
        com.example.hibernator.utils.AutomationLogger.isDebugMode = enabled
        _uiState.update { it.copy(isDebugMode = enabled) }
    }

    fun setActionDelay(ms: Long) {
        HibernatorAccessibilityService.actionDelayMs = ms
        _uiState.update { it.copy(actionDelayMs = ms) }
    }
}
