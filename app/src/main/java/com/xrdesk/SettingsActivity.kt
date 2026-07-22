package com.xrdesk

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : BaseSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_title))
        applyEdgeToEdge(findViewById(R.id.settingsRoot))

        findViewById<android.view.View>(R.id.rowTheme).setOnClickListener {
            startActivity(Intent(this, SettingsThemeActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowLanguage).setOnClickListener {
            startActivity(Intent(this, SettingsLanguageActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowDisplay).setOnClickListener {
            startActivity(Intent(this, SettingsDisplayActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowHUD).setOnClickListener {
            startActivity(Intent(this, SettingsHUDActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowTouchpad).setOnClickListener {
            startActivity(Intent(this, SettingsTouchpadActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowCursor).setOnClickListener {
            startActivity(Intent(this, SettingsCursorActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowDock).setOnClickListener {
            startActivity(Intent(this, SettingsDockActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowPermissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowAbout).setOnClickListener {
            startActivity(Intent(this, SettingsAboutActivity::class.java))
        }

        val rowDev = findViewById<android.view.View>(R.id.rowDeveloperMode)
        val divDev = findViewById<android.view.View>(R.id.rowDeveloperModeDivider)
        
        fun updateDevRow() {
            val visible = SettingsStore.developerModeUnlocked
            rowDev.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            divDev.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        updateDevRow()
        rowDev.setOnClickListener {
            startActivity(Intent(this, DeveloperModeActivity::class.java))
        }

        // Keep row updated if unlocked while settings is open (unlikely but good for flow)
        lifecycleScope.launch {
            SettingsStore.developerModeUnlockedFlow.collect {
                updateDevRow()
            }
        }
    }
}
