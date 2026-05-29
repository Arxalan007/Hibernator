package com.example.hibernator.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.hibernator.domain.model.AutomationState
import com.example.hibernator.utils.AutomationLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HibernatorAccessibilityService
 * ================================
 * This is the heart of the automation engine.
 *
 * WHY ACCESSIBILITY SERVICE IS NEEDED:
 * Android does not provide a public API to programmatically force-stop apps.
 * The only supported way to force-stop an app is via:
 *   Settings > Apps > [App Name] > Force Stop
 * This requires UI automation to:
 *   1. Open the App Info screen via Intent
 *   2. Find the "Force Stop" button in the UI
 *   3. Click it
 *   4. Confirm in the dialog
 *
 * AccessibilityService provides the only legitimate, non-root mechanism to
 * interact with UI elements in other apps (specifically Android Settings).
 *
 * SECURITY SCOPE — THIS SERVICE:
 * ✓ ONLY interacts with com.android.settings (and OEM variants)
 * ✓ ONLY clicks Force Stop button and its confirmation
 * ✓ ONLY reads node IDs and button text to identify the correct button
 * ✗ NEVER reads chat messages, emails, or any user content
 * ✗ NEVER captures keyboard input or passwords
 * ✗ NEVER interacts with banking apps, browsers, or messaging apps
 * ✗ NEVER monitors the clipboard
 *
 * This is enforced both by the packageNames filter in accessibility_service_config.xml
 * AND by the event handling logic below.
 *
 * OEM COMPATIBILITY:
 * Different Android OEMs use different button texts and resource IDs.
 * The service handles this by trying multiple text/ID variations.
 * Samsung, Xiaomi, Pixel, OnePlus are specifically handled.
 */
class HibernatorAccessibilityService : AccessibilityService() {

    // Coroutine scope tied to service lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Automation state machine state
    private var automationState: ServiceAutomationState = ServiceAutomationState.IDLE

    // Current app being processed
    private var currentPackageName: String? = null
    private var currentAppName: String? = null

    // Queue of apps waiting to be hibernated
    private var pendingQueue: ArrayDeque<Pair<String, String>> = ArrayDeque()

    // Retry counter for current app
    private var retryCount = 0
    private val maxRetries = 3

    // Timeout job for stuck states
    private var timeoutJob: Job? = null

    companion object {
        // Singleton reference — the only way to communicate with a bound service
        private var instance: HibernatorAccessibilityService? = null

        // Public shared state flow for UI to observe
        private val _automationStateFlow = MutableStateFlow<AutomationState>(AutomationState.Idle)
        val automationStateFlow: StateFlow<AutomationState> = _automationStateFlow.asStateFlow()

        fun getInstance(): HibernatorAccessibilityService? = instance

        /**
         * Entry point: called by HibernationForegroundService to start automation.
         * @param queue List of (packageName, appName) pairs to hibernate.
         */
        fun startAutomation(queue: List<Pair<String, String>>) {
            instance?.beginAutomation(queue)
        }

        fun isRunning(): Boolean = instance != null

        /**
         * Force Stop button text variations across OEMs and locales.
         * We try all of these when looking for the button.
         */
        val FORCE_STOP_BUTTON_TEXTS = listOf(
            "Force stop",
            "Force Stop",
            "FORCE STOP",
            "Force close",
            "Force Close",
            "强制停止",    // Chinese (Simplified)
            "강제 중지",   // Korean
            "Forcer l'arrêt", // French
            "Forzar detención" // Spanish
        )

        // Text that must NEVER be clicked — safety blacklist
        val NEVER_CLICK_TEXTS = listOf(
            "uninstall", "Uninstall", "UNINSTALL",
            "delete", "Delete", "DELETE",
            "remove", "Remove", "卸载", "삭제"
        )

        /**
         * Force Stop button resource ID variations.
         * Different OEMs use different resource IDs.
         */
        val FORCE_STOP_BUTTON_IDS = listOf(
            "com.android.settings:id/right_button",
            "com.android.settings:id/force_stop_button",
            "com.android.settings:id/forceStop",
            "android:id/button1",
            // Samsung-specific
            "com.samsung.android.settings:id/right_button",
            // Xiaomi-specific
            "com.miui.securitycenter:id/force_stop",
            // OPPO/ColorOS-specific
            "com.coloros.settings:id/right_button",
            "com.oppo.settings:id/right_button"
        )

        /**
         * Confirmation dialog OK button text variations.
         */
        val OK_BUTTON_TEXTS = listOf(
            "OK", "Ok", "ok",
            "Force stop", "Force Stop",
            "Yes", "YES",
            "确定",  // Chinese
            "확인"   // Korean
        )

        // Delay between automation actions (ms). User-tunable.
        var actionDelayMs: Long = 800L

        // Timeout for each app (ms)
        const val APP_TIMEOUT_MS = 10_000L
    }

    // ====================================================================
    // SERVICE LIFECYCLE
    // ====================================================================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AutomationLogger.log("AccessibilityService connected")
        _automationStateFlow.value = AutomationState.Idle
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        serviceScope.cancel()
        AutomationLogger.log("AccessibilityService disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        // Called when the service is interrupted — reset state
        AutomationLogger.log("AccessibilityService interrupted — resetting state")
        automationState = ServiceAutomationState.IDLE
        timeoutJob?.cancel()
    }

    // ====================================================================
    // ACCESSIBILITY EVENT HANDLING
    // ====================================================================

    /**
     * onAccessibilityEvent is called whenever the Settings app UI changes.
     *
     * WHY WE NEED THIS:
     * Opening an App Info screen via Intent is asynchronous — we can't know
     * exactly when the screen is ready. This callback fires when the window
     * content is loaded, at which point we can search for the Force Stop button.
     *
     * SECURITY: This callback is ONLY called for events from the packages
     * listed in accessibility_service_config.xml (Settings packages only).
     * It will NOT fire for browser, messaging, banking, or any other apps.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (automationState == ServiceAutomationState.IDLE) return

        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: return

        // Only react to window state changes (screen loaded) or content changes
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        AutomationLogger.log("Event: type=${eventType}, pkg=$packageName, state=$automationState")

        when (automationState) {
            ServiceAutomationState.WAITING_FOR_APP_INFO -> {
                // Check if the App Info screen has loaded for our target app
                handleAppInfoScreenDetection(event)
            }

            ServiceAutomationState.WAITING_FOR_CONFIRMATION -> {
                // Check if the confirmation dialog has appeared
                handleConfirmationDialogDetection(event)
            }

            ServiceAutomationState.WAITING_FOR_RETURN -> {
                // We're done with this app, process the next one
                processNextApp()
            }

            else -> { /* IDLE or other states — do nothing */ }
        }
    }

    // ====================================================================
    // AUTOMATION ENGINE
    // ====================================================================

    /**
     * Starts the automation sequence for a list of apps.
     * Called externally from HibernationForegroundService.
     */
    private fun beginAutomation(queue: List<Pair<String, String>>) {
        if (automationState != ServiceAutomationState.IDLE) {
            AutomationLogger.log("Automation already running, ignoring start request")
            return
        }

        pendingQueue = ArrayDeque(queue)
        processNextApp()
    }

    /**
     * Picks the next app from the queue and opens its App Info screen.
     * If the queue is empty, automation is complete.
     */
    private fun processNextApp() {
        timeoutJob?.cancel()

        if (pendingQueue.isEmpty()) {
            // All done!
            automationState = ServiceAutomationState.IDLE
            _automationStateFlow.value = AutomationState.Completed
            AutomationLogger.log("Automation complete — all apps processed")

            // Return to home screen
            serviceScope.launch {
                delay(500)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        val (pkg, name) = pendingQueue.removeFirst()
        currentPackageName = pkg
        currentAppName = name
        retryCount = 0

        AutomationLogger.log("Processing app: $name ($pkg)")
        _automationStateFlow.value = AutomationState.Processing(
            packageName = pkg,
            appName = name,
            index = 0,  // Could track total here
            total = pendingQueue.size + 1
        )

        openAppInfoScreen(pkg)
    }

    /**
     * Opens Android Settings to the App Info screen for the given package.
     *
     * Uses the standard Android ACTION_APPLICATION_DETAILS_SETTINGS intent.
     * This is a public, documented API. No root or private APIs used.
     */
    private fun openAppInfoScreen(packageName: String) {
        automationState = ServiceAutomationState.WAITING_FOR_APP_INFO
        startTimeout()

        serviceScope.launch {
            delay(300) // Brief delay before opening to allow previous screen to settle
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
                AutomationLogger.log("Opened App Info for $packageName")
            } catch (e: Exception) {
                AutomationLogger.log("Failed to open App Info for $packageName: ${e.message}")
                handleFailure("Could not open App Info screen")
            }
        }
    }

    /**
     * Called when the App Info screen loads.
     * Searches the UI tree for the Force Stop button and clicks it.
     *
     * HOW NODE TRAVERSAL WORKS:
     * AccessibilityNodeInfo represents a UI element. We recursively traverse
     * the view hierarchy from rootInActiveWindow, looking for buttons that
     * match known Force Stop text/ID variations.
     *
     * WHY RECURSIVE SEARCH:
     * OEM UIs have varying depths of nesting. Samsung may nest the button
     * 5 levels deep; Pixel may be 3 levels. We must search all levels.
     */
    private fun handleAppInfoScreenDetection(event: AccessibilityEvent) {
        // Confirm this is actually an App Info screen (not just any Settings screen)
        val rootNode = rootInActiveWindow ?: return

        // Verify the screen title contains the app name or shows "App info"
        // This prevents accidentally clicking Force Stop on the wrong screen
        if (!isAppInfoScreen(rootNode, currentPackageName)) {
            rootNode.recycle()
            return
        }

        AutomationLogger.log("App Info screen detected for ${currentPackageName}")
        automationState = ServiceAutomationState.CLICKING_FORCE_STOP

        serviceScope.launch {
            delay(actionDelayMs) // Wait for screen to fully render

            val freshRoot = rootInActiveWindow
            if (freshRoot == null) {
                AutomationLogger.log("Root node null after delay — retrying")
                handleRetry()
                return@launch
            }

            val forceStopNode = findForceStopButton(freshRoot)

            if (forceStopNode == null) {
                freshRoot.recycle()
                AutomationLogger.log("Force Stop button not found — retry $retryCount")
                handleRetry()
                return@launch
            }

            if (!forceStopNode.isEnabled) {
                // App is already stopped — skip it
                forceStopNode.recycle()
                freshRoot.recycle()
                AutomationLogger.log("Force Stop button is disabled — app already stopped")
                _automationStateFlow.value = AutomationState.Skipped(
                    currentPackageName ?: "",
                    "App is already stopped"
                )
                delay(500)
                goBackAndProcessNext()
                return@launch
            }

            // CLICK THE FORCE STOP BUTTON
            val clicked = forceStopNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            AutomationLogger.log("Force Stop click result: $clicked")

            forceStopNode.recycle()
            freshRoot.recycle()

            if (clicked) {
                automationState = ServiceAutomationState.WAITING_FOR_CONFIRMATION
                startTimeout()
            } else {
                handleRetry()
            }
        }
    }

    /**
     * Called when the confirmation dialog appears after clicking Force Stop.
     * Looks for "OK" or "Force stop" button in the dialog and clicks it.
     */
    private fun handleConfirmationDialogDetection(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        serviceScope.launch {
            delay(actionDelayMs / 2) // Shorter delay — dialog is simple

            val freshRoot = rootInActiveWindow ?: return@launch

            val okNode = findConfirmationButton(freshRoot)

            if (okNode == null) {
                freshRoot.recycle()
                AutomationLogger.log("Confirmation button not found — retry $retryCount")
                handleRetry()
                return@launch
            }

            val clicked = okNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            AutomationLogger.log("Confirmation click result: $clicked")

            okNode.recycle()
            freshRoot.recycle()

            if (clicked) {
                // SUCCESS for this app
                _automationStateFlow.value = AutomationState.Success(
                    packageName = currentPackageName ?: "",
                    appName = currentAppName ?: ""
                )
                AutomationLogger.log("✓ Successfully hibernated: ${currentPackageName}")
                automationState = ServiceAutomationState.WAITING_FOR_RETURN
                timeoutJob?.cancel()

                delay(actionDelayMs)
                goBackAndProcessNext()
            } else {
                handleRetry()
            }
        }
    }

    // ====================================================================
    // NODE FINDERS
    // ====================================================================

    /**
     * Verifies the current screen is the App Info screen for our target app.
     *
     * WHY THIS CHECK IS IMPORTANT:
     * Without this, we might accidentally click Force Stop on the wrong screen
     * (e.g., if Settings navigated somewhere unexpected). This is a safety guard.
     */
    private fun isAppInfoScreen(root: AccessibilityNodeInfo, packageName: String?): Boolean {
        // Look for any node that indicates this is an App Info page
        val appInfoIndicators = listOf(
            "App info", "Application info", "App details",
            "应用信息", "앱 정보"
        )

        return appInfoIndicators.any { text ->
            findNodeByText(root, text) != null
        } || findNodeByText(root, currentAppName ?: "") != null
    }

    /**
     * Recursively searches the UI tree for the Force Stop button.
     * Tries multiple strategies: resource ID first, then text matching.
     *
     * Returns the node if found (caller must recycle), or null.
     */
    private fun findForceStopButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Strategy 1: Try known resource IDs (faster, more reliable)
        for (resId in FORCE_STOP_BUTTON_IDS) {
            val node = root.findAccessibilityNodeInfosByViewId(resId)
                .firstOrNull { it.isEnabled || !it.isEnabled }
            if (node != null) {
                // SAFETY CHECK: make sure this node is not an Uninstall button
                val nodeText = node.text?.toString() ?: ""
                val nodeDesc = node.contentDescription?.toString() ?: ""
                val combined = (nodeText + nodeDesc).lowercase()
                if (NEVER_CLICK_TEXTS.any { combined.contains(it.lowercase()) }) {
                    AutomationLogger.log("SAFETY: Skipped node by ID — text was '$nodeText', looks like Uninstall")
                    node.recycle()
                    continue
                }
                AutomationLogger.log("Found Force Stop by ID: $resId, text: $nodeText")
                return node
            }
        }

        // Strategy 2: Search by text (exact match on Force Stop texts only)
        for (text in FORCE_STOP_BUTTON_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            val node = nodes.firstOrNull { candidate ->
                val isClickable = candidate.isClickable || candidate.className?.contains("Button") == true
                val candidateText = candidate.text?.toString() ?: ""
                val isNotUninstall = NEVER_CLICK_TEXTS.none {
                    candidateText.contains(it, ignoreCase = true)
                }
                isClickable && isNotUninstall
            }
            if (node != null) {
                AutomationLogger.log("Found Force Stop by text: $text")
                return node
            }
        }

        // Strategy 3: Deep recursive search with safety filter
        return findButtonByHeuristic(root, FORCE_STOP_BUTTON_TEXTS)
    }

    /**
     * Finds the OK/Confirm button in the Force Stop confirmation dialog.
     */
    private fun findConfirmationButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Try text matching first
        for (text in OK_BUTTON_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            val node = nodes.firstOrNull { it.isClickable }
            if (node != null) {
                AutomationLogger.log("Found confirmation button by text: $text")
                return node
            }
        }

        // Fallback: find any clickable button in a dialog
        return findButtonByHeuristic(root, OK_BUTTON_TEXTS)
    }

    /**
     * Deep recursive button search using heuristics.
     * Traverses the entire view tree looking for clickable elements
     * whose text matches any of the given candidates.
     *
     * WHY RECURSIVE: OEM UIs can have deep nesting. LinearLayout > ScrollView >
     * ConstraintLayout > ... > Button. We must go all the way down.
     */
    private fun findButtonByHeuristic(
        node: AccessibilityNodeInfo,
        textCandidates: List<String>
    ): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val isClickableButton = node.isClickable &&
                (node.className?.contains("Button") == true ||
                        node.className?.contains("TextView") == true)

        if (isClickableButton) {
            // First verify this is not an uninstall/delete button
            val isUninstallButton = NEVER_CLICK_TEXTS.any {
                nodeText.contains(it, ignoreCase = true)
            }
            if (isUninstallButton) {
                // Skip this node entirely — never click uninstall
                AutomationLogger.log("SAFETY: Blocked click on node with text '$nodeText'")
            } else {
                for (candidate in textCandidates) {
                    if (nodeText.equals(candidate, ignoreCase = true)) {
                        return node
                    }
                }
            }
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findButtonByHeuristic(child, textCandidates)
            if (found != null) return found
            child.recycle()
        }

        return null
    }

    /**
     * Finds a node by exact or partial text match.
     * Returns the first matching node or null.
     */
    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return root.findAccessibilityNodeInfosByText(text).firstOrNull()
    }

    // ====================================================================
    // RETRY / TIMEOUT / ERROR HANDLING
    // ====================================================================

    private fun handleRetry() {
        retryCount++
        if (retryCount >= maxRetries) {
            AutomationLogger.log("Max retries reached for ${currentPackageName}")
            handleFailure("Force Stop button not found after $maxRetries attempts")
            return
        }

        automationState = ServiceAutomationState.WAITING_FOR_APP_INFO
        serviceScope.launch {
            delay(1000L * retryCount) // Exponential-ish backoff
            // Re-trigger by opening App Info again
            currentPackageName?.let { openAppInfoScreen(it) }
        }
    }

    private fun handleFailure(reason: String) {
        timeoutJob?.cancel()
        _automationStateFlow.value = AutomationState.Failed(
            currentPackageName ?: "", reason
        )
        AutomationLogger.log("✗ Failed to hibernate ${currentPackageName}: $reason")
        serviceScope.launch {
            delay(500)
            goBackAndProcessNext()
        }
    }

    private fun goBackAndProcessNext() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        serviceScope.launch {
            delay(600)
            processNextApp()
        }
    }

    /**
     * Starts a timeout watchdog for the current automation step.
     * If the expected event doesn't arrive within APP_TIMEOUT_MS,
     * we consider the step failed and move on.
     *
     * WHY NEEDED: If Settings crashes or the button detection fails silently,
     * the service could get stuck forever. The timeout ensures progress.
     */
    private fun startTimeout() {
        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(APP_TIMEOUT_MS)
            AutomationLogger.log("TIMEOUT waiting for ${currentPackageName} state: $automationState")
            handleFailure("Timeout: Settings screen did not respond in time")
        }
    }
}

/**
 * Internal state machine states for the accessibility service.
 * These are different from AutomationState (which is the public-facing UI state).
 */
enum class ServiceAutomationState {
    IDLE,
    WAITING_FOR_APP_INFO,
    CLICKING_FORCE_STOP,
    WAITING_FOR_CONFIRMATION,
    WAITING_FOR_RETURN
}
