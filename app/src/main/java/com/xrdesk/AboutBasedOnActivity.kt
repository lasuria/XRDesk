package com.xrdesk

import android.content.Intent
import android.net.Uri
import android.os.Bundle

class AboutBasedOnActivity : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_based_on)
        setupToolbar(R.id.settingsToolbar, getString(R.string.about_based_on_title))
        applyEdgeToEdge(findViewById(R.id.aboutBasedOnRoot))

        findViewById<android.view.View>(R.id.btnGitHub).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_original_url)))
            startActivity(intent)
        }
    }
}
