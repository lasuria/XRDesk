package com.xrdesk

import android.os.Bundle
import android.widget.ArrayAdapter
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class SettingsHUDActivity : BaseSettingsActivity() {

    private var previewController: StatusPanelController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_hud)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_hud_title))
        applyEdgeToEdge(findViewById(R.id.settingsHUDRoot))

        val switchHudEnabled = findViewById<MaterialSwitch>(R.id.switchHudEnabled)
        val hudSettingsContainer = findViewById<View>(R.id.hudSettingsContainer)
        val switchAppNotifications = findViewById<MaterialSwitch>(R.id.switchAppNotifications)
        val notificationDurationContainer = findViewById<View>(R.id.notificationDurationContainer)
        val durationToggleGroup = findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.notificationDurationToggleGroup)
        val modeDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.modeDropdown)
        val positionDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.positionDropdown)
        val posContainer = findViewById<View>(R.id.positionContainer)
        val sliderSize = findViewById<Slider>(R.id.sliderHudSize)
        val sliderZone = findViewById<Slider>(R.id.sliderHudZone)
        val sliderDelay = findViewById<Slider>(R.id.sliderHudDelay)
        val tvSizeLabel = findViewById<TextView>(R.id.tvHudSizeLabel)

        // 1. App Notifications (Now at the top)
        switchAppNotifications.isChecked = SettingsStore.appNotificationsEnabled
        notificationDurationContainer.visibility = if (SettingsStore.appNotificationsEnabled) View.VISIBLE else View.GONE
        switchAppNotifications.setOnCheckedChangeListener { _, enabled ->
            SettingsStore.setAppNotificationsEnabled(this, enabled)
            SettingsStore.setHudNotificationsEnabled(this, enabled)
            notificationDurationContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        }

        if (SettingsStore.appNotificationDuration == 1) {
            durationToggleGroup.check(R.id.btnDurationLong)
        } else {
            durationToggleGroup.check(R.id.btnDurationShort)
        }

        durationToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val duration = if (checkedId == R.id.btnDurationLong) 1 else 0
                SettingsStore.setAppNotificationDuration(this, duration)
            }
        }

        // 2. HUD Enabled Master Toggle
        switchHudEnabled.isChecked = SettingsStore.hudEnabled
        hudSettingsContainer.visibility = if (SettingsStore.hudEnabled) View.VISIBLE else View.GONE
        switchHudEnabled.setOnCheckedChangeListener { _, enabled ->
            SettingsStore.setHudEnabled(this, enabled)
            hudSettingsContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        }

        // 2. HUD Modes
        val modes = listOf(
            SettingsStore.HUD_MODE_FULL_INFO to getString(R.string.hud_mode_detailed),
            SettingsStore.HUD_MODE_COMPACT_BAR to getString(R.string.hud_mode_compact)
        )
        modeDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modes.map { it.second }))
        val currentMode = if (SettingsStore.hudMode == SettingsStore.HUD_MODE_FULL_INFO) 
            SettingsStore.HUD_MODE_FULL_INFO else SettingsStore.HUD_MODE_COMPACT_BAR
        modeDropdown.setText(modes.find { it.first == currentMode }?.second, false)
        modeDropdown.setOnItemClickListener { _, _, i, _ ->
            val mode = modes[i].first
            SettingsStore.setHudMode(this, mode)
            updatePositionDropdown(mode, positionDropdown, posContainer)
            previewController?.refreshUI()
        }

        // 3. Sliders
        fun updateSizeLabel(v: Float) {
            tvSizeLabel.text = getString(R.string.settings_hud_size_label, v.toInt())
        }
        
        // Fix: Snap initial value to step size to avoid Slider crash
        val initialSize = snapToStep(SettingsStore.hudSizeDp.coerceIn(60f, 120f), 60f, 5f)
        sliderSize.value = initialSize
        updateSizeLabel(initialSize)
        
        sliderSize.addOnChangeListener { _, v, _ -> 
            SettingsStore.setHudSize(this, v)
            updateSizeLabel(v)
            previewController?.refreshUI()
        }
        
        sliderZone.value = SettingsStore.hudActivationZoneDp.coerceIn(4f, 64f)
        sliderZone.addOnChangeListener { _, v, _ -> SettingsStore.setHudActivationZone(this, v) }
        
        // Fix: Snap initial value to step size for delay
        val initialDelay = snapToStep(SettingsStore.hudHideDelayMs.toFloat().coerceIn(1000f, 10000f), 1000f, 500f)
        sliderDelay.value = initialDelay
        sliderDelay.addOnChangeListener { _, v, _ -> SettingsStore.setHudHideDelay(this, v.toLong()) }

        findViewById<View>(R.id.btnResetHUD).setOnClickListener {
            SettingsStore.resetHUDSettings(this)
            recreate()
        }

        updatePositionDropdown(SettingsStore.hudMode, positionDropdown, posContainer)
        setupPreview()
    }

    private fun updatePositionDropdown(mode: Int, dropdown: MaterialAutoCompleteTextView, container: View) {
        val showPos = mode != SettingsStore.HUD_MODE_FULL_INFO
        container.visibility = if (showPos) View.VISIBLE else View.GONE
        
        if (showPos) {
            val options = listOf(SettingsStore.HUD_POS_TOP to getString(R.string.settings_hud_edge_top), 
                                SettingsStore.HUD_POS_BOTTOM to getString(R.string.settings_hud_edge_bottom))
            
            dropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options.map { it.second }))
            
            val current = options.find { it.first == SettingsStore.hudPosition } ?: options[0]
            dropdown.setText(current.second, false)
            dropdown.setOnItemClickListener { _, _, i, _ ->
                SettingsStore.setHudPosition(this, options[i].first)
                previewController?.refreshUI()
            }
        }
    }

    private fun setupPreview() {
        val monitorFrame = findViewById<FrameLayout>(R.id.previewMonitorFrame)
        val area = findViewById<FrameLayout>(R.id.panelPreviewArea)
        monitorFrame.post {
            val width = monitorFrame.width
            val height = (width * 9 / 16)
            monitorFrame.layoutParams = monitorFrame.layoutParams.apply { this.height = height }
            // Force a small delay to ensure layout pass finished
            area.post {
                previewController = StatusPanelController(this, PreviewViewGroupContainer(area), isPreview = true)
                previewController?.show(immediate = true)
            }
        }
    }

    override fun onDestroy() {
        previewController?.destroy()
        super.onDestroy()
    }
}
