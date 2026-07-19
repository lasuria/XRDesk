package com.xrdesk

import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsCursorActivity : BaseSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_cursor)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_category_cursor_title))
        applyEdgeToEdge(findViewById(R.id.settingsCursorRoot))

        val cursorSizeSlider = findViewById<Slider>(R.id.sliderCursorSize)
        val cursorSizeValue = findViewById<TextView>(R.id.cursorSizeValue)
        val cursorColorToggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.cursorColorToggleGroup)
        val cursorOpacitySlider = findViewById<Slider>(R.id.sliderCursorOpacity)
        val cursorOpacityValue = findViewById<TextView>(R.id.cursorOpacityValue)
        val cursorSpeedSlider = findViewById<Slider>(R.id.sliderCursorSpeed)
        val cursorSpeedValue = findViewById<TextView>(R.id.cursorSpeedValue)
        val cursorHideSwitch = findViewById<SwitchMaterial>(R.id.switchCursorHide)
        val cursorHideOptions = findViewById<android.view.View>(R.id.cursorHideOptions)
        val cursorHideDelayValue = findViewById<TextView>(R.id.cursorHideDelayValue)
        val cursorHideDelaySlider = findViewById<Slider>(R.id.sliderCursorHideDelay)

        cursorSizeSlider.valueFrom = 0.5f
        cursorSizeSlider.valueTo = 3.0f
        cursorSizeSlider.stepSize = 0.1f
        cursorSizeSlider.value = snapToStep(SettingsStore.cursorScale.coerceIn(0.5f, 3.0f), 0.5f, 0.1f)
        cursorSizeValue.text = getString(R.string.settings_cursor_scale_value, cursorSizeSlider.value)
        cursorSizeSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 0.5f, 0.1f)
            cursorSizeValue.text = getString(R.string.settings_cursor_scale_value, snapped)
            if (fromUser) {
                if (snapped != value) cursorSizeSlider.value = snapped
                SettingsStore.setCursorScale(this, snapped)
                ControlAccessibilityService.requestCursorForceVisible(true)
            }
        }

        // Color Toggle Group
        val isBlack = SettingsStore.cursorColor == 0xFF000000.toInt()
        cursorColorToggleGroup.check(if (isBlack) R.id.btnColorBlack else R.id.btnColorWhite)
        cursorColorToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val color = if (checkedId == R.id.btnColorBlack) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            SettingsStore.setCursorColor(this, color)
            ControlAccessibilityService.requestCursorForceVisible(true)
        }

        cursorOpacitySlider.valueFrom = 0.6f
        cursorOpacitySlider.valueTo = 1.0f
        cursorOpacitySlider.stepSize = 0.1f
        cursorOpacitySlider.value = SettingsStore.cursorAlpha.coerceIn(0.6f, 1.0f)
        cursorOpacityValue.text = getString(R.string.settings_cursor_opacity_value, (cursorOpacitySlider.value * 100).toInt())
        cursorOpacitySlider.addOnChangeListener { _, value, fromUser ->
            cursorOpacityValue.text = getString(R.string.settings_cursor_opacity_value, (value * 100).toInt())
            if (fromUser) {
                SettingsStore.setCursorAlpha(this, value)
                ControlAccessibilityService.requestCursorForceVisible(true)
            }
        }

        cursorSpeedSlider.valueFrom = 0.7f
        cursorSpeedSlider.valueTo = 1.2f
        cursorSpeedSlider.stepSize = 0.1f
        cursorSpeedSlider.value = SettingsStore.pointerSpeed().coerceIn(0.7f, 1.2f)
        cursorSpeedValue.text = getString(R.string.settings_cursor_speed_value, cursorSpeedSlider.value)
        cursorSpeedSlider.addOnChangeListener { _, value, fromUser ->
            cursorSpeedValue.text = getString(R.string.settings_cursor_speed_value, value)
            if (fromUser) {
                SettingsStore.setPointerSpeed(this, value)
                ControlAccessibilityService.requestCursorForceVisible(true)
            }
        }

        val hideEnabled = SettingsStore.cursorHideDelayMs > 0
        cursorHideSwitch.isChecked = hideEnabled
        cursorHideOptions.isVisible = hideEnabled
        cursorHideSwitch.setOnCheckedChangeListener { _, isChecked ->
            cursorHideOptions.isVisible = isChecked
            if (!isChecked) SettingsStore.setCursorHideDelay(this, 0L)
            else if (SettingsStore.cursorHideDelayMs == 0L) SettingsStore.setCursorHideDelay(this, 2500L)
            updateHideDelay(cursorHideDelayValue, cursorHideDelaySlider)
            ControlAccessibilityService.requestCursorForceVisible(true)
        }

        cursorHideDelaySlider.valueFrom = 1.0f
        cursorHideDelaySlider.valueTo = 5.0f
        cursorHideDelaySlider.stepSize = 0.5f
        cursorHideDelaySlider.value = (SettingsStore.cursorHideDelayMs / 1000f).coerceIn(1.0f, 5.0f)
        updateHideDelay(cursorHideDelayValue, cursorHideDelaySlider)
        cursorHideDelaySlider.addOnChangeListener { _, value, fromUser ->
            cursorHideDelayValue.text = getString(R.string.settings_cursor_hide_delay_value, value)
            if (fromUser) {
                SettingsStore.setCursorHideDelay(this, (value * 1000).toLong())
                ControlAccessibilityService.requestCursorForceVisible(true)
            }
        }
    }

    private fun updateHideDelay(label: TextView, slider: Slider) {
        label.text = getString(R.string.settings_cursor_hide_delay_value, slider.value)
    }

    override fun onPause() {
        super.onPause()
        ControlAccessibilityService.requestCursorForceVisible(false)
    }
    
    private fun SettingsStore.pointerSpeed(): Float {
        return TouchpadTuning.baseGain
    }
}
