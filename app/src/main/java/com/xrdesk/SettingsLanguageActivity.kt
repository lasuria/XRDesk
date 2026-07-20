package com.xrdesk

import android.os.Bundle
import android.widget.RadioGroup

class SettingsLanguageActivity : BaseSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_language_v2)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_language))
        applyEdgeToEdge(findViewById(android.R.id.content))

        val radioGroup = findViewById<RadioGroup>(R.id.languageRadioGroup)
        
        val currentLang = SettingsStore.appLanguageTag
        when (currentLang) {
            "system" -> radioGroup.check(R.id.langSystem)
            "en" -> radioGroup.check(R.id.langEn)
            "zh-CN" -> radioGroup.check(R.id.langZh)
            "ru" -> radioGroup.check(R.id.langRu)
            "uk" -> radioGroup.check(R.id.langUk)
            else -> radioGroup.check(R.id.langSystem)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newLang = when (checkedId) {
                R.id.langSystem -> "system"
                R.id.langEn -> "en"
                R.id.langZh -> "zh-CN"
                R.id.langRu -> "ru"
                R.id.langUk -> "uk"
                else -> "system"
            }
            SettingsStore.setAppLanguage(this, newLang)
            recreate()
        }
    }
}
