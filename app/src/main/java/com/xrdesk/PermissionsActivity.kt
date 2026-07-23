package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import com.google.android.material.button.MaterialButton

class PermissionsActivity : BaseSettingsActivity() {

    private lateinit var btnNotifications: MaterialButton
    private lateinit var statusNotifications: TextView
    private lateinit var btnBluetooth: MaterialButton
    private lateinit var statusBluetooth: TextView
    private lateinit var btnLocation: MaterialButton
    private lateinit var statusLocation: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_permissions_title))
        applyEdgeToEdge(findViewById(R.id.permissionsRoot))

        btnNotifications = findViewById(R.id.btnGrantNotifications)
        statusNotifications = findViewById(R.id.statusNotifications)
        
        btnBluetooth = findViewById(R.id.btnGrantBluetooth)
        statusBluetooth = findViewById(R.id.statusBluetooth)
        
        btnLocation = findViewById(R.id.btnGrantLocation)
        statusLocation = findViewById(R.id.statusLocation)

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
    }

    override fun onResume() {
        super.onResume()
        updateAllPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateAllPermissions()
    }

    private fun updateAllPermissions() {
        updateStatusUI(statusNotifications, btnNotifications, isNotificationAccessGranted())
        updatePermissionUI(statusBluetooth, btnBluetooth, android.Manifest.permission.BLUETOOTH_CONNECT)
        updatePermissionUI(statusLocation, btnLocation, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun isNotificationAccessGranted(): Boolean {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val component = android.content.ComponentName(this, HUDNotificationService::class.java).flattenToString()
        return listeners?.split(":")?.any { it.equals(component, ignoreCase = true) } == true
    }

    private fun updateStatusUI(textView: TextView, button: MaterialButton?, granted: Boolean) {
        val statusText = getString(if (granted) R.string.status_granted else R.string.status_denied)
        if (textView.text != statusText) {
            textView.text = statusText
            textView.setTextColor(getColor(if (granted) R.color.success_color else R.color.error_color))
        }
        if (button != null && button.isEnabled == granted) {
            button.isEnabled = !granted
        }
    }

    private fun updatePermissionUI(status: TextView, button: MaterialButton, permission: String) {
        val granted = checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        updateStatusUI(status, button, granted)
    }
}
