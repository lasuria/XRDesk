package com.xrdesk

import android.os.Bundle
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsTouchpadActivity : BaseSettingsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_touchpad)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_category_touchpad_title))
        applyEdgeToEdge(findViewById(R.id.settingsTouchpadRoot))

        val keepScreenOnSwitch = findViewById<MaterialSwitch>(R.id.switchKeepScreenOn)
        val touchpadAutoDimSwitch = findViewById<MaterialSwitch>(R.id.switchTouchpadAutoDim)
        val autoDimSliderContainer = findViewById<android.view.View>(R.id.autoDimSliderContainer)
        val touchpadDimLevelValue = findViewById<TextView>(R.id.touchpadDimLevelValue)
        val touchpadDimLevelSlider = findViewById<Slider>(R.id.sliderTouchpadDimLevel)
        val touchpadAutoFocusSwitch = findViewById<MaterialSwitch>(R.id.switchTouchpadAutoFocus)
        val touchpadScrollInvertSwitch = findViewById<MaterialSwitch>(R.id.switchTouchpadScrollInvert)
        val touchpadScrollSpeedValue = findViewById<TextView>(R.id.touchpadScrollSpeedValue)
        val touchpadScrollSpeedSlider = findViewById<Slider>(R.id.sliderTouchpadScrollSpeed)
        val touchpadScrollDistanceValue = findViewById<TextView>(R.id.touchpadScrollDistanceValue)
        val touchpadScrollDistanceSlider = findViewById<Slider>(R.id.sliderTouchpadScrollDistance)
        val touchpadScrollGestureSwitch = findViewById<MaterialSwitch>(R.id.switchTouchpadScrollGesture)
        val touchpadScrollGestureGainValue = findViewById<TextView>(R.id.touchpadScrollGestureGainValue)
        val touchpadScrollGestureGainSlider = findViewById<Slider>(R.id.sliderTouchpadScrollGestureGain)
        val touchpadScrollGestureStepValue = findViewById<TextView>(R.id.touchpadScrollGestureStepValue)
        val touchpadScrollGestureStepSlider = findViewById<Slider>(R.id.sliderTouchpadScrollGestureStep)
        val touchpadDragBoostValue = findViewById<TextView>(R.id.touchpadDragBoostValue)
        val touchpadDragBoostSlider = findViewById<Slider>(R.id.sliderTouchpadDragBoost)
        val dpadDropdown = findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.dpadDropdown)
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

        touchpadAutoFocusSwitch.isChecked = SettingsStore.touchpadAutoFocusEnabled
        touchpadAutoFocusSwitch.setOnCheckedChangeListener { _, isChecked -> SettingsStore.setTouchpadAutoFocusEnabled(this, isChecked) }

        touchpadScrollInvertSwitch.isChecked = SettingsStore.touchpadScrollInverted
        touchpadScrollInvertSwitch.setOnCheckedChangeListener { _, isChecked -> SettingsStore.setTouchpadScrollInverted(this, isChecked) }

        touchpadScrollSpeedSlider.valueFrom = 0.5f
        touchpadScrollSpeedSlider.valueTo = 3.0f
        touchpadScrollSpeedSlider.stepSize = 0.1f
        touchpadScrollSpeedSlider.value = snapToStep(SettingsStore.touchpadScrollSpeed.coerceIn(0.5f, 3.0f), 0.5f, 0.1f)
        touchpadScrollSpeedValue.text = getString(R.string.settings_touchpad_scroll_speed_value, touchpadScrollSpeedSlider.value)
        touchpadScrollSpeedSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 0.5f, 0.1f)
            touchpadScrollSpeedValue.text = getString(R.string.settings_touchpad_scroll_speed_value, snapped)
            if (fromUser) {
                if (snapped != value) touchpadScrollSpeedSlider.value = snapped
                SettingsStore.setTouchpadScrollSpeed(this, snapped)
            }
        }

        touchpadScrollDistanceSlider.valueFrom = 3.0f
        touchpadScrollDistanceSlider.valueTo = 12.0f
        touchpadScrollDistanceSlider.stepSize = 0.5f
        touchpadScrollDistanceSlider.value = snapToStep(SettingsStore.touchpadScrollStepDp.coerceIn(3.0f, 12.0f), 3.0f, 0.5f)
        touchpadScrollDistanceValue.text = getString(R.string.settings_touchpad_scroll_distance_value, touchpadScrollDistanceSlider.value)
        touchpadScrollDistanceSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 3.0f, 0.5f)
            touchpadScrollDistanceValue.text = getString(R.string.settings_touchpad_scroll_distance_value, snapped)
            if (fromUser) {
                if (snapped != value) touchpadScrollDistanceSlider.value = snapped
                SettingsStore.setTouchpadScrollStepDp(this, snapped)
            }
        }

        touchpadScrollGestureSwitch.isChecked = SettingsStore.touchpadDirectScrollGestureEnabled
        val updateScrollModeUiState: (Boolean) -> Unit = { directEnabled ->
            touchpadScrollGestureGainSlider.isEnabled = directEnabled
            touchpadScrollGestureStepSlider.isEnabled = directEnabled
            touchpadScrollGestureGainValue.alpha = if (directEnabled) 1f else 0.5f
            touchpadScrollGestureStepValue.alpha = if (directEnabled) 1f else 0.5f
            val classicEnabled = !directEnabled
            touchpadScrollSpeedSlider.isEnabled = classicEnabled
            touchpadScrollDistanceSlider.isEnabled = classicEnabled
            touchpadScrollInvertSwitch.isEnabled = classicEnabled
            touchpadScrollSpeedValue.alpha = if (classicEnabled) 1f else 0.5f
            touchpadScrollDistanceValue.alpha = if (classicEnabled) 1f else 0.5f
        }
        updateScrollModeUiState(touchpadScrollGestureSwitch.isChecked)
        touchpadScrollGestureSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setTouchpadDirectScrollGestureEnabled(this, isChecked)
            updateScrollModeUiState(isChecked)
        }

        touchpadScrollGestureGainSlider.valueFrom = 0.5f
        touchpadScrollGestureGainSlider.valueTo = 2.5f
        touchpadScrollGestureGainSlider.stepSize = 0.1f
        touchpadScrollGestureGainSlider.value = snapToStep(SettingsStore.touchpadDirectScrollGain.coerceIn(0.5f, 2.5f), 0.5f, 0.1f)
        touchpadScrollGestureGainValue.text = getString(R.string.settings_touchpad_scroll_gesture_gain_value, touchpadScrollGestureGainSlider.value)
        touchpadScrollGestureGainSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 0.5f, 0.1f)
            touchpadScrollGestureGainValue.text = getString(R.string.settings_touchpad_scroll_gesture_gain_value, snapped)
            if (fromUser) {
                if (snapped != value) touchpadScrollGestureGainSlider.value = snapped
                SettingsStore.setTouchpadDirectScrollGain(this, snapped)
            }
        }

        touchpadScrollGestureStepSlider.valueFrom = 16.0f
        touchpadScrollGestureStepSlider.valueTo = 80.0f
        touchpadScrollGestureStepSlider.stepSize = 2.0f
        touchpadScrollGestureStepSlider.value = snapToStep(SettingsStore.touchpadDirectScrollStepDp.coerceIn(16.0f, 80.0f), 16.0f, 2.0f)
        touchpadScrollGestureStepValue.text = getString(R.string.settings_touchpad_scroll_gesture_step_value, touchpadScrollGestureStepSlider.value)
        touchpadScrollGestureStepSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 16.0f, 2.0f)
            touchpadScrollGestureStepValue.text = getString(R.string.settings_touchpad_scroll_gesture_step_value, snapped)
            if (fromUser) {
                if (snapped != value) touchpadScrollGestureStepSlider.value = snapped
                SettingsStore.setTouchpadDirectScrollStepDp(this, snapped)
            }
        }

        touchpadDragBoostSlider.valueFrom = 0.8f
        touchpadDragBoostSlider.valueTo = 2.0f
        touchpadDragBoostSlider.stepSize = 0.1f
        touchpadDragBoostSlider.value = snapToStep(TouchpadTuning.dragBoost.coerceIn(0.8f, 2.0f), 0.8f, 0.1f)
        touchpadDragBoostValue.text = getString(R.string.settings_touchpad_drag_boost_value, touchpadDragBoostSlider.value)
        touchpadDragBoostSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 0.8f, 0.1f)
            touchpadDragBoostValue.text = getString(R.string.settings_touchpad_drag_boost_value, snapped)
            if (fromUser) {
                if (snapped != value) touchpadDragBoostSlider.value = snapped
                SettingsStore.setTouchpadDragBoost(this, snapped)
            }
        }

        // D-Pad Position Setup
        val dpadOptions = listOf(
            SettingsStore.DPAD_HIDDEN to getString(R.string.dpad_pos_hidden),
            SettingsStore.DPAD_ABOVE to getString(R.string.dpad_pos_above),
            SettingsStore.DPAD_BELOW to getString(R.string.dpad_pos_below)
        )
        val dpadNames = dpadOptions.map { it.second }
        val dpadAdapter = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, dpadNames) {
            override fun getFilter() = object : android.widget.Filter() {
                override fun performFiltering(constraint: CharSequence?) = FilterResults().apply { values = dpadNames; count = dpadNames.size }
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) { notifyDataSetChanged() }
            }
        }
        dpadDropdown.setAdapter(dpadAdapter)
        dpadDropdown.setText(dpadOptions.find { it.first == SettingsStore.dPadPosition }?.second ?: dpadOptions[0].second, false)
        dpadDropdown.setOnItemClickListener { _, _, position, _ ->
            SettingsStore.setDPadPosition(this, dpadOptions[position].first)
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
