package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup

class SettingsThemeActivity : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_theme_v2)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_theme))
        applyEdgeToEdge(findViewById(android.R.id.content))

        val radioGroup = findViewById<RadioGroup>(R.id.themeRadioGroup)
        val sectionCustomize = findViewById<View>(R.id.sectionCustomize)
        val btnOpenEditor = findViewById<View>(R.id.btnOpenEditor)
        
        val currentTheme = SettingsStore.nightMode
        when (currentTheme) {
            SettingsStore.THEME_LIGHT -> radioGroup.check(R.id.radioLight)
            SettingsStore.THEME_DARK -> radioGroup.check(R.id.radioDark)
            SettingsStore.THEME_AMOLED -> radioGroup.check(R.id.radioAmoled)
            SettingsStore.THEME_CUSTOM -> radioGroup.check(R.id.radioCustom)
            else -> radioGroup.check(R.id.radioDark)
        }
        
        sectionCustomize.visibility = if (currentTheme == SettingsStore.THEME_CUSTOM) View.VISIBLE else View.GONE

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radioLight -> SettingsStore.THEME_LIGHT
                R.id.radioDark -> SettingsStore.THEME_DARK
                R.id.radioAmoled -> SettingsStore.THEME_AMOLED
                R.id.radioCustom -> SettingsStore.THEME_CUSTOM
                else -> SettingsStore.THEME_DARK
            }
            if (newTheme != SettingsStore.nightMode) {
                SettingsStore.setNightMode(this, newTheme)
                // Activity will be recreated by SettingsStore.setNightMode which applies to all activities
            }
        }

        btnOpenEditor.setOnClickListener {
            startActivity(Intent(this, ThemeEditorActivity::class.java))
        }
    }
}
