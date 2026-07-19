package com.xrdesk

import android.os.Bundle

class SettingsChangelogActivity : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)
        setupToolbar(R.id.settingsToolbar, getString(R.string.about_whats_new))
        applyEdgeToEdge(findViewById(R.id.changelogRoot))
    }
}
