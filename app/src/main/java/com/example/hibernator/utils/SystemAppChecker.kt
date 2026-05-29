package com.example.hibernator.utils

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SystemAppChecker
 * =================
 * Determines whether an app is system-critical and should NEVER be force-stopped.
 *
 * SAFETY-CRITICAL: This class is a hard guard against accidentally stopping
 * essential system components that could render the device unusable.
 *
 * Categories of protected apps:
 * 1. Launchers (home screen) — stopping kills the home screen
 * 2. SystemUI — stopping kills status bar, nav bar, notifications
 * 3. Settings — stopping breaks the automation flow itself
 * 4. Phone/Dialer — stopping during a call could drop the call
 * 5. Accessibility services — stopping kills assistive tech users need
 * 6. Our own app — stopping mid-automation would hang the process
 * 7. Known critical packages — hardcoded safety list
 */
@Singleton
class SystemAppChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Returns null if the app is safe to hibernate,
     * or a reason string explaining why it is protected.
     */
    fun isCritical(packageName: String): String? {
        // Hardcoded critical packages
        if (packageName in ALWAYS_PROTECTED_PACKAGES) {
            return "System-critical app (protected)"
        }

        // Check for launcher/home apps dynamically
        if (isLauncherApp(packageName)) {
            return "This is the home launcher — cannot stop"
        }

        // Check prefix patterns for system packages
        val criticalPrefixes = listOf(
            "com.android.systemui",
            "com.android.phone",
            "com.android.server",
            "android.process",
            "com.qualcomm",
            "com.samsung.android.app.telephonyui"
        )
        criticalPrefixes.forEach { prefix ->
            if (packageName.startsWith(prefix)) {
                return "Protected system component"
            }
        }

        return null // Safe to hibernate
    }

    /**
     * Dynamically detects if a package is registered as a launcher/home app.
     */
    private fun isLauncherApp(packageName: String): Boolean {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            val resolveInfos = context.packageManager.queryIntentActivities(
                intent, PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfos.any { it.activityInfo.packageName == packageName }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns known phone/dialer package names.
     * Used to protect the phone app during active calls.
     */
    fun getPhonePackages(): Set<String> = setOf(
        "com.android.phone",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.google.android.dialer",
        "com.miui.phone",
        "com.coloros.phone"
    )

    companion object {
        /**
         * Hardcoded list of packages that must NEVER be force-stopped.
         * Derived from Android internals and OEM research.
         */
        val ALWAYS_PROTECTED_PACKAGES = setOf(
            // Core Android
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.dialer",
            "com.android.server.telecom",
            "com.android.inputmethod.latin",

            // Google core services
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.syncadapters.contacts",

            // Launchers (common)
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.android.launcher",

            // Samsung critical
            "com.samsung.android.app.telephonyui",
            "com.sec.android.app.launcher",
            "com.samsung.android.systemui",

            // Accessibility services (protect all users' accessibility tools)
            "com.google.android.marvin.talkback",
            "com.samsung.accessibility",

            // This app itself
            "com.example.hibernator"
        )
    }
}

/**
 * PermissionChecker
 * Utility for checking runtime and special permissions.
 */
object PermissionChecker {

    /**
     * Checks if the app has been granted Usage Stats access.
     * This is a special permission — not granted at runtime.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
                    as android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if AccessibilityService is currently enabled for this app.
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        return try {
            val expectedComponentName = android.content.ComponentName(context, serviceClass)
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(expectedComponentName.flattenToString())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the app is excluded from battery optimization.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE)
                as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Checks if notification permission is granted (Android 13+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-13, no runtime permission needed
        }
    }
}
