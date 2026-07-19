package com.xrdesk

import android.content.Intent
import android.os.Bundle

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

        findViewById<android.view.View>(R.id.rowTouchpad).setOnClickListener {
            startActivity(Intent(this, SettingsTouchpadActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowCursor).setOnClickListener {
            startActivity(Intent(this, SettingsCursorActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowDock).setOnClickListener {
            startActivity(Intent(this, SettingsDockActivity::class.java))
        }

        findViewById<android.view.View>(R.id.rowAbout).setOnClickListener {
            startActivity(Intent(this, SettingsAboutActivity::class.java))
        }
    }
}
