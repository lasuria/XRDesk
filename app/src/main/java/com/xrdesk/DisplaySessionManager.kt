package com.xrdesk

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.xrdesk.diagnostics.DiagnosticsManager
import kotlin.math.max
import kotlin.math.min

object DisplaySessionManager {
    data class ExternalDisplayInfo(
        val displayId: Int,
        val name: String,
        val width: Int,         // Normalized: Always Landscape (max)
        val height: Int,        // Normalized: Always Landscape (min)
        val rawWidth: Int,      // Raw from Android
        val rawHeight: Int,     // Raw from Android
        val densityDpi: Int,
        val rotation: Int,
        val refreshRate: Float,
        val isHdr: Boolean
    )

    interface Listener {
        fun onDisplayChanged(info: ExternalDisplayInfo?)
        fun onDisplaysUpdated(displays: List<ExternalDisplayInfo>, selectedDisplayId: Int?) {}
    }

    private val listeners = mutableSetOf<Listener>()
    private var displayManager: DisplayManager? = null
    private var displayInfo: ExternalDisplayInfo? = null
    private var externalDisplays: List<ExternalDisplayInfo> = emptyList()
    private var selectedDisplayId: Int? = null
    private var listenerRegistered = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            android.util.Log.e("HUD-Lifecycle", "Display detected: id=$displayId")
            DiagnosticsManager.info("Display", "Display added: id=$displayId")
            refreshDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            DiagnosticsManager.info("Display", "Display removed: id=$displayId")
            refreshDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            DiagnosticsManager.info("Display", "Display changed: id=$displayId")
            refreshDisplays()
        }
    }

    fun init(context: Context) {
        if (displayManager != null) return
        displayManager = context.getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayListener, null)
        listenerRegistered = true
        refreshDisplays()
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onDisplaysUpdated(externalDisplays, selectedDisplayId)
        listener.onDisplayChanged(displayInfo)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun getExternalDisplayInfo(): ExternalDisplayInfo? = displayInfo
    fun getExternalDisplays(): List<ExternalDisplayInfo> = externalDisplays
    fun getSelectedDisplayId(): Int? = selectedDisplayId

    fun setSelectedDisplayId(displayId: Int) {
        if (selectedDisplayId == displayId) return
        selectedDisplayId = displayId
        refreshDisplays()
    }

    fun stopSession() {
        SessionStore.clear()
        ControlAccessibilityService.requestDetachOverlay()
    }

    private fun refreshDisplays() {
        val dm = displayManager
        
        val presentationDisplays = dm
            ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            ?.toList()
            .orEmpty()
        val usingFallback = presentationDisplays.isEmpty()
        val displays = if (!usingFallback) {
            presentationDisplays
        } else {
            // Fallback for OEMs that don't classify external displays as presentations.
            dm?.getDisplays()
                ?.toList()
                ?.filter { it.displayId != Display.DEFAULT_DISPLAY }
                .orEmpty()
        }.map { buildInfo(it) }
        externalDisplays = displays

        val previousInfo = displayInfo
        if (externalDisplays.isEmpty()) {
            displayInfo = null
            selectedDisplayId = null
        } else {
            if (selectedDisplayId == null ||
                externalDisplays.none { it.displayId == selectedDisplayId }
            ) {
                selectedDisplayId = externalDisplays.first().displayId
            }
            displayInfo = externalDisplays.first { it.displayId == selectedDisplayId }
        }
        val newInfo = displayInfo
        if (previousInfo != null && newInfo == null) {
            stopSession()
        }
        if (previousInfo != newInfo ||
            (newInfo != null && ControlAccessibilityService.current()?.hasExternalDisplaySession() == false)
        ) {
            ControlAccessibilityService.requestAttachToDisplay(newInfo)
        }

        listeners.forEach {
            it.onDisplaysUpdated(externalDisplays, selectedDisplayId)
            it.onDisplayChanged(displayInfo)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildInfo(display: Display): ExternalDisplayInfo {
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        
        val rw = metrics.widthPixels
        val rh = metrics.heightPixels

        android.util.Log.e("Geometry-Audit", "1. DisplaySessionManager: Raw=${rw}x${rh} Normalized=${max(rw, rh)}x${min(rw, rh)} Rotation=${display.rotation}")
        val hdrCaps = display.hdrCapabilities
        val hasHdr = hdrCaps != null && hdrCaps.supportedHdrTypes.isNotEmpty()
        
        val newInfo = ExternalDisplayInfo(
            displayId = display.displayId,
            name = display.name ?: "Unknown Display",
            width = max(rw, rh),
            height = min(rw, rh),
            rawWidth = rw,
            rawHeight = rh,
            densityDpi = metrics.densityDpi,
            rotation = display.rotation,
            refreshRate = display.refreshRate,
            isHdr = hasHdr
        )
        
        android.util.Log.e("Geometry-Audit", "DisplaySessionManager: ID=${newInfo.displayId} Name=${newInfo.name} " +
            "Raw=${rw}x${rh} Normalized=${newInfo.width}x${newInfo.height} Rotation=${newInfo.rotation}")
            
        return newInfo
    }


}
