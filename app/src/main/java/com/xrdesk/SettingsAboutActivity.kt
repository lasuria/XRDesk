package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.view.View

class SettingsAboutActivity : BaseSettingsActivity() {

    private var logoTapCount = 0
    private var lastLogoTapTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_about)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_about_title))
        applyEdgeToEdge(findViewById(R.id.settingsAboutRoot))

        findViewById<View>(R.id.appLogo).setOnClickListener {
            handleLogoTap()
        }
        
        findViewById<View>(R.id.rowBasedOn).setOnClickListener {
            startActivity(Intent(this, AboutBasedOnActivity::class.java))
        }

        findViewById<View>(R.id.rowXRDesk).setOnClickListener {
            startActivity(Intent(this, AboutXRDeskActivity::class.java))
        }

        findViewById<View>(R.id.rowWhatsNew).setOnClickListener {
            startActivity(Intent(this, SettingsChangelogActivity::class.java))
        }

        findViewById<View>(R.id.rowDiagnostics).visibility = View.GONE
    }

    private fun handleLogoTap() {
        if (SettingsStore.developerModeUnlocked) {
            android.widget.Toast.makeText(this, getString(R.string.dev_toast_already), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastLogoTapTime > 1500) {
            logoTapCount = 0
        }
        logoTapCount++
        lastLogoTapTime = now

        if (logoTapCount in 5..6) {
            val stepsLeft = 7 - logoTapCount
            android.widget.Toast.makeText(this, getString(R.string.dev_toast_steps, stepsLeft), android.widget.Toast.LENGTH_SHORT).show()
        } else if (logoTapCount >= 7) {
            SettingsStore.setDeveloperModeUnlocked(this, true)
            android.widget.Toast.makeText(this, getString(R.string.dev_toast_enabled), android.widget.Toast.LENGTH_LONG).show()
            logoTapCount = 0
        }
    }
}
