package com.xrdesk

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

class SettingsDisplayActivity : BaseSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_display)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_category_display_title))
        applyEdgeToEdge(findViewById(R.id.settingsDisplayRoot))

        // Power
        val keepScreenOnSwitch = findViewById<MaterialSwitch>(R.id.switchKeepScreenOn)
        keepScreenOnSwitch.isChecked = SettingsStore.keepScreenOn
        keepScreenOnSwitch.setOnCheckedChangeListener { _, isChecked -> SettingsStore.setKeepScreenOn(this, isChecked) }

        // Unlock Hint
        val showHintSwitch = findViewById<MaterialSwitch>(R.id.switchShowUnlockHint)
        val showArrowSwitch = findViewById<MaterialSwitch>(R.id.switchShowHintArrow)
        val hintOptions = findViewById<View>(R.id.unlockHintOptions)
        val sliderTimeout = findViewById<Slider>(R.id.sliderHintTimeout)
        val textTimeout = findViewById<TextView>(R.id.hintTimeoutValue)
        val editHintText = findViewById<TextInputEditText>(R.id.editHintText)
        val sliderFontSize = findViewById<Slider>(R.id.sliderHintFontSize)
        val textFontSize = findViewById<TextView>(R.id.hintFontSizeValue)
        val sliderOpacity = findViewById<Slider>(R.id.sliderHintOpacity)
        val textOpacity = findViewById<TextView>(R.id.hintOpacityValue)
        val btnReset = findViewById<MaterialButton>(R.id.btnResetUnlockHint)

        showHintSwitch.isChecked = SettingsStore.blackoutShowHint
        hintOptions.isVisible = SettingsStore.blackoutShowHint
        showHintSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setBlackoutShowHint(this, isChecked)
            hintOptions.isVisible = isChecked
        }

        showArrowSwitch.isChecked = SettingsStore.blackoutShowArrow
        showArrowSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setBlackoutShowArrow(this, isChecked)
        }

        // Timeout
        sliderTimeout.valueFrom = 0f
        sliderTimeout.valueTo = 15f
        sliderTimeout.stepSize = 1f
        
        fun refreshTimeoutUI() {
            val timeout = SettingsStore.blackoutHintTimeout
            sliderTimeout.value = timeout.toFloat().coerceIn(0f, 15f)
            updateTimeoutText(textTimeout, timeout)
        }
        refreshTimeoutUI()
        
        sliderTimeout.addOnChangeListener { _, value, fromUser ->
            val timeout = value.toInt()
            updateTimeoutText(textTimeout, timeout)
            if (fromUser) SettingsStore.setBlackoutHintTimeout(this, timeout)
        }

        // Text
        editHintText.setText(SettingsStore.blackoutHintText)
        editHintText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                SettingsStore.setBlackoutHintText(this@SettingsDisplayActivity, s.toString())
            }
        })

        // Font Size
        sliderFontSize.valueFrom = 12f
        sliderFontSize.valueTo = 32f
        sliderFontSize.stepSize = 1f
        
        fun refreshFontSizeUI() {
            val size = SettingsStore.blackoutHintFontSize
            sliderFontSize.value = size.coerceIn(12f, 32f)
            textFontSize.text = getString(R.string.settings_hint_font_size_value, size.toInt())
        }
        refreshFontSizeUI()
        
        sliderFontSize.addOnChangeListener { _, value, fromUser ->
            textFontSize.text = getString(R.string.settings_hint_font_size_value, value.toInt())
            if (fromUser) SettingsStore.setBlackoutHintFontSize(this, value)
        }

        // Opacity
        sliderOpacity.valueFrom = 30f
        sliderOpacity.valueTo = 100f
        sliderOpacity.stepSize = 5f
        
        fun refreshOpacityUI() {
            val opacityPercent = (SettingsStore.blackoutHintOpacity * 100).toInt()
            sliderOpacity.value = opacityPercent.toFloat().coerceIn(30f, 100f)
            textOpacity.text = getString(R.string.settings_hint_opacity_value, opacityPercent)
        }
        refreshOpacityUI()
        
        sliderOpacity.addOnChangeListener { _, value, fromUser ->
            textOpacity.text = getString(R.string.settings_hint_opacity_value, value.toInt())
            if (fromUser) SettingsStore.setBlackoutHintOpacity(this, value / 100f)
        }

        // Reset
        btnReset.setOnClickListener {
            SettingsStore.resetBlackoutHintSettings(this)
            
            // Refresh all UI components
            showHintSwitch.isChecked = SettingsStore.blackoutShowHint
            showArrowSwitch.isChecked = SettingsStore.blackoutShowArrow
            hintOptions.isVisible = SettingsStore.blackoutShowHint
            
            refreshTimeoutUI()
            editHintText.setText(SettingsStore.blackoutHintText)
            refreshFontSizeUI()
            refreshOpacityUI()
        }
    }

    private fun updateTimeoutText(view: TextView, value: Int) {
        view.text = if (value == 0) {
            getString(R.string.settings_hint_timeout_persistent)
        } else {
            getString(R.string.settings_hint_timeout_value, value)
        }
    }
}
