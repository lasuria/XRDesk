package com.xrdesk

import android.os.Bundle
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

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
        val cursorHideSwitch = findViewById<MaterialSwitch>(R.id.switchCursorHide)
        val cursorHideOptions = findViewById<android.view.View>(R.id.cursorHideOptions)
        val cursorHideDelayValue = findViewById<TextView>(R.id.cursorHideDelayValue)
        val cursorHideDelaySlider = findViewById<Slider>(R.id.sliderCursorHideDelay)

        cursorSizeSlider.valueFrom = 0.5f
        cursorSizeSlider.valueTo = 2.0f
        cursorSizeSlider.stepSize = 0.1f
        cursorSizeSlider.value = snapToStep(SettingsStore.cursorScale.coerceIn(0.5f, 2.0f), 0.5f, 0.1f)
        cursorSizeValue.text = getString(R.string.settings_cursor_scale_value, cursorSizeSlider.value)
        cursorSizeSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 0.5f, 0.1f)
            cursorSizeValue.text = getString(R.string.settings_cursor_scale_value, snapped)
            if (fromUser) {
                if (snapped != value) cursorSizeSlider.value = snapped
                SettingsStore.setCursorScale(this, snapped)
            }
        }

        if (SettingsStore.cursorColor == 0xFFFFFFFF.toInt()) {
            cursorColorToggleGroup.check(R.id.btnColorWhite)
        } else {
            cursorColorToggleGroup.check(R.id.btnColorBlack)
        }
        cursorColorToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val color = if (checkedId == R.id.btnColorWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                SettingsStore.setCursorColor(this, color)
            }
        }

        cursorOpacitySlider.valueFrom = 0.2f
        cursorOpacitySlider.valueTo = 1.0f
        cursorOpacitySlider.stepSize = 0.05f
        cursorOpacitySlider.value = snapToStep(SettingsStore.cursorAlpha.coerceIn(0.2f, 1.0f), 0.2f, 0.05f)
        cursorOpacityValue.text = getString(R.string.settings_cursor_opacity_value, (cursorOpacitySlider.value * 100).toInt())
        cursorOpacitySlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 0.2f, 0.05f)
            cursorOpacityValue.text = getString(R.string.settings_cursor_opacity_value, (snapped * 100).toInt())
            if (fromUser) {
                if (snapped != value) cursorOpacitySlider.value = snapped
                SettingsStore.setCursorAlpha(this, snapped)
            }
        }

        cursorSpeedSlider.valueFrom = 0.5f
        cursorSpeedSlider.valueTo = 2.0f
        cursorSpeedSlider.stepSize = 0.1f
        cursorSpeedSlider.value = snapToStep(TouchpadTuning.emaAlpha.coerceIn(0.5f, 2.0f), 0.5f, 0.1f)
        cursorSpeedValue.text = getString(R.string.settings_cursor_speed_value, cursorSpeedSlider.value)
        cursorSpeedSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 0.5f, 0.1f)
            cursorSpeedValue.text = getString(R.string.settings_cursor_speed_value, snapped)
            if (fromUser) {
                if (snapped != value) cursorSpeedSlider.value = snapped
                SettingsStore.setTouchpadSmoothing(this, snapped)
            }
        }

        val currentDelay = SettingsStore.cursorHideDelayMs
        cursorHideSwitch.isChecked = currentDelay > 0
        cursorHideOptions.visibility = if (currentDelay > 0) android.view.View.VISIBLE else android.view.View.GONE
        cursorHideSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                cursorHideOptions.visibility = android.view.View.VISIBLE
                SettingsStore.setCursorHideDelay(this, 2500L)
                cursorHideDelaySlider.value = 2.5f
                cursorHideDelayValue.text = getString(R.string.settings_cursor_hide_delay_value, 2.5f)
            } else {
                cursorHideOptions.visibility = android.view.View.GONE
                SettingsStore.setCursorHideDelay(this, 0L)
            }
        }

        cursorHideDelaySlider.valueFrom = 1.0f
        cursorHideDelaySlider.valueTo = 5.0f
        cursorHideDelaySlider.stepSize = 0.5f
        val delayVal = if (currentDelay > 0) currentDelay / 1000f else 2.5f
        cursorHideDelaySlider.value = snapToStep(delayVal, 1.0f, 0.5f)
        cursorHideDelayValue.text = getString(R.string.settings_cursor_hide_delay_value, cursorHideDelaySlider.value)
        cursorHideDelaySlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 1.0f, 0.5f)
            cursorHideDelayValue.text = getString(R.string.settings_cursor_hide_delay_value, snapped)
            if (fromUser) {
                if (snapped != value) cursorHideDelaySlider.value = snapped
                SettingsStore.setCursorHideDelay(this, (snapped * 1000).toLong())
            }
        }
    }
}
