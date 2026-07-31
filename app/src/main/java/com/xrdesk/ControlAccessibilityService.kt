package com.xrdesk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.KeyEvent
import android.media.AudioManager
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine

class ControlAccessibilityService : AccessibilityService() {

    companion object {
        private const val WARMUP_MIN_INTERVAL_MS = 15_000L
        private const val ATTACH_RETRY_DELAY_MS = 250L
        private const val ATTACH_RETRY_MAX = 8
        private const val FOCUS_NUDGE_DISTANCE_DP = 8f
        private const val FOCUS_NUDGE_DURATION_MS = 56L
        private const val DEBUG = true
        @Volatile
        private var instance: ControlAccessibilityService? = null
        @Volatile
        private var pendingDisplayInfo: DisplaySessionManager.ExternalDisplayInfo? = null

        fun current(): ControlAccessibilityService? = instance

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
            if (enabled != 1) return false
            val component = ComponentName(context, ControlAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(component.flattenToString()) == true
        }

        fun requestAttachToDisplay(info: DisplaySessionManager.ExternalDisplayInfo?) {
            pendingDisplayInfo = info
            instance?.attachToDisplay(info)
        }

        fun requestDetachOverlay() {
            instance?.detachOverlay()
        }

        fun requestCursorAppearanceRefresh() {
            instance?.refreshCursorAppearance()
        }

        fun requestCursorForceVisible(enabled: Boolean) {
            instance?.setCursorForceVisible(enabled)
        }

        fun requestSwitchBarRefresh() {
            instance?.refreshSwitchBarSettings()
        }

        fun requestSwitchBarForceVisible(enabled: Boolean) {
            instance?.setSwitchBarForceVisible(enabled)
        }

        fun requestExternalFocusWarmup(reason: String) {
            instance?.warmUpExternalFocus(reason)
        }
    }

    private var overlayView: CursorOverlayView? = null
    private var switchBarController: SwitchBarController? = null
    private var windowManager: WindowManager? = null
    private var overlayWindowContext: Context? = null
    private var displayInfo: DisplaySessionManager.ExternalDisplayInfo? = null
    private var overlaySessionReady = false
    private var attachRetryInfo: DisplaySessionManager.ExternalDisplayInfo? = null
    private var attachRetryCount = 0
    private var attachRetryRunnable: Runnable? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var cursorSizePx = 24
    private var cursorBaseSizePx = 16
    private val dragStartDurationMs = 8L
    private val dragSegmentDurationMs = 16L
    @Volatile
    private var gesturesInFlight = 0
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var deferredBackRunnable: Runnable? = null
    private var cursorVisible = true
    private var forceCursorVisible = false
    private var continuousGestureStroke: GestureDescription.StrokeDescription? = null
    private var continuousGesturePointX = 0f
    private var continuousGesturePointY = 0f
    private var continuousGesturePendingPoint: PointF? = null
    private var continuousGestureDispatchInFlight = false
    private var continuousGestureEndRequested = false
    private var lastMoveTime = 0L
    private var lastParamsX = -1
    private var lastParamsY = -1
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cursorUpdateJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val currentInfo = serviceInfo
        if (currentInfo != null) {
            currentInfo.flags = currentInfo.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            serviceInfo = currentInfo
            DiagnosticsLog.add("Accessibility", "Accessibility: flags=${currentInfo.flags}")
        }
        cursorSizePx = (resources.displayMetrics.density * 14f).toInt().coerceAtLeast(10)
        attachToDisplay(pendingDisplayInfo)
        DiagnosticsLog.add("Accessibility", "Accessibility: connected")
        setupCursorObservation()
    }

    private fun setupCursorObservation() {
        cursorUpdateJob?.cancel()
        cursorUpdateJob = serviceScope.launch {
            combine(
                SettingsStore.cursorScaleFlow,
                SettingsStore.cursorColorFlow,
                SettingsStore.cursorAlphaFlow
            ) { _, _, _ -> }.collect {
                refreshCursorAppearance()
            }
        }
    }

    override fun onDestroy() {
        deferredBackRunnable?.let { handler.removeCallbacks(it) }
        deferredBackRunnable = null
        detachOverlay()
        instance = null
        serviceScope.cancel()
        attachRetryRunnable?.let { handler.removeCallbacks(it) }
        DiagnosticsLog.add("Accessibility", "Accessibility: destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // No-op for MVP.
    }

    override fun onInterrupt() {
        // No-op for MVP.
    }

    fun getCursorPosition(): PointF = PointF(cursorX, cursorY)

    fun moveCursorBy(dx: Float, dy: Float) {
        val info = displayInfo ?: return
        val maxX = info.width + (cursorSizePx / 4f)
        val maxY = info.height + (cursorSizePx / 4f)
        cursorX = (cursorX + dx).coerceIn(0f, maxX)
        cursorY = (cursorY + dy).coerceIn(0f, maxY)
        
        // Notify HUD of cursor movement
        HUDSystemMonitor.publishCursor(cursorX, cursorY)
        
        notifyCursorActivity()
        notifyCursorSpeed(dx, dy)
        updateOverlayPosition()
        
        DiagnosticsLog.add("Accessibility", "Cursor moved: ($cursorX, $cursorY)")
        switchBarController?.onCursorMoved(cursorX, cursorY, cursorSizePx)
    }

    fun wakeCursor() {
        notifyCursorActivity()
    }

    fun tapAtCursor() {
        val info = displayInfo ?: return
        val clamped = clampToDisplay(cursorX, cursorY, info)
        val mapped = CoordinateMapper.mapForRotation(clamped.x, clamped.y, info)
        notifyCursorActivity()
        dispatchTap(mapped.x, mapped.y, info.displayId)
    }

    fun performBack(): Boolean {
        SessionStore.lastBackFailure = null
        val now = SystemClock.uptimeMillis()
        DiagnosticsLog.add("Back", "request t=$now")
        DiagnosticsLog.add("Back", 
            "gesturesInFlight=$gesturesInFlight continuousActive=${continuousGestureStroke != null}"
        )
        val info = displayInfo
        if (info == null) {
            DiagnosticsLog.add("Back", "blocked (no external display)")
            SessionStore.lastBackFailure = "no_display"
            return false
        }
        val snapshot = snapshotWindows()
        val externalState = resolveExternalWindowState(info, snapshot)
        logBackFocusSnapshot("before", info, snapshot, externalState)
        if (snapshot.none { it.displayId == info.displayId }) {
            DiagnosticsLog.add("Back", "no window for external displayId=${info.displayId}, skip back dispatch")
            if (!SettingsStore.touchpadAutoFocusEnabled) {
                SessionStore.lastBackFailure = "external_window_missing"
                return false
            }
            val focused = dispatchFocusActivationGesture(info, snapshot, allowFallback = true)
            if (focused) {
                scheduleDeferredBackAfterFocusProbe(info.displayId)
                return true
            }
            SessionStore.lastBackFailure = "external_window_missing"
            return false
        }
        if (externalState == null || (!externalState.isActive && !externalState.isFocused)) {
            DiagnosticsLog.add(
                "Back",
                "external display not focused before back " +
                    "active=${externalState?.isActive ?: false} " +
                    "focused=${externalState?.isFocused ?: false}"
            )
            cancelContinuousGesture()
            if (SettingsStore.touchpadAutoFocusEnabled &&
                dispatchFocusActivationGesture(info, snapshot, allowFallback = true)
            ) {
                SessionStore.lastBackFailure = "external_not_focused"
                DiagnosticsLog.add("Back", "focus activation requested; require user retry")
                return false
            }
        }
        return executeBackWithLogging("immediate", snapshot, allowFocusRetry = true)
    }

    private fun executeBackWithLogging(
        reason: String,
        snapshot: List<AccessibilityWindowInfo>? = null,
        allowFocusRetry: Boolean
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        DiagnosticsLog.add("Back", "execute $reason t=$now")
        DiagnosticsLog.add("Back", 
            "gesturesInFlight=$gesturesInFlight continuousActive=${continuousGestureStroke != null}"
        )
        val info = displayInfo
        if (info == null) {
            DiagnosticsLog.add("Back", "blocked (no external display)")
            return false
        }
        val windowSnapshot = snapshot ?: snapshotWindows()
        val externalState = resolveExternalWindowState(info, windowSnapshot)
        logBackFocusSnapshot("action", info, windowSnapshot, externalState)
        if (externalState == null || (!externalState.isActive && !externalState.isFocused)) {
            DiagnosticsLog.add("Back", 
                "external display not focused at action " +
                    "active=${externalState?.isActive ?: false} " +
                    "focused=${externalState?.isFocused ?: false}"
            )
            if (allowFocusRetry &&
                SettingsStore.touchpadAutoFocusEnabled &&
                dispatchFocusActivationGesture(info, windowSnapshot, allowFallback = true)
            ) {
                SessionStore.lastBackFailure = "external_not_focused"
                DiagnosticsLog.add("Back", "focus activation requested; require user retry")
                return false
            }
            SessionStore.lastBackFailure = "external_not_focused"
            DiagnosticsLog.add("Back", "skipped (external display not focused)")
            return false
        }
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        if (!success) {
            SessionStore.lastBackFailure = "dispatch_failed"
        }
        DiagnosticsLog.add("Back", "dispatched success=$success")
        return success
    }

    fun showToastOnExternalDisplay(message: String, @Suppress("UNUSED_PARAMETER") long: Boolean = false): Boolean {
        val displayContext = overlayWindowContext ?: run {
            val info = displayInfo ?: return false
            val display = getSystemService(DisplayManager::class.java).getDisplay(info.displayId)
                ?: return false
            createDisplayContext(display)
        }
        ToastHelper.show(displayContext, message)
        return true
    }

    private data class ExternalWindowState(
        val displayId: Int,
        val type: Int,
        val isActive: Boolean,
        val isFocused: Boolean,
        val packageName: String?
    )

    private fun snapshotWindows(): List<AccessibilityWindowInfo> {
        return windows?.toList().orEmpty()
    }

    private fun resolveExternalWindowState(
        info: DisplaySessionManager.ExternalDisplayInfo,
        windows: List<AccessibilityWindowInfo>
    ): ExternalWindowState? {
        val matches = windows.filter { it.displayId == info.displayId }
        if (matches.isEmpty()) return null
        val preferred = matches.firstOrNull { it.isFocused || it.isActive } ?: matches.first()
        val packageName = preferred.root?.packageName?.toString()
        return ExternalWindowState(
            displayId = preferred.displayId,
            type = preferred.type,
            isActive = matches.any { it.isActive },
            isFocused = matches.any { it.isFocused },
            packageName = packageName
        )
    }

    private fun dumpWindows(tag: String, windows: List<AccessibilityWindowInfo>) {
        if (windows.isEmpty()) {
            DiagnosticsLog.add("Accessibility", "$tag: none")
            return
        }
        windows.forEach { window ->
            val packageName = window.root?.packageName?.toString() ?: "none"
            DiagnosticsLog.add("Accessibility", 
                "$tag displayId=${window.displayId} type=${window.type} " +
                    "active=${window.isActive} focused=${window.isFocused} root=$packageName"
            )
        }
    }

    private fun dispatchFocusActivationGesture(
        info: DisplaySessionManager.ExternalDisplayInfo,
        snapshot: List<AccessibilityWindowInfo>? = null,
        allowFallback: Boolean = false
    ): Boolean {
        if (tryTaskFocus()) {
            DiagnosticsLog.add("Back", "focus activation via task manager")
            return true
        }
        val windowSnapshot = snapshot ?: windows
        val targetWindow = pickTopAppWindow(info.displayId, windowSnapshot)
            ?: windowSnapshot?.firstOrNull { it.displayId == info.displayId }
        val root = targetWindow?.root ?: run {
            DiagnosticsLog.add("Back", "focus activation skipped (no window root)")
            if (allowFallback) {
                val nudged = dispatchFocusProbeNudge(info)
                DiagnosticsLog.add("Back", "focus activation via nudge fallback success=$nudged")
                return nudged
            }
            return false
        }
        if (tryFocusAtCursor(root, info)) {
            DiagnosticsLog.add("Back", "focus activation via cursor hit")
            return true
        }
        val candidates = collectFocusableNodes(root, maxCount = 3)
        if (candidates.isEmpty()) {
            DiagnosticsLog.add("Back", "focus activation skipped (no focusable node)")
            return false
        }
        for (node in candidates) {
            val focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
                node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            if (focused) {
                DiagnosticsLog.add("Back", "focus activation via node success=true")
                return true
            }
        }
        val rootFocused = root.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            root.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        DiagnosticsLog.add("Back", "focus activation via node success=$rootFocused")
        return rootFocused
    }

    private fun tryFocusAtCursor(
        root: AccessibilityNodeInfo,
        info: DisplaySessionManager.ExternalDisplayInfo
    ): Boolean {
        val clamped = clampToDisplay(cursorX, cursorY, info)
        val mapped = CoordinateMapper.mapForRotation(clamped.x, clamped.y, info)
        val hitNode = findNodeAtPoint(root, mapped.x.toInt(), mapped.y.toInt())
        val focusTarget = when {
            hitNode == null -> if (root.isFocusable) copyNode(root) else findFocusableNode(root)
            hitNode.isFocusable -> copyNode(hitNode)
            else -> findFocusableAncestor(hitNode) ?: findFocusableNode(root)
        }
        
        if (focusTarget == null) return false
        
        val success = focusTarget.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            focusTarget.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        
        return success
    }

    private fun tryTaskFocus(): Boolean {
        // Non-SDK reflection for ActivityTaskManager is blocked by lint/targetSdk 35+.
        // Keep task-focus path disabled and rely on accessibility focus fallback.
        return false
    }

    private fun warmUpExternalFocus(reason: String) {
        if (!SettingsStore.touchpadAutoFocusEnabled) {
            DiagnosticsLog.add("Back", "focus warmup skipped reason=$reason feature_disabled=true")
            return
        }
        val info = displayInfo ?: return
        val snapshot = snapshotWindows()
        val externalState = resolveExternalWindowState(info, snapshot)
        if (externalState?.isFocused == true || externalState?.isActive == true) {
            DiagnosticsLog.add("Back", "focus warmup skipped reason=$reason already_focused=true")
            return
        }
        val success = dispatchFocusActivationGesture(info, snapshot)
        DiagnosticsLog.add("Back", "focus warmup reason=$reason success=$success")
    }

    private fun dispatchFocusProbeNudge(info: DisplaySessionManager.ExternalDisplayInfo): Boolean {
        if (gesturesInFlight > 0) {
            DiagnosticsLog.add("Back", "focus probe nudge skipped (gesture busy)")
            return false
        }
        val density = resources.displayMetrics.density
        val nudgeDistance = FOCUS_NUDGE_DISTANCE_DP * density
        val startX = (info.width * 0.52f).coerceIn(0f, info.width.toFloat())
        val endX = (startX - nudgeDistance).coerceAtLeast(0f)
        val y = (info.height * 0.68f).coerceIn(0f, info.height.toFloat())
        val mappedStart = CoordinateMapper.mapForRotation(startX, y, info)
        val mappedEnd = CoordinateMapper.mapForRotation(endX, y, info)
        val path = Path().apply {
            moveTo(mappedStart.x, mappedStart.y)
            lineTo(mappedEnd.x, mappedEnd.y)
        }
        val builder = GestureDescription.Builder()
        builder.setDisplayId(info.displayId)
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, FOCUS_NUDGE_DURATION_MS))
        dispatchGestureTracked(
            builder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    DiagnosticsLog.add("Back", 
                        "focus probe nudge injected start=(${startX.toInt()},${y.toInt()}) " +
                            "end=(${endX.toInt()},${y.toInt()})"
                    )
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    DiagnosticsLog.add("Back", "focus probe nudge cancelled")
                }
            }
        )
        return true
    }

    private fun scheduleDeferredBackAfterFocusProbe(displayId: Int) {
        deferredBackRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            val current = displayInfo
            if (current == null || current.displayId != displayId) {
                DiagnosticsLog.add("Back", 
                    "deferred dispatch dropped displayId=$displayId session_changed=true"
                )
                return@Runnable
            }
            val success = performGlobalAction(GLOBAL_ACTION_BACK)
            if (!success) {
                SessionStore.lastBackFailure = "dispatch_failed"
            }
            DiagnosticsLog.add("Back", "deferred dispatch after focus probe success=$success")
        }
        deferredBackRunnable = runnable
        handler.postDelayed(runnable, 120L)
    }

    private fun logBackFocusSnapshot(
        stage: String,
        info: DisplaySessionManager.ExternalDisplayInfo,
        windows: List<AccessibilityWindowInfo>,
        externalState: ExternalWindowState?
    ) {
        val onDisplay = windows.count { it.displayId == info.displayId }
        DiagnosticsLog.add("Back", 
            "focus snapshot stage=$stage displayId=${info.displayId} " +
                "windowsOnDisplay=$onDisplay active=${externalState?.isActive ?: false} " +
                "focused=${externalState?.isFocused ?: false} pkg=${externalState?.packageName ?: "none"}"
        )
    }

    private fun pickTopAppWindow(
        displayId: Int,
        snapshot: List<AccessibilityWindowInfo>? = null
    ): AccessibilityWindowInfo? {
        val windowList = snapshot ?: windows
        val matches = windowList?.filter { it.displayId == displayId }.orEmpty()
        if (matches.isEmpty()) return null
        val appWindows = matches.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val candidates = if (appWindows.isNotEmpty()) appWindows else matches
        return candidates.maxByOrNull { it.layer }
    }

    private fun collectFocusableNodes(
        root: AccessibilityNodeInfo,
        maxCount: Int
    ): List<AccessibilityNodeInfo> {
        val results = ArrayList<AccessibilityNodeInfo>(maxCount)
        if (root.isFocusable && root.isVisibleToUser) {
            results.add(copyNode(root))
            if (results.size >= maxCount) return results
        }
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(copyNode(root))
        var visited = 0
        while (queue.isNotEmpty() && results.size < maxCount && visited < 200) {
            val node = queue.removeFirst()
            visited += 1
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                if (child.isFocusable && child.isVisibleToUser) {
                    results.add(copyNode(child))
                    if (results.size >= maxCount) {
                        return results
                    }
                }
                queue.add(child)
            }
        }
        return results
    }

    fun warmUpBackPipeline() {
        if (displayInfo == null) return
        val now = SystemClock.uptimeMillis()
        if (now - SessionStore.lastBackWarmupUptime < WARMUP_MIN_INTERVAL_MS) return
        SessionStore.lastBackWarmupUptime = now
        handler.post {
            if (displayInfo == null) return@post
            // Warm-up input/overlay pipeline to mitigate first-back delay without clicks.
            val originalX = cursorX
            val originalY = cursorY
            moveCursorBy(1f, 0f)
            cursorX = originalX
            cursorY = originalY
            updateOverlayPosition()
        }
    }

    fun hasExternalDisplaySession(): Boolean = displayInfo != null

    fun dumpAllWindowsDebug() {
        val tag = "XRDesk"
        val header = "=== WINDOW DIAGNOSTICS START ==="
        android.util.Log.wtf(tag, header)
        DiagnosticsLog.add("Diagnostics", header)
        
        try {
            val sdk = android.os.Build.VERSION.SDK_INT
            DiagnosticsLog.add("Diagnostics", "SDK_INT=$sdk")

            val currentDisplayId = displayInfo?.displayId ?: -1
            DiagnosticsLog.add("Diagnostics", "Target DisplayID (from DisplayManager): $currentDisplayId")

            // Check DisplayManager's perspective
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val allDisplays = dm.displays
            DiagnosticsLog.add("Diagnostics", "DisplayManager sees ${allDisplays.size} displays:")
            allDisplays.forEach { d ->
                DiagnosticsLog.add("Diagnostics", "  - ID=${d.displayId} Name=${d.name} Flags=${Integer.toHexString(d.flags)}")
            }

            // 1. Check default windows property
            val defaultWindows = windows ?: emptyList()
            DiagnosticsLog.add("Diagnostics", "getWindows().size=${defaultWindows.size}")
            defaultWindows.forEachIndexed { index, win ->
                logWindowToDiagnostics("Default", index, win)
            }

            // 2. Check all displays (API 30+)
            if (sdk >= Build.VERSION_CODES.R) {
                val allWindowsSparse = getWindowsOnAllDisplays()
                val displaysSeen = allWindowsSparse.size()
                DiagnosticsLog.add("Diagnostics", "getWindowsOnAllDisplays().displaysSeen=$displaysSeen")
                
                for (i in 0 until displaysSeen) {
                    val dId = allWindowsSparse.keyAt(i)
                    val windowList = allWindowsSparse.valueAt(i)
                    DiagnosticsLog.add("Diagnostics", "  Display $dId has ${windowList.size} windows:")
                    windowList.forEachIndexed { index, win ->
                        logWindowToDiagnostics("AllDisplays", index, win)
                    }
                }
            }
        } catch (e: Throwable) {
            val err = "FATAL: dumpAllWindowsDebug crashed: ${e.message}"
            android.util.Log.wtf(tag, err, e)
            DiagnosticsLog.add("Diagnostics", err)
        } finally {
            val footer = "=== WINDOW DIAGNOSTICS END ==="
            android.util.Log.wtf(tag, footer)
            DiagnosticsLog.add("Diagnostics", footer)
        }
    }

    private fun logWindowToDiagnostics(source: String, index: Int, win: AccessibilityWindowInfo) {
        val rootNode = try { win.root } catch (ignored: Exception) { null }
        val pkg = rootNode?.packageName ?: "unknown"
        val title = try { win.title ?: "no-title" } catch(ignored: Exception) { "n/a" }
        val detail = "    [$source][$index] id=${win.displayId} pkg=$pkg type=${win.type} title=$title active=${win.isActive} focused=${win.isFocused}"
        DiagnosticsLog.add("Diagnostics", detail)
        android.util.Log.wtf("XRDesk", detail)
    }

    fun injectKeyEvent(keycode: Int, longPress: Boolean = false): Boolean {
        val info = displayInfo ?: return false
        
        if (DEBUG) {
            DiagnosticsLog.add("KeyEvent", "code=$keycode long=$longPress display=${info.displayId}")
        }

        val shizukuAlive = ShizukuShell.isAlive()

        // 1. Primary Path: Shizuku Injection
        if (shizukuAlive) {
            if (DEBUG) DiagnosticsLog.add("KeyEvent", "using Shizuku path")
            Thread {
                val dId = info.displayId.toString()
                val cmd = if (longPress) {
                    arrayOf("input", "-d", dId, "keyevent", "--longpress", keycode.toString())
                } else {
                    arrayOf("input", "-d", dId, "keyevent", keycode.toString())
                }
                
                val result = ShizukuShell.run(*cmd)
                if (result.exitCode != 0 && DEBUG) {
                    DiagnosticsLog.add("KeyEvent", "Shizuku failed code=${result.exitCode} err=${result.error}")
                }
            }.start()
            return true
        }

        // 2. Fallback paths (Shizuku is NOT alive)
        if (DEBUG) DiagnosticsLog.add("KeyEvent", "Shizuku not available, trying fallbacks")

        when (keycode) {
            KeyEvent.KEYCODE_BACK -> {
                if (DEBUG) DiagnosticsLog.add("KeyEvent", "Back Shizuku missing, trying smart Accessibility")
                
                val snapshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val all = getWindowsOnAllDisplays()
                    val list = mutableListOf<AccessibilityWindowInfo>()
                    for (i in 0 until all.size()) { list.addAll(all.valueAt(i)) }
                    list
                } else {
                    windows?.toList().orEmpty()
                }

                val targetWindows = snapshot.filter { it.displayId == info.displayId }
                val focused = findCurrentFocusedNode(targetWindows)
                
                if (focused != null) {
                    // Try DISMISS (dialogs, menus)
                    if (performActionWithParentFallback(focused, AccessibilityNodeInfo.ACTION_DISMISS)) {
                        if (DEBUG) DiagnosticsLog.add("Back", "smart Accessibility ACTION_DISMISS success")
                        return true
                    }
                    // Try COLLAPSE (dropdowns, expandable lists)
                    if (performActionWithParentFallback(focused, AccessibilityNodeInfo.ACTION_COLLAPSE)) {
                        if (DEBUG) DiagnosticsLog.add("Back", "smart Accessibility ACTION_COLLAPSE success")
                        return true
                    }
                }

                if (DEBUG) DiagnosticsLog.add("Back", "smart Accessibility failed, using GLOBAL_ACTION_BACK")
                return performGlobalAction(GLOBAL_ACTION_BACK)
            }
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (DEBUG) DiagnosticsLog.add("KeyEvent", "Media fallback (AudioManager)")
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    val downTime = SystemClock.uptimeMillis()
                    val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keycode, 0)
                    val upEvent = KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keycode, 0)
                    
                    am.dispatchMediaKeyEvent(downEvent)
                    am.dispatchMediaKeyEvent(upEvent)
                    return true
                } else {
                    if (DEBUG) DiagnosticsLog.add("KeyEvent", "Media rejected (AudioManager missing)")
                    showToastOnExternalDisplay(getString(R.string.touchpad_shizuku_required_media))
                    return false
                }
            }
            else -> {
                if (DEBUG) DiagnosticsLog.add("KeyEvent", "No fallback for keycode $keycode")
                return false
            }
        }
    }

    /**
     * POC/Legacy method - now delegates to injectKeyEvent
     */
    fun injectNativeKeyPoC(keycode: Int): Boolean {
        return injectKeyEvent(keycode)
    }

    fun navigateFocus(direction: Int): Boolean {
        val info = displayInfo ?: return false
        
        // 1. Try Native KeyEvent via Shizuku (Highest priority for TV behavior)
        if (ShizukuShell.isAlive()) {
            val keycode = when (direction) {
                android.view.View.FOCUS_UP -> 19
                android.view.View.FOCUS_DOWN -> 20
                android.view.View.FOCUS_LEFT -> 21
                android.view.View.FOCUS_RIGHT -> 22
                else -> -1
            }
            if (keycode != -1) {
                Thread {
                    val result = ShizukuShell.run("input", "-d", info.displayId.toString(), "keyevent", keycode.toString())
                    if (result.exitCode != 0) {
                        android.util.Log.e("XRDesk", "D-Pad: Native command failed: ${result.error}")
                        showToastOnExternalDisplay("Shizuku Error: ${result.error}")
                    }
                }.start()
                return true
            }
        }

        // 2. Fallback to Accessibility navigation if Shizuku is missing
        val snapshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val all = getWindowsOnAllDisplays()
            val list = mutableListOf<AccessibilityWindowInfo>()
            for (i in 0 until all.size()) { list.addAll(all.valueAt(i)) }
            list
        } else {
            windows?.toList().orEmpty()
        }

        val targetWindows = snapshot.filter { it.displayId == info.displayId }
        if (targetWindows.isEmpty()) return false

        var current = findCurrentFocusedNode(targetWindows)
        if (current == null) {
            current = findNodeAtPointOnDisplay(targetWindows, cursorX.toInt(), cursorY.toInt())
                ?: findFirstFocusableOnDisplay(targetWindows)
        }
        
        if (current == null) return false

        val nextFocus = current.focusSearch(direction)
        
        if (nextFocus != null) {
            val targetWindow = targetWindows.find { it.displayId == info.displayId && (it.isFocused || it.isActive) }
                ?: targetWindows.firstOrNull { it.displayId == info.displayId }

            if (DEBUG) {
                val log = StringBuilder()
                log.append("\n=== FOCUS NAVIGATION DEBUG ===\n")
                log.append("Direction: $direction (UP=33, DOWN=130, LEFT=17, RIGHT=66)\n")
                log.append("Target Node BEFORE: ${getNodeDescription(nextFocus)}\n")
                
                if (targetWindow != null) {
                    log.append("Window BEFORE: focused=${targetWindow.isFocused} active=${targetWindow.isActive}\n")
                }
                DiagnosticsLog.add("Focus", log.toString())
                android.util.Log.i("XRDesk", log.toString())
            }

            // Stability: Refresh node to ensure it's not stale
            val isFresh = nextFocus.refresh()
            if (!isFresh && DEBUG) {
                DiagnosticsLog.add("Focus", "STABILITY: Node became STALE before action.")
            }

            // Stability: Verify node is still valid for focus
            val canFocus = nextFocus.isVisibleToUser && nextFocus.isFocusable && nextFocus.isEnabled
            if (!canFocus && DEBUG) {
                DiagnosticsLog.add("Focus", "STABILITY: Node not focusable: visible=${nextFocus.isVisibleToUser} focusable=${nextFocus.isFocusable} enabled=${nextFocus.isEnabled}")
            }

            val resFocus = nextFocus.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val resAccFocus = nextFocus.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            
            if (DEBUG) {
                val actionLog = "ACTION_FOCUS: $resFocus, ACTION_ACCESSIBILITY_FOCUS: $resAccFocus"
                DiagnosticsLog.add("Focus", actionLog)
                android.util.Log.i("XRDesk", actionLog)
            }

            // Delayed verification
            handler.postDelayed({
                val refreshSnapshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val all = getWindowsOnAllDisplays()
                    val list = mutableListOf<AccessibilityWindowInfo>()
                    for (i in 0 until all.size()) { list.addAll(all.valueAt(i)) }
                    list
                } else {
                    windows?.toList().orEmpty()
                }
                val refreshTargetWindows = refreshSnapshot.filter { it.displayId == info.displayId }
                
                var inputFocused: AccessibilityNodeInfo? = null
                var accFocused: AccessibilityNodeInfo? = null
                
                for (win in refreshTargetWindows) {
                    val root = try { win.root } catch (e: Exception) { null } ?: continue
                    if (inputFocused == null) inputFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (accFocused == null) accFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                }

                val matched = (inputFocused != null && isSameNode(inputFocused, nextFocus)) || 
                              (accFocused != null && isSameNode(accFocused, nextFocus))
                
                if (!matched && DEBUG) {
                    val delayedLog = StringBuilder()
                    delayedLog.append("\n=== FOCUS REJECTED OR LOST (100ms) ===\n")
                    delayedLog.append("Target Node: ${getNodeDescription(nextFocus)}\n")
                    delayedLog.append("Actual INPUT Focus: ${getNodeDescription(inputFocused)}\n")
                    delayedLog.append("Actual ACC Focus: ${getNodeDescription(accFocused)}\n")
                    
                    if (inputFocused != null || accFocused != null) {
                        delayedLog.append("REASON: Application REDIRECTED focus or node was RECREATED.\n")
                    } else {
                        delayedLog.append("REASON: Application REJECTED focus or window lost focus.\n")
                    }
                    delayedLog.append("=== END VERIFICATION ===\n")
                    
                    DiagnosticsLog.add("Focus", delayedLog.toString())
                    android.util.Log.i("XRDesk", delayedLog.toString())
                } else if (DEBUG) {
                    DiagnosticsLog.add("Focus", "D-Pad: Focus verified successfully at 100ms.")
                }
            }, 100)

            return true
        }

        val bestNode = performGeometricFocusSearch(targetWindows, current, direction)
        if (bestNode != null) {
            val success = bestNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ||
                          bestNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (success) {
                DiagnosticsLog.add("Focus", "D-Pad: geometric search success")
                return true
            }
        }

        return false
    }

    fun clickFocused(): Boolean {
        val info = displayInfo ?: return false
        
        // 1. Try Native KeyEvent via Shizuku
        if (ShizukuShell.isAlive()) {
            Thread {
                val result = ShizukuShell.run("input", "-d", info.displayId.toString(), "keyevent", "23")
                if (result.exitCode != 0) {
                    showToastOnExternalDisplay("Shizuku Error: ${result.error}")
                }
            }.start()
            return true
        }

        // 2. Fallback to Accessibility Actions
        val snapshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val all = getWindowsOnAllDisplays()
            val list = mutableListOf<AccessibilityWindowInfo>()
            for (i in 0 until all.size()) { list.addAll(all.valueAt(i)) }
            list
        } else {
            windows?.toList().orEmpty()
        }

        val targetWindows = snapshot.filter { it.displayId == info.displayId }
        val focused = findCurrentFocusedNode(targetWindows)

        if (focused != null) {
            if (performActionWithParentFallback(focused, AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            
            val rect = Rect()
            focused.getBoundsInScreen(rect)
            dispatchTap(rect.centerX().toFloat(), rect.centerY().toFloat(), info.displayId)
            return true
        }

        tapAtCursor()
        return true
    }

    private fun findCurrentFocusedNode(windows: List<AccessibilityWindowInfo>): AccessibilityNodeInfo? {
        for (win in windows) {
            val root = try { win.root } catch (e: Exception) { null } ?: continue
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) return focused
        }
        return null
    }

    private fun findNodeAtPointOnDisplay(windows: List<AccessibilityWindowInfo>, x: Int, y: Int): AccessibilityNodeInfo? {
        for (win in windows) {
            val root = try { win.root } catch (e: Exception) { null } ?: continue
            val hit = findNodeAtPoint(root, x, y)
            if (hit != null) return hit
        }
        return null
    }

    private fun findFirstFocusableOnDisplay(windows: List<AccessibilityWindowInfo>): AccessibilityNodeInfo? {
        for (win in windows) {
            val root = try { win.root } catch (e: Exception) { null } ?: continue
            val node = findFocusableNode(root)
            if (node != null) return node
        }
        return null
    }

    private fun performGeometricFocusSearch(
        windows: List<AccessibilityWindowInfo>,
        current: AccessibilityNodeInfo,
        direction: Int
    ): AccessibilityNodeInfo? {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        for (win in windows) {
            val root = try { win.root } catch (e: Exception) { null } ?: continue
            collectAllFocusableNodes(root, allNodes)
        }
        
        android.util.Log.d("XRDesk", "D-Pad: geometric search found ${allNodes.size} total candidates")
        if (allNodes.isEmpty()) {
            android.util.Log.e("XRDesk", "D-Pad: No visible focusable/clickable nodes found in the target window hierarchy.")
        }

        val currentRect = Rect()
        current.getBoundsInScreen(currentRect)
        android.util.Log.d("XRDesk", "D-Pad: currentRect=$currentRect")

        var bestNode: AccessibilityNodeInfo? = null
        var minDistance = Float.MAX_VALUE

        for (node in allNodes) {
            if (isSameNode(node, current)) {
                continue
            }
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)

            // Allow a small overlap (5dp) to be more forgiving with alignment
            val margin = (resources.displayMetrics.density * 5).toInt()

            val isCandidate = when (direction) {
                android.view.View.FOCUS_UP -> nodeRect.centerY() < currentRect.centerY() - margin
                android.view.View.FOCUS_DOWN -> nodeRect.centerY() > currentRect.centerY() + margin
                android.view.View.FOCUS_LEFT -> nodeRect.centerX() < currentRect.centerX() - margin
                android.view.View.FOCUS_RIGHT -> nodeRect.centerX() > currentRect.centerX() + margin
                else -> false
            }

            if (isCandidate) {
                val dist = calculateGeometricDistance(currentRect, nodeRect, direction)
                if (dist < minDistance) {
                    minDistance = dist
                    bestNode = node // bestNode now owns this node
                    continue
                }
            }
        }
        return bestNode
    }

    private fun isSameNode(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean {
        val ra = Rect()
        val rb = Rect()
        a.getBoundsInScreen(ra)
        b.getBoundsInScreen(rb)
        
        return ra == rb && 
               a.className == b.className && 
               a.text == b.text &&
               a.viewIdResourceName == b.viewIdResourceName &&
               a.contentDescription == b.contentDescription &&
               a.packageName == b.packageName &&
               a.windowId == b.windowId
    }

    private fun getNodeDescription(node: AccessibilityNodeInfo?): String {
        if (node == null) return "null"
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return buildString {
            append("[${node.className}] ")
            append("id=${node.viewIdResourceName} ")
            append("text=\"${node.text}\" ")
            append("desc=\"${node.contentDescription}\" ")
            append("bounds=$rect ")
            append("pkg=${node.packageName} ")
            append("win=${node.windowId} ")
            append("focusable=${node.isFocusable} ")
            append("visible=${node.isVisibleToUser} ")
            append("enabled=${node.isEnabled} ")
            append("focused=${node.isFocused} ")
            append("accFocused=${node.isAccessibilityFocused}")
        }
    }

    private fun collectAllFocusableNodes(root: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if ((root.isFocusable || root.isClickable) && root.isVisibleToUser) {
            list.add(copyNode(root))
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            if (child != null) {
                collectAllFocusableNodes(child, list)
            }
        }
    }

    private fun calculateGeometricDistance(src: Rect, dest: Rect, direction: Int): Float {
        val dx = (dest.centerX() - src.centerX()).toFloat()
        val dy = (dest.centerY() - src.centerY()).toFloat()
        return when (direction) {
            android.view.View.FOCUS_UP, android.view.View.FOCUS_DOWN -> abs(dy) + abs(dx) * 2f
            android.view.View.FOCUS_LEFT, android.view.View.FOCUS_RIGHT -> abs(dx) + abs(dy) * 2f
            else -> abs(dx) + abs(dy)
        }
    }

    private fun performFallbackDpadGesture(direction: Int) {
        val density = resources.displayMetrics.density
        val step = 60f * density
        var dx = 0f
        var dy = 0f
        when (direction) {
            android.view.View.FOCUS_UP -> dy = -step
            android.view.View.FOCUS_DOWN -> dy = step
            android.view.View.FOCUS_LEFT -> dx = -step
            android.view.View.FOCUS_RIGHT -> dx = step
        }
        moveCursorBy(dx, dy)
    }

    private fun performActionWithParentFallback(node: AccessibilityNodeInfo, action: Int): Boolean {
        var current: AccessibilityNodeInfo? = copyNode(node)
        while (current != null) {
            if (current.performAction(action)) {
                return true
            }
            val parent = current.parent
            current = parent
        }
        return false
    }

    private fun logNode(prefix: String, node: AccessibilityNodeInfo?) {
        if (node == null) {
            DiagnosticsLog.add("Focus", "$prefix: null")
            return
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: "no-text"
        val id = node.viewIdResourceName ?: "no-id"
        DiagnosticsLog.add("Focus", "$prefix: [${node.className}] \"$text\" id=$id bounds=$rect clickable=${node.isClickable}")
    }

    fun setTextOnFocused(text: String): Boolean {
        val info = displayInfo ?: return recordInjection(
            false,
            getString(R.string.injection_no_external_display)
        )
        val targetWindows = windows?.filter { it.displayId == info.displayId }.orEmpty()
        val roots = if (targetWindows.isNotEmpty()) {
            targetWindows.mapNotNull { it.root }
        } else {
            listOfNotNull(rootInActiveWindow)
        }
        for (root in roots) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val target = focused ?: findEditableNode(root)
            if (target != null) {
                if (target.isFocusable && !target.isFocused) {
                    target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                }
                if (!target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) {
                    return recordInjection(
                        false,
                        getString(R.string.injection_action_set_text_not_supported)
                    )
                }
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                val success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                return recordInjection(
                    success,
                    if (success) {
                        getString(R.string.injection_action_set_text_success)
                    } else {
                        getString(R.string.injection_action_set_text_failed)
                    }
                )
            }
        }
        return recordInjection(false, getString(R.string.injection_no_editable_field))
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(copyNode(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun attachToDisplay(
        info: DisplaySessionManager.ExternalDisplayInfo?,
        allowRetry: Boolean = true
    ) {
        if (info == null) {
            detachOverlay()
            cancelAttachRetry()
            return
        }
        if (displayInfo?.displayId == info.displayId && overlayView != null) {
            return
        }
        detachOverlay()
        displayInfo = info
        DiagnosticsLog.add("Accessibility", "Accessibility: attach displayId=${info.displayId}")
        cursorBaseSizePx = cursorBaseSizeForDisplay(info)
        cursorSizePx = cursorMaxSizeForDisplay(cursorBaseSizePx)
        cursorX = (info.width / 2f)
        cursorY = (info.height / 2f)

        val display = getSystemService(DisplayManager::class.java).getDisplay(info.displayId)
        if (display == null) {
            android.util.Log.e("Geometry-Audit", "Display MISSING for ID=${info.displayId}")
            if (allowRetry) {
                scheduleAttachRetry(info)
            }
            return
        }
        
        val baseDisplayContext = createDisplayContext(display)
        
        // 1. Create WindowContext bound to the display (Fixes Geometry)
        val windowContext = if (Build.VERSION.SDK_INT >= 30) {
            baseDisplayContext.createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        } else {
            baseDisplayContext
        }
            
        val themeRes = if (SettingsStore.nightMode == SettingsStore.THEME_AMOLED) 
            R.style.Theme_XRDesk_Amoled 
        else 
            R.style.Theme_XRDesk

        android.util.Log.e("HUD-Lifecycle", "WindowContext created")
        overlayWindowContext = android.view.ContextThemeWrapper(windowContext, themeRes)
        
        val wm = overlayWindowContext!!.getSystemService(WindowManager::class.java)
        windowManager = wm

        if (Build.VERSION.SDK_INT >= 30) {
            android.util.Log.e("Geometry-Audit", "[REAL HUD] WindowContext Bounds=${wm.currentWindowMetrics.bounds} " +
                "Orientation=${windowContext.resources.configuration.orientation}")
        }

        val view = CursorOverlayView(overlayWindowContext!!)
        overlayView = view
        cursorVisible = true
        view.alpha = SettingsStore.cursorAlpha
        view.setBaseSizePx(cursorBaseSizePx)
        view.setArrowColor(SettingsStore.cursorColor)

        val params = WindowManager.LayoutParams(
            cursorSizePx,
            cursorSizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val tipOffset = cursorTipOffsetPx()
        params.x = (cursorX - tipOffset.x).toInt()
        params.y = (cursorY - tipOffset.y).toInt()
        
        android.util.Log.d("HUD-Lifecycle", "Cursor addView started")
        try {
            wm.addView(view, params)
            android.util.Log.d("HUD-Lifecycle", "Cursor addView success")
            notifyOverlaySessionReady(info, wm, overlayWindowContext!!)
        } catch (e: WindowManager.BadTokenException) {
            android.util.Log.e("HUD-Lifecycle", "Cursor addView failed (BadToken): ${e.message}")
            detachOverlay()
            if (allowRetry) {
                DiagnosticsLog.add("Accessibility", "Accessibility: attach failed (BadToken), retrying id=${info.displayId}")
                scheduleAttachRetry(info)
            }
        } catch (e: Throwable) {
            android.util.Log.e("HUD-Lifecycle", "Cursor addView hard fail", e)
            detachOverlay()
        }
        
        scheduleCursorHide()
        cancelAttachRetry()
    }

    /**
     * Central entry point for initializing secondary overlay components.
     * Called only after the primary window (cursor) has been successfully attached,
     * ensuring that the WindowContext has a valid token.
     */
    private fun notifyOverlaySessionReady(
        info: DisplaySessionManager.ExternalDisplayInfo,
        wm: WindowManager,
        windowContext: Context
    ) {
        if (overlaySessionReady) return
        overlaySessionReady = true
        
        android.util.Log.e("HUD-Lifecycle", "Overlay session ready for display ${info.displayId}")

        // 1. Initialize HUD
        HUDManager.onDisplayConnected(windowContext, wm, info)
        DiagnosticsLog.add("WindowManager", "HUD Attached to display ${info.displayId}")

        // 2. Initialize SwitchBar if enabled
        if (SettingsStore.switchBarEnabled) {
            switchBarController = SwitchBarController(
                this,
                windowContext,
                wm,
                info
            )
        }
    }

    private fun scheduleAttachRetry(info: DisplaySessionManager.ExternalDisplayInfo) {
        if (attachRetryInfo?.displayId == info.displayId && attachRetryRunnable != null) return
        attachRetryInfo = info
        attachRetryCount = 0
        attachRetryRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                val currentInfo = attachRetryInfo ?: return
                attachRetryCount += 1
                attachToDisplay(currentInfo, allowRetry = false)
                if (overlayView == null && attachRetryCount < ATTACH_RETRY_MAX) {
                    handler.postDelayed(this, ATTACH_RETRY_DELAY_MS)
                } else {
                    if (overlayView == null) {
                        DiagnosticsLog.add("Accessibility", "Accessibility: attach retry exhausted id=${currentInfo.displayId}")
                    }
                    cancelAttachRetry()
                }
            }
        }
        attachRetryRunnable = runnable
        handler.postDelayed(runnable, ATTACH_RETRY_DELAY_MS)
    }

    private fun cancelAttachRetry() {
        attachRetryRunnable?.let { handler.removeCallbacks(it) }
        attachRetryRunnable = null
        attachRetryInfo = null
        attachRetryCount = 0
    }

    private fun detachOverlay() {
        android.util.Log.e("HUD-Lifecycle", "Overlay session destroyed")
        android.util.Log.d("Accessibility", "detachOverlay CALLED - isFinishing logic check")
        overlaySessionReady = false
        deferredBackRunnable?.let { handler.removeCallbacks(it) }
        deferredBackRunnable = null
        cancelAttachRetry()
        
        // Teardown HUD
        HUDManager.onDisplayDisconnected()

        switchBarController?.teardown()
        switchBarController = null
        overlayView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { windowManager?.removeView(view) }
            }
        }
        overlayView = null
        windowManager = null
        overlayWindowContext = null
        displayInfo = null
        cancelContinuousGesture()
        cancelCursorHide()
        DiagnosticsLog.add("Accessibility", "Accessibility: overlay detached")
    }

    private fun refreshSwitchBarSettings() {
        val info = displayInfo ?: return
        val wm = windowManager ?: return
        val context = overlayWindowContext ?: return
        if (!SettingsStore.switchBarEnabled) {
            switchBarController?.teardown()
            switchBarController = null
            return
        }
        if (switchBarController == null) {
            switchBarController = SwitchBarController(
                this,
                context,
                wm,
                info
            )
        } else {
            switchBarController?.refreshScale()
            switchBarController?.refreshItems()
        }
    }

    private fun setSwitchBarForceVisible(enabled: Boolean) {
        switchBarController?.setForceVisible(enabled)
    }

    private fun cursorBaseSizeForDisplay(info: DisplaySessionManager.ExternalDisplayInfo): Int {
        val minDim = min(info.width, info.height).toFloat()
        val size = (minDim * 0.012f * SettingsStore.cursorScale).toInt()
        return size.coerceIn(10, 26)
    }

    private fun cursorMaxSizeForDisplay(baseSize: Int): Int {
        return (baseSize * CursorOverlayView.MAX_SCALE).toInt().coerceAtLeast(baseSize)
    }

    private fun clampToDisplay(
        x: Float,
        y: Float,
        info: DisplaySessionManager.ExternalDisplayInfo
    ): PointF {
        val clampedX = x.coerceIn(0f, info.width.toFloat())
        val clampedY = y.coerceIn(0f, info.height.toFloat())
        return PointF(clampedX, clampedY)
    }

    private fun cursorTipOffsetPx(): PointF {
        val offsetX = cursorSizePx * CursorOverlayView.HOTSPOT_FRACTION_X
        val offsetY = cursorSizePx * CursorOverlayView.HOTSPOT_FRACTION_Y
        return PointF(offsetX, offsetY)
    }
    private fun updateOverlayPosition() {
        val view = overlayView ?: return
        val wm = windowManager ?: return
        
        // If cursor is not visible, don't waste CPU/IPC updating its position
        if (!cursorVisible && !forceCursorVisible) return
        
        val params = view.layoutParams as WindowManager.LayoutParams
        val tipOffset = cursorTipOffsetPx()
        val newX = (cursorX - tipOffset.x).toInt()
        val newY = (cursorY - tipOffset.y).toInt()
        
        // Only update if the integer pixel position has actually changed
        if (newX == lastParamsX && newY == lastParamsY) return
        
        params.x = newX
        params.y = newY
        lastParamsX = newX
        lastParamsY = newY
        
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun notifyCursorSpeed(dx: Float, dy: Float) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastMoveTime == 0L) 0L else now - lastMoveTime
        lastMoveTime = now
        overlayView?.onCursorMoved(dx, dy, dt)
    }

    private fun notifyCursorActivity() {
        showCursor()
        scheduleCursorHide()
    }

    private fun scheduleCursorHide() {
        val delay = SettingsStore.cursorHideDelayMs
        cancelCursorHide()
        if (forceCursorVisible) return
        if (delay <= 0L) return
        hideRunnable = Runnable { hideCursor() }
        handler.postDelayed(hideRunnable!!, delay)
    }

    private fun cancelCursorHide() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
    }

    private fun showCursor() {
        val view = overlayView ?: return
        if (!cursorVisible) {
            cursorVisible = true
            view.alpha = SettingsStore.cursorAlpha
        }
    }

    private fun hideCursor() {
        val view = overlayView ?: run { return }
        cursorVisible = false
        view.alpha = 0f
    }

    private fun setCursorForceVisible(enabled: Boolean) {
        forceCursorVisible = enabled
        if (enabled) {
            cancelCursorHide()
            showCursor()
        } else {
            scheduleCursorHide()
        }
    }

    private fun refreshCursorAppearance() {
        val info = displayInfo ?: return
        cursorBaseSizePx = cursorBaseSizeForDisplay(info)
        cursorSizePx = cursorMaxSizeForDisplay(cursorBaseSizePx)
        overlayView?.alpha = if (cursorVisible) SettingsStore.cursorAlpha else 0f
        overlayView?.let { view ->
            view.setBaseSizePx(cursorBaseSizePx)
            view.setArrowColor(SettingsStore.cursorColor)
            val wm = windowManager ?: return
            val params = view.layoutParams as WindowManager.LayoutParams
            params.width = cursorSizePx
            params.height = cursorSizePx
            val tipOffset = cursorTipOffsetPx()
            params.x = (cursorX - tipOffset.x).toInt()
            params.y = (cursorY - tipOffset.y).toInt()
            wm.updateViewLayout(view, params)
        }
    }

    private fun dispatchTap(x: Float, y: Float, displayId: Int) {
        val path = Path().apply { moveTo(x, y) }
        val builder = GestureDescription.Builder()
        builder.setDisplayId(displayId)
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
        dispatchGestureTracked(
            builder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    recordInjection(true, getString(R.string.injection_tap_injected))
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    recordInjection(false, getString(R.string.injection_tap_cancelled))
                }
            }
        )
    }

    private fun dispatchGestureTracked(
        description: GestureDescription,
        callback: GestureResultCallback
    ) {
        gesturesInFlight += 1
        val accepted = dispatchGesture(
            description,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gesturesInFlight = (gesturesInFlight - 1).coerceAtLeast(0)
                    callback.onCompleted(gestureDescription)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gesturesInFlight = (gesturesInFlight - 1).coerceAtLeast(0)
                    callback.onCancelled(gestureDescription)
                }
            },
            null
        )
        if (!accepted) {
            gesturesInFlight = (gesturesInFlight - 1).coerceAtLeast(0)
            callback.onCancelled(null)
        }
    }

    private fun findScrollableTargetAtPoint(
        info: DisplaySessionManager.ExternalDisplayInfo,
        x: Float,
        y: Float
    ): AccessibilityNodeInfo? {
        val targetWindows = windows?.filter { it.displayId == info.displayId }.orEmpty()
        val window = targetWindows.firstOrNull { it.isFocused || it.isActive }
            ?: targetWindows.firstOrNull()
        val root = window?.root ?: return null
        val hitNode = findNodeAtPoint(root, x.toInt(), y.toInt())
        
        var current: AccessibilityNodeInfo? = if (hitNode != null) hitNode else copyNode(root)
        
        while (current != null) {
            if (current.isScrollable && current.isVisibleToUser) {
                return current
            }
            val parent = current.parent
            current = parent
        }
        
        return findScrollableNode(root)
    }

    private fun findNodeAtPoint(
        root: AccessibilityNodeInfo,
        x: Int,
        y: Int
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val depths = ArrayDeque<Int>()
        
        queue.add(copyNode(root))
        depths.add(0)
        
        var best: AccessibilityNodeInfo? = null
        var bestDepth = -1
        val rect = Rect()
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val depth = depths.removeFirst()
            
            node.getBoundsInScreen(rect)
            if (rect.contains(x, y)) {
                if (depth >= bestDepth) {
                    best = copyNode(node)
                    bestDepth = depth
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                        depths.add(depth + 1)
                    }
                }
            }
        }
        return best
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(copyNode(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable && node.isVisibleToUser) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findFocusableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(copyNode(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isFocusable && node.isVisibleToUser) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findFocusableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = copyNode(node)
        while (current != null) {
            if (current.isFocusable && current.isVisibleToUser) {
                return current
            }
            val parent = current.parent
            current = parent
        }
        return null
    }

    private fun copyNode(source: AccessibilityNodeInfo): AccessibilityNodeInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(source)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(source)
        }
    }

    private fun recordInjection(success: Boolean, message: String): Boolean {
        SessionStore.lastInjectionResult = if (success) {
            message
        } else {
            getString(R.string.injection_failed_with_message, message)
        }
        return success
    }

    fun startContinuousGestureAtCursor(): Boolean {
        val info = displayInfo ?: return false
        if (continuousGestureStroke != null || continuousGestureDispatchInFlight) {
            return false
        }
        
        val clamped = clampToDisplay(cursorX, cursorY, info)
        continuousGesturePointX = clamped.x
        continuousGesturePointY = clamped.y
        
        val mapped = CoordinateMapper.mapForRotation(clamped.x, clamped.y, info)
        val path = Path().apply { moveTo(mapped.x, mapped.y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, dragStartDurationMs, true)
        
        continuousGestureStroke = stroke
        continuousGesturePendingPoint = null
        continuousGestureEndRequested = false
        
        notifyCursorActivity()
        return dispatchContinuousGestureStrokeTracked(stroke, info.displayId)
    }

    fun updateContinuousGestureTo(x: Float, y: Float) {
        val info = displayInfo ?: return
        if (continuousGestureStroke == null) return
        if (!x.isFinite() || !y.isFinite()) return
        
        val next = clampToDisplay(x, y, info)
        continuousGesturePendingPoint = next
        dispatchPendingContinuousGesture()
    }

    fun endContinuousGesture() {
        if (continuousGestureStroke == null) return
        continuousGestureEndRequested = true
        dispatchPendingContinuousGesture()
    }

    fun cancelContinuousGesture() {
        abandonContinuousGesture()
    }

    fun startContinuousScrollAtPoint(x: Float, y: Float): Boolean {
        val info = displayInfo ?: return false
        
        // Ensure any previous session (like a drag) is terminated
        if (continuousGestureStroke != null || continuousGestureDispatchInFlight) {
            abandonContinuousGesture()
        }

        val clamped = clampToDisplay(x, y, info)
        continuousGesturePointX = clamped.x
        continuousGesturePointY = clamped.y
        
        val mapped = CoordinateMapper.mapForRotation(clamped.x, clamped.y, info)
        val path = Path().apply { moveTo(mapped.x, mapped.y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, dragStartDurationMs, true)
        
        continuousGestureStroke = stroke
        continuousGesturePendingPoint = null
        continuousGestureEndRequested = false
        
        notifyCursorActivity()
        return dispatchContinuousGestureStrokeTracked(stroke, info.displayId)
    }

    fun updateContinuousScrollTo(x: Float, y: Float) {
        val info = displayInfo ?: return
        if (continuousGestureStroke == null) return
        if (!x.isFinite() || !y.isFinite()) return
        
        val next = clampToDisplay(x, y, info)
        continuousGesturePendingPoint = next
        dispatchPendingContinuousGesture()
    }

    fun endContinuousScroll() {
        if (continuousGestureStroke == null) return
        continuousGestureEndRequested = true
        dispatchPendingContinuousGesture()
    }

    private fun dispatchContinuousGestureStrokeTracked(
        stroke: GestureDescription.StrokeDescription,
        displayId: Int
    ): Boolean {
        continuousGestureDispatchInFlight = true
        val builder = GestureDescription.Builder()
        builder.setDisplayId(displayId)
        builder.addStroke(stroke)
        
        val accepted = dispatchGesture(
            builder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gesturesInFlight = (gesturesInFlight - 1).coerceAtLeast(0)
                    continuousGestureDispatchInFlight = false
                    dispatchPendingContinuousGesture()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gesturesInFlight = (gesturesInFlight - 1).coerceAtLeast(0)
                    continuousGestureDispatchInFlight = false
                    abandonContinuousGesture()
                }
            },
            null
        )
        
        if (accepted) {
            gesturesInFlight += 1
        } else {
            abandonContinuousGesture()
        }
        return accepted
    }

    private fun dispatchPendingContinuousGesture() {
        if (continuousGestureDispatchInFlight) return
        val info = displayInfo ?: run {
            abandonContinuousGesture()
            return
        }
        val activeStroke = continuousGestureStroke ?: return
        val pending = continuousGesturePendingPoint
        
        if (pending != null) {
            val mappedStart = CoordinateMapper.mapForRotation(
                continuousGesturePointX,
                continuousGesturePointY,
                info
            )
            val mappedEnd = CoordinateMapper.mapForRotation(pending.x, pending.y, info)
            continuousGesturePendingPoint = null
            
            val moved = abs(mappedEnd.x - mappedStart.x) >= 0.5f ||
                abs(mappedEnd.y - mappedStart.y) >= 0.5f
                
            if (moved) {
                val willContinue = !continuousGestureEndRequested
                val path = Path().apply {
                    moveTo(mappedStart.x, mappedStart.y)
                    lineTo(mappedEnd.x, mappedEnd.y)
                }
                val stroke = activeStroke.continueStroke(
                    path,
                    0,
                    dragSegmentDurationMs,
                    willContinue
                )
                continuousGesturePointX = pending.x
                continuousGesturePointY = pending.y
                continuousGestureStroke = if (willContinue) stroke else null
                if (!willContinue) continuousGestureEndRequested = false
                
                dispatchContinuousGestureStrokeTracked(stroke, info.displayId)
                return
            }
        }
        
        if (!continuousGestureEndRequested) return
        
        // Finalize if end was requested but no more movement
        val mapped = CoordinateMapper.mapForRotation(
            continuousGesturePointX,
            continuousGesturePointY,
            info
        )
        val path = Path().apply {
            moveTo(mapped.x, mapped.y)
            lineTo(mapped.x, mapped.y)
        }
        val stroke = activeStroke.continueStroke(path, 0, dragSegmentDurationMs, false)
        continuousGestureStroke = null
        continuousGestureEndRequested = false
        dispatchContinuousGestureStrokeTracked(stroke, info.displayId)
    }

    private fun abandonContinuousGesture() {
        continuousGestureStroke = null
        continuousGesturePendingPoint = null
        continuousGestureDispatchInFlight = false
        continuousGestureEndRequested = false
    }
}
