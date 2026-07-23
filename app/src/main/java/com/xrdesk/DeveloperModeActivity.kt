package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch

class DeveloperModeActivity : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_mode)
        setupToolbar(R.id.settingsToolbar, getString(R.string.dev_mode_title))
        applyEdgeToEdge(findViewById(R.id.developerModeRoot))

        val swAlwaysShow = findViewById<MaterialSwitch>(R.id.switchDebugAlwaysShow)
        swAlwaysShow.isChecked = SettingsStore.hudDebugAlwaysShow
        swAlwaysShow.setOnCheckedChangeListener { _, b -> SettingsStore.setHudDebugAlwaysShow(this, b) }

        val swHighlightZone = findViewById<MaterialSwitch>(R.id.switchDebugHighlightZone)
        swHighlightZone.isChecked = SettingsStore.hudDebugHighlightZone
        swHighlightZone.setOnCheckedChangeListener { _, b -> SettingsStore.setHudDebugHighlightZone(this, b) }

        val swShowBounds = findViewById<MaterialSwitch>(R.id.switchDebugShowBounds)
        swShowBounds.isChecked = SettingsStore.hudDebugShowBounds
        swShowBounds.setOnCheckedChangeListener { _, b -> SettingsStore.setHudDebugShowBounds(this, b) }

        findViewById<View>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        findViewById<TextView>(R.id.tvVersion).text = getString(R.string.dev_version_label, BuildConfig.VERSION_NAME)
        findViewById<TextView>(R.id.tvBuild).text = getString(R.string.dev_build_label, "2026.07.23")

        findViewById<View>(R.id.btnResetDeveloper).setOnClickListener {
            SettingsStore.setDeveloperModeUnlocked(this, false)
            finish()
        }
    }
}
