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
            ToastHelper.show(this, R.string.dev_toast_already)
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
            ToastHelper.show(this, getString(R.string.dev_toast_steps, stepsLeft))
        } else if (logoTapCount >= 7) {
            SettingsStore.setDeveloperModeUnlocked(this, true)
            ToastHelper.show(this, R.string.dev_toast_enabled)
            logoTapCount = 0
        }
    }
}
