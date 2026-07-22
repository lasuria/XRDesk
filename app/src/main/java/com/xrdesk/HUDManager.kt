package com.xrdesk

import android.content.Context
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * Singleton orchestrator for the HUD system.
 * Subscribes to settings and manages real HUD instances on external displays.
 */
object HUDManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var statusPanel: StatusPanelController? = null
    private var notifications: NotificationController? = null
    
    private var contextRef: WeakReference<Context>? = null
    private var currentWindowManager: WindowManager? = null
    private var currentDisplayInfo: DisplaySessionManager.ExternalDisplayInfo? = null

    init {
        // Observe master toggle
        scope.launch {
            SettingsStore.hudEnabledFlow.collectLatest { enabled ->
                if (enabled) {
                    resumeHUD()
                } else {
                    pauseHUD()
                }
            }
        }
    }

    fun onDisplayConnected(context: Context, windowManager: WindowManager, info: DisplaySessionManager.ExternalDisplayInfo) {
        android.util.Log.e("Cursor-Debug", "onDisplayConnected: Display=${info.displayId} hudEnabled=${SettingsStore.hudEnabled}")
        contextRef = WeakReference(context)
        currentWindowManager = windowManager
        
        val changed = currentDisplayInfo?.displayId != info.displayId ||
                     currentDisplayInfo?.width != info.width ||
                     currentDisplayInfo?.height != info.height
        
        currentDisplayInfo = info
        
        if (SettingsStore.hudEnabled) {
            if (changed) {
                android.util.Log.e("Cursor-Debug", "Display changed, recreating HUD")
                pauseHUD()
            }
            resumeHUD()
        }
    }

    fun onDisplayDisconnected() {
        pauseHUD()
        contextRef = null
        currentWindowManager = null
        currentDisplayInfo = null
    }

    fun requestShow() = statusPanel?.show()
    fun requestHide() = statusPanel?.hide()
    fun requestRefresh() = statusPanel?.refreshUI()
    fun getDebugStatus(): String = statusPanel?.getDebugStatus() ?: "HUD not initialized"
    fun getDebugInfo(): DisplaySessionManager.ExternalDisplayInfo? = currentDisplayInfo

    private fun resumeHUD() {
        val context = contextRef?.get() ?: return
        val wm = currentWindowManager ?: return
        val info = currentDisplayInfo ?: return
        
        if (statusPanel != null) return // Already running

        HUDSystemMonitor.start(context)
        
        val container = WindowManagerContainer(wm, info.width, info.height)
        statusPanel = StatusPanelController(context, container, isPreview = false)
        notifications = NotificationController(context, wm)
        
        android.util.Log.d("HUD-Trace", "HUD Resumed on display ${info.displayId} (${info.width}x${info.height})")
    }

    private fun pauseHUD() {
        statusPanel?.destroy()
        notifications?.destroy()
        statusPanel = null
        notifications = null
    }

    fun postNotification(notification: HUDNotification) {
        notifications?.post(notification)
    }
    
    fun showTestNotification(context: Context) {
        if (notifications == null) {
            if (SettingsStore.hudEnabled && currentDisplayInfo != null) resumeHUD()
        }
        
        val controller = notifications ?: return
        
        val testNotif = HUDNotification(
            id = "test_${System.currentTimeMillis()}",
            packageName = context.packageName,
            appName = "XRDesk",
            title = "Test Notification",
            text = "This is how notifications look on your external display.",
            icon = context.packageManager.getApplicationIcon(context.packageName)
        )
        controller.post(testNotif)
    }
}
