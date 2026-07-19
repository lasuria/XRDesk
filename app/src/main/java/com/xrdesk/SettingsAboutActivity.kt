package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.view.View

class SettingsAboutActivity : BaseSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_about)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_about_title))
        applyEdgeToEdge(findViewById(R.id.settingsAboutRoot))

        findViewById<View>(R.id.rowBasedOn).setOnClickListener {
            startActivity(Intent(this, AboutBasedOnActivity::class.java))
        }

        findViewById<View>(R.id.rowXRDesk).setOnClickListener {
            startActivity(Intent(this, AboutXRDeskActivity::class.java))
        }

        findViewById<View>(R.id.rowWhatsNew).setOnClickListener {
            startActivity(Intent(this, SettingsChangelogActivity::class.java))
        }

        findViewById<View>(R.id.rowDiagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
    }
}
