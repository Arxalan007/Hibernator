package com.example.hibernator.presentation.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.hibernator.accessibility.HibernatorAccessibilityService
import com.example.hibernator.domain.model.*
import com.example.hibernator.presentation.viewmodel.*
import com.example.hibernator.utils.PermissionChecker
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
// LOGS SCREEN
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    navController: NavController,
    viewModel: LogsViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Hibernate Logs") },
            actions = {
                if (logs.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, "Clear all logs")
                    }
                }
            }
        )

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No logs yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(logs, key = { it.id }) { log ->
                    LogItem(log = log)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all logs?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLogs()
                    showClearDialog = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun LogItem(log: HibernateLog) {
    val resultColor = when (log.result) {
        HibernateResult.SUCCESS -> Color(0xFF4CAF50)
        HibernateResult.FAILED -> MaterialTheme.colorScheme.error
        HibernateResult.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        HibernateResult.TIMEOUT -> Color(0xFFFF9800)
        HibernateResult.ALREADY_STOPPED -> Color(0xFF2196F3)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (log.result) {
                HibernateResult.SUCCESS -> Icons.Default.CheckCircle
                HibernateResult.FAILED -> Icons.Default.Cancel
                HibernateResult.SKIPPED -> Icons.Default.SkipNext
                HibernateResult.TIMEOUT -> Icons.Default.Timer
                HibernateResult.ALREADY_STOPPED -> Icons.Default.PowerOff
            },
            null,
            tint = resultColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(log.appName, fontWeight = FontWeight.Medium)
            if (log.reason.isNotBlank()) {
                Text(log.reason, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatDateTime(log.timestamp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = resultColor.copy(alpha = 0.15f)
        ) {
            Text(
                log.result.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 11.sp,
                color = resultColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================================
// WHITELIST SCREEN
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(
    navController: NavController,
    viewModel: WhitelistViewModel = hiltViewModel()
) {
    val exclusions by viewModel.exclusions.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Whitelist") })

        if (exclusions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Shield, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No excluded apps",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Apps added here will never be hibernated",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(exclusions, key = { it.packageName }) { exclusion ->
                    ListItem(
                        headlineContent = { Text(exclusion.appName) },
                        supportingContent = { Text(exclusion.reason.name.replace("_", " ")) },
                        trailingContent = {
                            IconButton(onClick = {
                                viewModel.removeExclusion(exclusion.packageName)
                            }) {
                                Icon(Icons.Default.RemoveCircleOutline, "Remove from whitelist",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Default.Shield, null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// ============================================================
// SCHEDULE SCREEN
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    navController: NavController,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Schedules") },
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add schedule")
                }
            }
        )

        if (schedules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Schedule, null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No schedules", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Schedule")
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleItem(
                        schedule = schedule,
                        onDelete = { viewModel.deleteSchedule(schedule.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { hour, minute, type, label ->
                viewModel.addSchedule(HibernateSchedule(
                    label = label,
                    type = type,
                    hour = hour,
                    minute = minute
                ))
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ScheduleItem(schedule: HibernateSchedule, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(schedule.label) },
        supportingContent = {
            Text("${"%02d".format(schedule.hour)}:${"%02d".format(schedule.minute)} · ${schedule.type.name}")
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete schedule",
                    tint = MaterialTheme.colorScheme.error)
            }
        },
        leadingContent = {
            Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
        }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, type: ScheduleType, label: String) -> Unit
) {
    var label by remember { mutableStateOf("Daily Hibernate") }
    var hour by remember { mutableStateOf(22) }
    var minute by remember { mutableStateOf(0) }
    var type by remember { mutableStateOf(ScheduleType.DAILY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label") }, singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour.toString(),
                        onValueChange = { it.toIntOrNull()?.coerceIn(0, 23)?.let { v -> hour = v } },
                        label = { Text("Hour") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = minute.toString(),
                        onValueChange = { it.toIntOrNull()?.coerceIn(0, 59)?.let { v -> minute = v } },
                        label = { Text("Min") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                // Schedule type selector
                ScheduleType.entries.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Text(t.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute, type, label) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ============================================================
// SETTINGS SCREEN
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshPermissionStates() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })

        LazyColumn {
            // ── Permissions section ──
            item {
                SettingsSectionHeader("Permissions")
                PermissionRow(
                    title = "Accessibility Service",
                    subtitle = "Required for Force Stop automation",
                    isGranted = state.isAccessibilityEnabled,
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )
                PermissionRow(
                    title = "Usage Access",
                    subtitle = "Required for app usage statistics",
                    isGranted = state.isUsageStatsGranted,
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                )
                PermissionRow(
                    title = "Battery Optimization",
                    subtitle = "Ensures schedules fire reliably",
                    isGranted = state.isBatteryOptimizationIgnored,
                    onGrant = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // ── Tuning section ──
            item {
                SettingsSectionHeader("Automation Tuning")
                ListItem(
                    headlineContent = { Text("Action Delay") },
                    supportingContent = { Text("${state.actionDelayMs}ms between clicks") },
                    trailingContent = {
                        Slider(
                            value = state.actionDelayMs.toFloat(),
                            onValueChange = { viewModel.setActionDelay(it.toLong()) },
                            valueRange = 300f..2000f,
                            steps = 16,
                            modifier = Modifier.width(140.dp)
                        )
                    }
                )
            }

            // ── Debug section ──
            item {
                SettingsSectionHeader("Developer")
                ListItem(
                    headlineContent = { Text("Debug Mode") },
                    supportingContent = { Text("Show verbose accessibility logs") },
                    trailingContent = {
                        Switch(
                            checked = state.isDebugMode,
                            onCheckedChange = viewModel::setDebugMode
                        )
                    }
                )
            }

            // ── Privacy notice ──
            item {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Privacy", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Hibernator is fully offline. No data is collected, uploaded, " +
                                    "or shared. All data stays on your device. No internet permission " +
                                    "is declared.",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                null,
                tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        },
        trailingContent = {
            if (!isGranted) {
                TextButton(onClick = onGrant) { Text("Grant") }
            }
        }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ============================================================
// PERMISSIONS ONBOARDING SCREEN
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(navController: NavController) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            Icons.Default.Security,
            null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Setup Required", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Hibernator needs a few permissions to work. All data stays on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        PermissionCard(
            icon = Icons.Default.Accessibility,
            title = "Accessibility Service",
            description = "Automates Force Stop inside Android Settings. " +
                    "Scoped only to Settings app — cannot read your content.",
            action = "Enable",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        )
        Spacer(Modifier.height(12.dp))
        PermissionCard(
            icon = Icons.Default.BarChart,
            title = "Usage Access",
            description = "Shows app usage statistics so you can sort by most used.",
            action = "Enable",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { navController.navigate("apps") { popUpTo("permissions") { inclusive = true } } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, description: String,
    action: String, onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(description, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

// ============================================================
// USAGE STATS SCREEN
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Usage Statistics") })
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Usage stats coming soon",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Helpers ──

private fun formatDateTime(epochMs: Long): String =
    SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
