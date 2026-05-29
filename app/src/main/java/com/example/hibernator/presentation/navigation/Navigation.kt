package com.example.hibernator.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hibernator.presentation.screens.*

sealed class Screen(val route: String) {
    object Apps : Screen("apps")
    object Whitelist : Screen("whitelist")
    object Schedule : Screen("schedule")
    object Logs : Screen("logs")
    object UsageStats : Screen("usage_stats")
    object Settings : Screen("settings")
    object Permissions : Screen("permissions")
}

@Composable
fun HibernatorNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Apps.route
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Apps.route) {
            AppsScreen(navController = navController)
        }
        composable(Screen.Whitelist.route) {
            WhitelistScreen(navController = navController)
        }
        composable(Screen.Schedule.route) {
            ScheduleScreen(navController = navController)
        }
        composable(Screen.Logs.route) {
            LogsScreen(navController = navController)
        }
        composable(Screen.UsageStats.route) {
            UsageStatsScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(navController = navController)
        }
    }
}
