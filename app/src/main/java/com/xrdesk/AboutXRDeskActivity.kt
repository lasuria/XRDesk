package com.xrdesk

import android.content.Intent
import android.net.Uri
import android.os.Bundle

class AboutXRDeskActivity : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_xrdesk)
        setupToolbar(R.id.settingsToolbar, getString(R.string.about_xrdesk_title))
        applyEdgeToEdge(findViewById(R.id.aboutXRDeskRoot))

        findViewById<android.view.View>(R.id.btnGitHub).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_fork_url)))
            startActivity(intent)
        }

        findViewById<android.view.View>(R.id.btn4PDA).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_4pda_url)))
            startActivity(intent)
        }

        findViewById<android.view.View>(R.id.btnIssues).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_fork_url) + "/issues"))
            startActivity(intent)
        }
    }
}
