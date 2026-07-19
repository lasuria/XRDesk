package com.deskcontrol

import android.os.Bundle
import android.widget.RadioGroup

class SettingsThemeActivity : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_theme)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_theme))
        applyEdgeToEdge(findViewById(R.id.settingsToolbar))

        val radioGroup = findViewById<RadioGroup>(R.id.themeRadioGroup)
        val sectionCustomize = findViewById<android.view.View>(R.id.sectionCustomize)
        val btnOpenEditor = findViewById<android.view.View>(R.id.btnOpenEditor)
        
        val currentTheme = SettingsStore.nightMode
        when (currentTheme) {
            SettingsStore.THEME_LIGHT -> radioGroup.check(R.id.radioLight)
            SettingsStore.THEME_DARK -> radioGroup.check(R.id.radioDark)
            SettingsStore.THEME_AMOLED -> radioGroup.check(R.id.radioAmoled)
            SettingsStore.THEME_CUSTOM -> radioGroup.check(R.id.radioCustom)
        }
        
        updateCustomizeSection(currentTheme, sectionCustomize)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radioLight -> SettingsStore.THEME_LIGHT
                R.id.radioDark -> SettingsStore.THEME_DARK
                R.id.radioAmoled -> SettingsStore.THEME_AMOLED
                R.id.radioCustom -> SettingsStore.THEME_CUSTOM
                else -> SettingsStore.THEME_DARK
            }
            if (newTheme != SettingsStore.nightMode) {
                updateCustomizeSection(newTheme, sectionCustomize, animate = true)
                SettingsStore.setNightMode(this, newTheme)
            }
        }

        btnOpenEditor.setOnClickListener {
            startActivity(android.content.Intent(this, ThemeEditorActivity::class.java))
        }
    }

    private fun updateCustomizeSection(theme: Int, section: android.view.View, animate: Boolean = false) {
        val visible = theme == SettingsStore.THEME_CUSTOM
        if (animate) {
            if (visible) {
                section.visibility = android.view.View.VISIBLE
                section.alpha = 0f
                section.translationY = -20f
                section.animate().alpha(1f).translationY(0f).setDuration(200).start()
            } else {
                section.animate().alpha(0f).translationY(-20f).setDuration(200).withEndAction {
                    section.visibility = android.view.View.GONE
                }.start()
            }
        } else {
            section.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
}
