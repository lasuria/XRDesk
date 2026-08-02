package com.xrdesk


import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Shared system-Back behavior for phone-side control surfaces.
 * Manages back redirection to external display and focus recovery strategies.
 */
class ControlSurfaceBackController(
    private val activity: AppCompatActivity,
    private val isControlActive: () -> Boolean,
    private val preBackHandler: () -> Boolean = { false }
) {
    init {
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (preBackHandler()) return
                    handleBack()
                }
            }
        )
    }

    /** Warm up focus when the control surface becomes visible/resumed. */
    fun warmUpOnResume() {
        ControlAccessibilityService.current()?.warmUpBackPipeline()
        if (SettingsStore.touchpadAutoFocusEnabled) {
            ControlAccessibilityService.requestExternalFocusWarmup()
        }
    }

    /** Warm up focus when the control surface is specifically activated by user touch. */
    fun warmUpOnActivation() {
        if (SettingsStore.touchpadAutoFocusEnabled) {
            ControlAccessibilityService.requestExternalFocusWarmup()
        }
    }

    private fun handleBack() {
        if (!isControlActive()) {
            activity.finish()
            return
        }

        if (DisplaySessionManager.getExternalDisplayInfo() == null) {
            ToastHelper.show(activity, R.string.touchpad_no_external_display)
            return
        }

        val service = ControlAccessibilityService.current()
        if (service == null) {
            ToastHelper.show(activity, R.string.touchpad_accessibility_required_toast)
            return
        }

        val success = service.performBack()
        if (!success) {
            val messageRes = when (SessionStore.lastBackFailure) {
                "external_not_focused" -> R.string.touchpad_back_external_not_focused
                "external_window_missing" -> R.string.touchpad_back_external_window_missing
                "dispatch_failed" -> R.string.touchpad_back_dispatch_failed
                else -> null
            }
            messageRes?.let { service.showToastOnExternalDisplay(activity.getString(it)) }
        }
    }
}
