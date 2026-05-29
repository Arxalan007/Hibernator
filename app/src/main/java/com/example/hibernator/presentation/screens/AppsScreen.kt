package com.example.hibernator.presentation.screens

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.hibernator.domain.model.*
import com.example.hibernator.presentation.viewmodel.AppUiItem
import com.example.hibernator.presentation.viewmodel.AppsViewModel
import com.example.hibernator.services.HibernationForegroundService
import com.example.hibernator.utils.PermissionChecker
import com.example.hibernator.accessibility.HibernatorAccessibilityService
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    navController: NavController,
    viewModel: AppsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }
    var showHibernateConfirm by remember { mutableStateOf(false) }

    // Check permissions
    val isAccessibilityEnabled = remember {
        PermissionChecker.isAccessibilityServiceEnabled(
            context, HibernatorAccessibilityService::class.java
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Top App Bar ───
        TopAppBar(
            title = { Text("Hibernator", fontWeight = FontWeight.Bold) },
            actions = {
                // Sort menu
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.Sort, "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    AppSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.displayName()) },
                            onClick = {
                                viewModel.setSortOrder(order)
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (uiState.sortOrder == order) {
                                    Icon(Icons.Default.Check, null)
                                }
                            }
                        )
                    }
                }
                // System apps toggle
                IconButton(onClick = { viewModel.toggleSystemApps() }) {
                    Icon(
                        if (uiState.showSystemApps) Icons.Default.VisibilityOff
                        else Icons.Default.Visibility,
                        "Toggle system apps"
                    )
                }
            }
        )

        // ─── Search Bar ───
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search apps…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )

        // ─── Automation Status Banner ───
        AutomationStatusBanner(state = uiState.automationState)

        // ─── Permission Warning ───
        if (!isAccessibilityEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Accessibility service not enabled. Tap Settings to enable it.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ─── Selection Bar ───
        if (uiState.selectedCount > 0) {
            SelectionBar(
                count = uiState.selectedCount,
                onClear = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                onHibernate = { showHibernateConfirm = true }
            )
        }

        // ─── App List ───
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filteredApps = viewModel.getFilteredApps()
            if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        AppListItem(
                            app = app,
                            onToggleSelect = { selected ->
                                viewModel.toggleAppSelection(app.packageName, selected)
                            }
                        )
                    }
                }
            }
        }
    }

    // ─── Hibernate Confirmation Dialog ───
    if (showHibernateConfirm) {
        AlertDialog(
            onDismissRequest = { showHibernateConfirm = false },
            title = { Text("Hibernate ${uiState.selectedCount} apps?") },
            text = {
                Text(
                    "This will force-stop the selected apps using Android Settings automation. " +
                            "Apps will restart normally when you open them."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showHibernateConfirm = false
                    startHibernation(context, viewModel)
                }) {
                    Text("Hibernate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHibernateConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun startHibernation(context: Context, viewModel: AppsViewModel) {
    val selectedApps = viewModel.uiState.value.apps.filter { it.isSelected }
    if (selectedApps.isEmpty()) return

    HibernationForegroundService.startService(
        context = context,
        packages = selectedApps.map { it.packageName },
        names = selectedApps.map { it.appName }
    )
}

@Composable
fun AutomationStatusBanner(state: AutomationState) {
    AnimatedVisibility(
        visible = state !is AutomationState.Idle,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (state) {
                    is AutomationState.Processing -> MaterialTheme.colorScheme.primaryContainer
                    is AutomationState.Success -> MaterialTheme.colorScheme.secondaryContainer
                    is AutomationState.Failed -> MaterialTheme.colorScheme.errorContainer
                    is AutomationState.Completed -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state) {
                    is AutomationState.Processing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Hibernating: ${state.appName} (${state.index + 1}/${state.total})")
                    }
                    is AutomationState.Success -> {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text("✓ Hibernated: ${state.appName}")
                    }
                    is AutomationState.Failed -> {
                        Icon(Icons.Default.Error, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Failed: ${state.reason}")
                    }
                    is AutomationState.Completed -> {
                        Icon(Icons.Default.Done, null)
                        Spacer(Modifier.width(8.dp))
                        Text("All apps hibernated!")
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onHibernate: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, "Clear selection")
            }
            Text(
                "$count selected",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onSelectAll) {
                Text("All")
            }
            Button(
                onClick = onHibernate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Hibernate")
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppUiItem,
    onToggleSelect: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelect(!app.isSelected) }
            .background(
                if (app.isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon
        app.icon?.let { drawable ->
            val bitmap = remember(drawable) {
                drawable.toBitmap(72, 72).asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = app.appName,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } ?: Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.appName.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.appName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (app.isWhitelisted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Shield,
                        "Whitelisted",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (app.isSystemApp) {
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            "SYS",
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (app.lastUsed > 0) {
                Text(
                    text = "Last used: ${formatTime(app.lastUsed)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Checkbox(
            checked = app.isSelected,
            onCheckedChange = onToggleSelect
        )
    }
}

private fun formatTime(epochMs: Long): String {
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun AppSortOrder.displayName() = when (this) {
    AppSortOrder.NAME_ASC -> "Name (A–Z)"
    AppSortOrder.NAME_DESC -> "Name (Z–A)"
    AppSortOrder.USAGE_DESC -> "Most Used"
    AppSortOrder.RECENTLY_USED -> "Recently Used"
}
