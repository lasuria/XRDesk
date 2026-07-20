package com.xrdesk

import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsDisplayActivity : BaseSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_display)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_category_display_title))
        applyEdgeToEdge(findViewById(R.id.settingsDisplayRoot))

        val keepScreenOnSwitch = findViewById<MaterialSwitch>(R.id.switchKeepScreenOn)
        val touchpadAutoDimSwitch = findViewById<MaterialSwitch>(R.id.switchTouchpadAutoDim)
        val autoDimSliderContainer = findViewById<android.view.View>(R.id.autoDimSliderContainer)
        val touchpadDimLevelValue = findViewById<TextView>(R.id.touchpadDimLevelValue)
        val touchpadDimLevelSlider = findViewById<Slider>(R.id.sliderTouchpadDimLevel)
        val touchpadAutoLockSwitch = findViewById<MaterialSwitch>(R.id.switchTouchpadAutoLock)
        val autoLockSliderContainer = findViewById<android.view.View>(R.id.autoLockSliderContainer)
        val touchpadAutoLockValue = findViewById<TextView>(R.id.touchpadAutoLockValue)
        val touchpadAutoLockSlider = findViewById<Slider>(R.id.sliderTouchpadAutoLock)

        keepScreenOnSwitch.isChecked = SettingsStore.keepScreenOn
        keepScreenOnSwitch.setOnCheckedChangeListener { _, isChecked -> SettingsStore.setKeepScreenOn(this, isChecked) }

        touchpadAutoDimSwitch.isChecked = SettingsStore.touchpadAutoDimEnabled
        autoDimSliderContainer.isVisible = SettingsStore.touchpadAutoDimEnabled
        touchpadAutoDimSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setTouchpadAutoDimEnabled(this, isChecked)
            autoDimSliderContainer.isVisible = isChecked
        }

        touchpadDimLevelSlider.valueFrom = 0.01f
        touchpadDimLevelSlider.valueTo = 0.15f
        touchpadDimLevelSlider.stepSize = 0.01f
        touchpadDimLevelSlider.value = SettingsStore.touchpadDimLevel.coerceIn(0.01f, 0.15f)
        touchpadDimLevelValue.text = getString(R.string.settings_touchpad_dim_level_value, (touchpadDimLevelSlider.value * 100).toInt())
        touchpadDimLevelSlider.addOnChangeListener { _, value, fromUser ->
            touchpadDimLevelValue.text = getString(R.string.settings_touchpad_dim_level_value, (value * 100).toInt())
            if (fromUser) SettingsStore.setTouchpadDimLevel(this, value)
        }

        // Auto-lock Timeout Setup
        val lockTimeouts = listOf(15_000L, 30_000L, 45_000L, 60_000L, 120_000L, 180_000L, 300_000L, 420_000L, 600_000L)
        val lockLabels = listOf(
            R.string.auto_lock_15s,
            R.string.auto_lock_30s,
            R.string.auto_lock_45s,
            R.string.auto_lock_1m,
            R.string.auto_lock_2m,
            R.string.auto_lock_3m,
            R.string.auto_lock_5m,
            R.string.auto_lock_7m,
            R.string.auto_lock_10m
        )

        touchpadAutoLockSwitch.isChecked = SettingsStore.touchpadAutoLockEnabled
        autoLockSliderContainer.isVisible = SettingsStore.touchpadAutoLockEnabled
        touchpadAutoLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setTouchpadAutoLockEnabled(this, isChecked)
            autoLockSliderContainer.isVisible = isChecked
        }

        touchpadAutoLockSlider.valueFrom = 0f
        touchpadAutoLockSlider.valueTo = (lockTimeouts.size - 1).toFloat()
        touchpadAutoLockSlider.stepSize = 1f
        
        val currentTimeout = SettingsStore.touchpadAutoLockTimeoutMs
        val currentIndex = lockTimeouts.indexOf(currentTimeout).coerceAtLeast(0)
        touchpadAutoLockSlider.value = currentIndex.toFloat()
        touchpadAutoLockValue.text = getString(lockLabels[currentIndex])

        touchpadAutoLockSlider.addOnChangeListener { _, value, fromUser ->
            val index = value.toInt().coerceIn(lockTimeouts.indices)
            touchpadAutoLockValue.text = getString(lockLabels[index])
            if (fromUser) {
                SettingsStore.setTouchpadAutoLockTimeout(this, lockTimeouts[index])
            }
        }
    }
}
