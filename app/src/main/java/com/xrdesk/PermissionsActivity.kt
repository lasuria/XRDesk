package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PermissionsActivity : BaseSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_permissions_title))
        applyEdgeToEdge(findViewById(R.id.permissionsRoot))

        val btnNotifications = findViewById<MaterialButton>(R.id.btnGrantNotifications)
        val statusNotifications = findViewById<TextView>(R.id.statusNotifications)
        
        val btnBluetooth = findViewById<MaterialButton>(R.id.btnGrantBluetooth)
        val statusBluetooth = findViewById<TextView>(R.id.statusBluetooth)
        
        val btnLocation = findViewById<MaterialButton>(R.id.btnGrantLocation)
        val statusLocation = findViewById<TextView>(R.id.statusLocation)

        btnNotifications.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnBluetooth.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 102)
            }
        }

        btnLocation.setOnClickListener {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }

        // Auto-refresh status
        lifecycleScope.launch {
            while (true) {
                updateStatusUI(statusNotifications, isNotificationAccessGranted())
                updatePermissionUI(statusBluetooth, btnBluetooth, android.Manifest.permission.BLUETOOTH_CONNECT)
                updatePermissionUI(statusLocation, btnLocation, android.Manifest.permission.ACCESS_FINE_LOCATION)
                delay(1000)
            }
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val contentResolver = contentResolver
        val enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val packageName = packageName
        return enabledNotificationListeners != null && enabledNotificationListeners.contains(packageName)
    }

    private fun updateStatusUI(textView: TextView, granted: Boolean) {
        if (granted) {
            textView.text = getString(R.string.status_granted)
            textView.setTextColor(getColor(R.color.success_color))
        } else {
            textView.text = getString(R.string.status_denied)
            textView.setTextColor(getColor(R.color.error_color))
        }
    }

    private fun updatePermissionUI(status: TextView, button: MaterialButton, permission: String) {
        val granted = checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        updateStatusUI(status, granted)
        button.isEnabled = !granted
    }
}
