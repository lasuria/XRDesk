package com.xrdesk

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import rikka.shizuku.Shizuku

/**
 * Shared accessibility gate controller.
 * Manages manual settings redirection and optional Shizuku enablement.
 * Uses XRDesk's ShizukuShell backend for command execution.
 */
class AccessibilityGateController(
    private val activity: AppCompatActivity,
    private val gate: View,
    private val content: View,
    private val touchpadArea: View,
    private val tuningPanel: View,
    private val openSettingsButton: View,
    private val enableWithShizukuButton: View,
    private val onEnabledChanged: (Boolean) -> Unit,
    private val onShizukuStatusChanged: (Boolean) -> Unit = {}
) {
    private var shizukuBinderReady = false
    private var shizukuEnableInFlight = false
    private var destroyed = false

    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        shizukuBinderReady = true
        updateShizukuButton()
    }
    private val shizukuDeadListener = Shizuku.OnBinderDeadListener {
        shizukuBinderReady = false
        updateShizukuButton()
    }
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != SHIZUKU_PERMISSION_REQUEST) {
                return@OnRequestPermissionResultListener
            }
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                enableAccessibilityWithShizuku()
            } else {
                ToastHelper.show(activity, R.string.touchpad_shizuku_permission_denied)
                updateShizukuButton()
            }
        }

    init {
        openSettingsButton.setOnClickListener {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        enableWithShizukuButton.setOnClickListener {
            requestAccessibilityViaShizuku()
        }
        Shizuku.addBinderReceivedListener(shizukuBinderListener)
        Shizuku.addBinderDeadListener(shizukuDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        refreshShizukuBinderState()
        updateShizukuButton()
    }

    fun onStart() {
        refreshShizukuBinderState()
        refresh()
    }

    fun refresh() {
        val enabled = ControlAccessibilityService.isEnabled(activity)
        gate.isVisible = !enabled
        content.alpha = if (enabled) 1f else DISABLED_CONTENT_ALPHA
        touchpadArea.isEnabled = enabled
        tuningPanel.isEnabled = enabled
        onEnabledChanged(enabled)
        updateShizukuButton()
    }

    fun onDestroy() {
        destroyed = true
        Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        Shizuku.removeBinderDeadListener(shizukuDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    private fun updateShizukuButton() {
        if (destroyed) return
        refreshShizukuBinderState()
        val alive = ShizukuShell.isAlive()
        enableWithShizukuButton.alpha = if (alive) 1f else 0.5f
        enableWithShizukuButton.isEnabled = !shizukuEnableInFlight
        onShizukuStatusChanged(alive)
    }

    private fun refreshShizukuBinderState() {
        shizukuBinderReady = shizukuBinderReady || isShizukuBinderAlive()
    }

    private fun requestAccessibilityViaShizuku() {
        if (!ShizukuShell.isAlive()) {
            showShizukuIntroDialog()
            return
        }
        val permission = try {
            Shizuku.checkSelfPermission()
        } catch (e: Throwable) {
            showShizukuIntroDialog()
            return
        }
        if (permission == PackageManager.PERMISSION_GRANTED) {
            enableAccessibilityWithShizuku()
            return
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            ToastHelper.show(activity, R.string.touchpad_shizuku_permission_rationale)
            return
        }
        try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
        } catch (e: Throwable) {
            showShizukuIntroDialog()
        }
    }

    private fun enableAccessibilityWithShizuku() {
        if (shizukuEnableInFlight) return
        shizukuEnableInFlight = true
        updateShizukuButton()
        Thread {
            val success = enableAccessibilityWithShizukuInternal()
            activity.runOnUiThread {
                if (destroyed) return@runOnUiThread
                shizukuEnableInFlight = false
                updateShizukuButton()
                if (success) {
                    ToastHelper.show(activity, R.string.touchpad_shizuku_enable_success)
                } else {
                    ToastHelper.show(activity, R.string.touchpad_shizuku_enable_failed)
                }
                refresh()
            }
        }.start()
    }

    private fun enableAccessibilityWithShizukuInternal(): Boolean {
        val component = ComponentName(activity, ControlAccessibilityService::class.java)
            .flattenToString()
        val current = Settings.Secure.getString(
            activity.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val updated = mergeAccessibilityServices(current, component)
        
        val setServices = ShizukuShell.runSettingsCommand("enabled_accessibility_services", updated)
        if (setServices.exitCode != 0) {
            DiagnosticsLog.add("Shizuku", "Shizuku: enable services failed code=${setServices.exitCode} err=${setServices.error}")
            return false
        }
        
        val enable = ShizukuShell.runSettingsCommand("accessibility_enabled", "1")
        if (enable.exitCode != 0) {
            DiagnosticsLog.add("Shizuku", "Shizuku: enable accessibility flag failed code=${enable.exitCode} err=${enable.error}")
            return false
        }
        
        SystemClock.sleep(SHIZUKU_ENABLE_SETTLE_MS)
        return ControlAccessibilityService.isEnabled(activity)
    }

    private fun mergeAccessibilityServices(current: String?, component: String): String {
        if (current.isNullOrBlank() || current == "null") return component
        val entries = current.split(":").filter(String::isNotBlank)
        if (entries.contains(component)) return entries.joinToString(":")
        return (entries + component).joinToString(":")
    }

    private fun isShizukuBinderAlive(): Boolean {
        return try {
            val method = Shizuku::class.java.declaredMethods.firstOrNull { candidate ->
                (candidate.name == "pingBinder" || candidate.name == "isBinderAlive") &&
                    candidate.parameterTypes.isEmpty()
            } ?: return false
            method.isAccessible = true
            (method.invoke(null) as? Boolean) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun showShizukuIntroDialog() {
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.touchpad_shizuku_intro_title)
            .setMessage(activity.getString(R.string.touchpad_shizuku_intro_message))
            .setPositiveButton(R.string.touchpad_shizuku_intro_ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private companion object {
        const val DISABLED_CONTENT_ALPHA = 0.35f
        const val SHIZUKU_ENABLE_SETTLE_MS = 150L
        const val SHIZUKU_PERMISSION_REQUEST = 1201
    }
}
