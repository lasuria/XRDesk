package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.slider.Slider
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsDockActivity : BaseSettingsActivity() {

    private var switchBarLabelMap: Map<String, String> = emptyMap()
    private var switchBarIconMap: Map<String, android.graphics.drawable.Drawable> = emptyMap()
    private lateinit var switchBarSlotIcons: List<ImageView>
    private lateinit var switchBarSlotLabels: List<TextView>

    private val pickSwitchBarApp = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val packageName = data.getStringExtra(AppPickerActivity.EXTRA_PICK_PACKAGE) ?: return@registerForActivityResult
        val slotIndex = data.getIntExtra(AppPickerActivity.EXTRA_PICK_SLOT, -1)
        if (slotIndex !in 0..2) return@registerForActivityResult
        SwitchBarStore.setFavoriteSlot(this, slotIndex, packageName)
        refreshSwitchBarSlotLabels()
        ControlAccessibilityService.requestSwitchBarRefresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_dock)
        setupToolbar(R.id.settingsToolbar, getString(R.string.settings_category_dock_title))
        applyEdgeToEdge(findViewById(R.id.settingsDockRoot))

        val switchBarEnabledSwitch = findViewById<MaterialSwitch>(R.id.switchSwitchBarEnabled)
        val switchBarScaleSlider = findViewById<Slider>(R.id.sliderSwitchBarScale)
        val switchBarScaleValue = findViewById<TextView>(R.id.switchBarScaleValue)
        val switchBarSlot1 = findViewById<android.view.View>(R.id.switchBarSlot1)
        val switchBarSlot2 = findViewById<android.view.View>(R.id.switchBarSlot2)
        val switchBarSlot3 = findViewById<android.view.View>(R.id.switchBarSlot3)
        val switchBarSlotIcon1 = findViewById<ImageView>(R.id.switchBarSlotIcon1)
        val switchBarSlotIcon2 = findViewById<ImageView>(R.id.switchBarSlotIcon2)
        val switchBarSlotIcon3 = findViewById<ImageView>(R.id.switchBarSlotIcon3)
        val switchBarSlotLabel1 = findViewById<TextView>(R.id.switchBarSlotLabel1)
        val switchBarSlotLabel2 = findViewById<TextView>(R.id.switchBarSlotLabel2)
        val switchBarSlotLabel3 = findViewById<TextView>(R.id.switchBarSlotLabel3)
        
        switchBarSlotIcons = listOf(switchBarSlotIcon1, switchBarSlotIcon2, switchBarSlotIcon3)
        switchBarSlotLabels = listOf(switchBarSlotLabel1, switchBarSlotLabel2, switchBarSlotLabel3)

        val apps = LaunchableAppCatalog.load(this)
        switchBarLabelMap = apps.associate { it.packageName to it.label }
        switchBarIconMap = apps.associate { it.packageName to it.icon }
        refreshSwitchBarSlotLabels()

        switchBarEnabledSwitch.isChecked = SettingsStore.switchBarEnabled
        updateSwitchBarControlsEnabled(SettingsStore.switchBarEnabled, switchBarScaleSlider, switchBarScaleValue, listOf(switchBarSlot1, switchBarSlot2, switchBarSlot3))
        switchBarEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            SettingsStore.setSwitchBarEnabled(this, isChecked)
            updateSwitchBarControlsEnabled(isChecked, switchBarScaleSlider, switchBarScaleValue, listOf(switchBarSlot1, switchBarSlot2, switchBarSlot3))
            ControlAccessibilityService.requestSwitchBarForceVisible(true)
        }

        switchBarScaleSlider.valueFrom = 0.7f
        switchBarScaleSlider.valueTo = 1.3f
        switchBarScaleSlider.stepSize = 0.05f
        switchBarScaleSlider.value = snapToStep(SettingsStore.switchBarScale.coerceIn(0.7f, 1.3f), 0.7f, 0.05f)
        switchBarScaleValue.text = getString(R.string.settings_switch_bar_scale_value, (switchBarScaleSlider.value * 100).toInt())
        switchBarScaleSlider.addOnChangeListener { _, value, fromUser ->
            val snapped = snapToStep(value, 0.7f, 0.05f)
            switchBarScaleValue.text = getString(R.string.settings_switch_bar_scale_value, (snapped * 100).toInt())
            if (fromUser) {
                if (snapped != value) switchBarScaleSlider.value = snapped
                SettingsStore.setSwitchBarScale(this, snapped)
                ControlAccessibilityService.requestSwitchBarForceVisible(true)
            }
        }

        val slots = SwitchBarStore.getFavoriteSlots(this)
        listOf(switchBarSlot1, switchBarSlot2, switchBarSlot3).forEachIndexed { index, row ->
            row.setOnClickListener {
                ControlAccessibilityService.requestSwitchBarForceVisible(true)
                val currentPkg = slots.getOrNull(index)
                val intent = Intent(this, AppPickerActivity::class.java).apply {
                    putExtra(AppPickerActivity.EXTRA_PICK_MODE, true)
                    putExtra(AppPickerActivity.EXTRA_PICK_TITLE, getString(R.string.settings_switch_bar_app_add_slot, index + 1))
                    putExtra(AppPickerActivity.EXTRA_PICK_SLOT, index)
                    putExtra(AppPickerActivity.EXTRA_CURRENT_PACKAGE, currentPkg)
                }
                pickSwitchBarApp.launch(intent)
            }
        }
    }

    private fun refreshSwitchBarSlotLabels() {
        val slots = SwitchBarStore.getFavoriteSlots(this)
        val pm = packageManager
        switchBarSlotIcons.forEachIndexed { index, imageView ->
            val pkg = slots.getOrNull(index)
            val labelView = switchBarSlotLabels[index]
            if (pkg.isNullOrBlank()) {
                imageView.setImageResource(R.drawable.ic_add)
                labelView.text = getString(R.string.settings_switch_bar_app_pick_placeholder)
            } else {
                // Try map first, then direct PackageManager lookup
                val icon = switchBarIconMap[pkg] ?: try {
                    pm.getApplicationIcon(pkg)
                } catch (e: Exception) {
                    null
                }
                
                if (icon != null) {
                    imageView.setImageDrawable(icon)
                } else {
                    imageView.setImageResource(R.drawable.ic_add)
                }

                labelView.text = switchBarLabelMap[pkg] ?: try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
            }
        }
    }

    private fun updateSwitchBarControlsEnabled(enabled: Boolean, scaleSlider: Slider, scaleValue: TextView, slotRows: List<android.view.View>) {
        scaleSlider.isEnabled = enabled
        scaleValue.alpha = if (enabled) 1f else 0.4f
        slotRows.forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else 0.4f }
    }

    override fun onPause() {
        super.onPause()
        ControlAccessibilityService.requestSwitchBarForceVisible(false)
    }
}
