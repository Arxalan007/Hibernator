package com.example.hibernator

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import com.example.hibernator.utils.SystemAppChecker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * SystemAppCheckerTest
 * =====================
 * Unit tests for the critical safety guard that prevents hibernating
 * system-critical apps.
 *
 * These tests verify:
 * 1. Own app is always protected
 * 2. Known critical packages are blocked
 * 3. Launcher apps detected dynamically are blocked
 * 4. Regular user apps are allowed through
 */
class SystemAppCheckerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var checker: SystemAppChecker

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockPackageManager = mock(PackageManager::class.java)
        `when`(mockContext.packageName).thenReturn("com.example.hibernator")
        `when`(mockContext.packageManager).thenReturn(mockPackageManager)

        // Default: no launcher apps
        `when`(mockPackageManager.queryIntentActivities(any(), anyInt()))
            .thenReturn(emptyList())

        checker = SystemAppChecker(mockContext)
    }

    @Test
    fun `own app package is always protected`() {
        val result = checker.isCritical("com.example.hibernator")
        assertNotNull("Own app should be protected", result)
    }

    @Test
    fun `system ui is protected`() {
        val result = checker.isCritical("com.android.systemui")
        assertNotNull("SystemUI should be protected", result)
    }

    @Test
    fun `android package is protected`() {
        val result = checker.isCritical("android")
        assertNotNull("android package should be protected", result)
    }

    @Test
    fun `google play services is protected`() {
        val result = checker.isCritical("com.google.android.gms")
        assertNotNull("GMS should be protected", result)
    }

    @Test
    fun `regular user app is allowed`() {
        val result = checker.isCritical("com.spotify.music")
        assertNull("Spotify should not be protected", result)
    }

    @Test
    fun `twitter is allowed`() {
        val result = checker.isCritical("com.twitter.android")
        assertNull("Twitter should not be protected", result)
    }

    @Test
    fun `launcher app detected dynamically is protected`() {
        // Mock PackageManager to return our test package as a launcher
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.test.launcher"
            }
        }
        `when`(mockPackageManager.queryIntentActivities(any(), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val result = checker.isCritical("com.test.launcher")
        assertNotNull("Dynamically detected launcher should be protected", result)
    }

    @Test
    fun `phone packages are returned`() {
        val phonePackages = checker.getPhonePackages()
        assertTrue(phonePackages.contains("com.android.phone"))
        assertTrue(phonePackages.contains("com.android.dialer"))
    }

    @Test
    fun `qualcomm package prefix is protected`() {
        val result = checker.isCritical("com.qualcomm.atfwd")
        assertNotNull("Qualcomm system component should be protected", result)
    }

    @Test
    fun `random user apps are not protected`() {
        val userApps = listOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.whatsapp",
            "com.netflix.mediaclient",
            "com.amazon.mShop.android.shopping"
        )
        userApps.forEach { pkg ->
            val result = checker.isCritical(pkg)
            assertNull("$pkg should not be protected", result)
        }
    }
}

/**
 * HibernateLogTest
 * =================
 * Tests for domain model serialization and defaults.
 */
class HibernateLogTest {

    @Test
    fun `log defaults to current timestamp`() {
        val before = System.currentTimeMillis()
        val log = com.example.hibernator.domain.model.HibernateLog(
            packageName = "com.test.app",
            appName = "Test App",
            result = com.example.hibernator.domain.model.HibernateResult.SUCCESS
        )
        val after = System.currentTimeMillis()

        assertTrue(log.timestamp in before..after)
    }

    @Test
    fun `excluded app with null expiresAt is permanent`() {
        val excluded = com.example.hibernator.domain.model.ExcludedApp(
            packageName = "com.test.app",
            appName = "Test",
            reason = com.example.hibernator.domain.model.ExclusionReason.USER_ADDED,
            expiresAt = null
        )
        assertNull("Permanent exclusion should have null expiresAt", excluded.expiresAt)
    }

    @Test
    fun `schedule days of week serialization round trip`() {
        val days = setOf(1, 3, 5) // Mon, Wed, Fri
        val serialized = days.joinToString(",")
        val deserialized = serialized.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        assertEquals(days, deserialized)
    }
}

/**
 * AutomationStateTest
 * ====================
 * Tests for the automation state machine sealed class.
 */
class AutomationStateTest {

    @Test
    fun `idle state is distinct`() {
        val state = com.example.hibernator.domain.model.AutomationState.Idle
        assertTrue(state is com.example.hibernator.domain.model.AutomationState.Idle)
    }

    @Test
    fun `processing state carries package info`() {
        val state = com.example.hibernator.domain.model.AutomationState.Processing(
            packageName = "com.test.app",
            appName = "Test App",
            index = 2,
            total = 5
        )
        assertEquals("com.test.app", state.packageName)
        assertEquals(2, state.index)
        assertEquals(5, state.total)
    }

    @Test
    fun `completed state is terminal`() {
        val state = com.example.hibernator.domain.model.AutomationState.Completed
        assertTrue(state is com.example.hibernator.domain.model.AutomationState.Completed)
    }
}
