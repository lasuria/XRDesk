package com.xrdesk

import android.content.Intent
import android.os.Bundle
import android.animation.ObjectAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.xrdesk.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity(), DisplaySessionManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private var externalDisplayConnected = false
    private var availableDisplays: List<DisplaySessionManager.ExternalDisplayInfo> = emptyList()
    private var selectedDisplayId: Int? = null
    private var lastSelectedDisplayId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        DiagnosticsLog.add("Main", "Main: create displayId=${display?.displayId ?: -1}")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeHelper.applyTheme(this)
        applyEdgeToEdgePadding(binding.root)
        
        // Pre-load app catalog to prevent stutters in Dock/Settings
        LaunchableAppCatalog.preLoad(this)

        binding.btnTouchpad.setOnClickListener {
            startActivity(Intent(this, TouchpadActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        DisplaySessionManager.addListener(this)
        updateAccessibilityState()
    }

    override fun onStop() {
        super.onStop()
        DisplaySessionManager.removeListener(this)
    }

    override fun onDisplayChanged(info: DisplaySessionManager.ExternalDisplayInfo?) {
        externalDisplayConnected = info != null
        updateDisplayInfoUI()
        updateSecondaryActions()
    }

    override fun onDisplaysUpdated(
        displays: List<DisplaySessionManager.ExternalDisplayInfo>,
        selectedDisplayId: Int?
    ) {
        availableDisplays = displays
        this.selectedDisplayId = selectedDisplayId
        updateDisplayInfoUI()
        updateDisplaySelector()
    }

    private fun updateDisplayInfoUI() {
        val displayType = XrDeviceDetector.getExternalDisplayType(this)
        val colors = ThemeEngine.getColors()
        val primaryColor = colors.textPrimary

        // Reset all labels to 100% Alpha and theme's Primary Text
        binding.statusDisplayValue.setTextColor(primaryColor)
        binding.statusDisplayValue.alpha = 1.0f
        binding.deviceStatusLabel.setTextColor(primaryColor)
        binding.deviceStatusLabel.alpha = 1.0f
        binding.labelTouchpad.setTextColor(primaryColor)
        binding.labelTouchpad.alpha = 1.0f
        binding.labelSettings.setTextColor(primaryColor)
        binding.labelSettings.alpha = 1.0f

        if (displayType == XrDeviceDetector.ExternalDisplayType.NONE) {
            binding.iconDisplay.setImageResource(R.drawable.monitor_disconnected)
            binding.statusDisplayValue.text = getString(R.string.external_display_not_connected)
            binding.statusDisplayValue.textSize = 20f
            binding.deviceStatusLabel.isVisible = false
            
            // Apply Gray (Secondary) tint when disconnected
            binding.iconDisplay.setColorFilter(colors.textSecondary)
        } else {
            // 1. Primary Line: Device Model or Generic Type (BIGGER)
            val deviceName = XrDeviceDetector.getDetectedDeviceName()
            val primaryText = if (displayType == XrDeviceDetector.ExternalDisplayType.XR_GLASSES) {
                binding.iconDisplay.setImageResource(R.drawable.glasses_connected)
                deviceName ?: getString(R.string.display_type_xr_glasses)
            } else {
                binding.iconDisplay.setImageResource(R.drawable.monitor_connected)
                getString(R.string.display_type_external)
            }
            binding.statusDisplayValue.text = primaryText
            binding.statusDisplayValue.textSize = 20f
            binding.statusDisplayValue.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)

            // 2. Secondary Line: Capabilities (SMALLER)
            val info = availableDisplays.firstOrNull { it.displayId == selectedDisplayId }
            if (info != null) {
                val components = mutableListOf<String>()
                components.add(formatResolution(info.width, info.height))
                if (info.refreshRate > 0) {
                    components.add(getString(R.string.display_capability_refresh_rate, info.refreshRate.toInt()))
                }
                if (info.isHdr) {
                    components.add(getString(R.string.display_capability_hdr))
                }
                binding.deviceStatusLabel.text = components.joinToString(" • ")
            } else {
                binding.deviceStatusLabel.text = getString(R.string.external_display_connected)
            }
            
            binding.deviceStatusLabel.textSize = 12f
            binding.deviceStatusLabel.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            binding.deviceStatusLabel.isVisible = true
            
            // Apply Green (Success) tint when connected
            binding.iconDisplay.setColorFilter(colors.colorSuccess)
        }
    }

    private fun formatResolution(width: Int, height: Int): String {
        return when {
            width >= 3840 || height >= 2160 -> "4K"
            width >= 2560 || height >= 1440 -> "1440p"
            width >= 1920 || height >= 1080 -> "1080p"
            width >= 1280 || height >= 720 -> "720p"
            else -> "${width}×${height}"
        }
    }

    private fun updateAccessibilityState() {
        val accessibilityEnabled = ControlAccessibilityService.isEnabled(this)
        
        binding.accessibilityContainer.isVisible = !accessibilityEnabled
        
        if (!accessibilityEnabled) {
            binding.statusAccessibilityValue.text = getString(R.string.accessibility_required)
            binding.statusAccessibilityValue.setTextColor(getColor(R.color.apple_red))
            binding.statusAccessibilityValue.setTypeface(null, android.graphics.Typeface.BOLD)
            
            val color = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
            binding.iconAccessibility.setColorFilter(color)
        }
    }

    private fun updateSecondaryActions() {
        binding.btnTouchpad.isEnabled = true
        binding.btnTouchpad.alpha = 1f
    }

    private fun updateDisplaySelector() {
        val showSelector = availableDisplays.size > 1
        binding.displaySelector.isVisible = showSelector
        if (!showSelector) return

        val items = availableDisplays.take(3)
        binding.displaySelectorRow.removeAllViews()
        val selectedPrimary = MaterialColors.getColor(
            binding.displaySelectorRow,
            com.google.android.material.R.attr.colorOnSurface,
            0
        )
        val unselectedPrimary = MaterialColors.getColor(
            binding.displaySelectorRow,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            0
        )
        val secondaryColor = MaterialColors.getColor(
            binding.displaySelectorRow,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            0
        )
        items.forEachIndexed { index, display ->
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { DisplaySessionManager.setSelectedDisplayId(display.displayId) }
            }
            val primary = MaterialTextView(this).apply {
                text = getString(R.string.display_selector_title, index + 1)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                gravity = android.view.Gravity.CENTER
            }
            val secondary = MaterialTextView(this).apply {
                text = getString(R.string.display_selector_resolution, display.width, display.height)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                setTextColor(secondaryColor)
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                gravity = android.view.Gravity.CENTER
            }
            val textParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            container.addView(primary, textParams)
            container.addView(secondary, textParams)
            container.setPadding(0, dpToPx(6), 0, dpToPx(6))
            val params = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            binding.displaySelectorRow.addView(container, params)
            primary.setTextColor(
                if (display.displayId == selectedDisplayId) selectedPrimary else unselectedPrimary
            )
        }

        binding.displaySelector.doOnLayout {
            if (items.isEmpty()) return@doOnLayout
            val contentWidth = it.width - it.paddingStart - it.paddingEnd
            val segmentWidth = contentWidth / items.size
            val highlightParams = binding.displaySelectorHighlight.layoutParams
            if (highlightParams.width != segmentWidth) {
                highlightParams.width = segmentWidth
                binding.displaySelectorHighlight.layoutParams = highlightParams
            }
            val selectedIndex = items.indexOfFirst { display ->
                display.displayId == selectedDisplayId
            }.coerceAtLeast(0)
            val targetX = segmentWidth * selectedIndex.toFloat()
            binding.displaySelectorHighlight.animate().cancel()
            if (lastSelectedDisplayId == null) {
                binding.displaySelectorHighlight.translationX = targetX
            } else {
                ObjectAnimator.ofFloat(
                    binding.displaySelectorHighlight,
                    "translationX",
                    binding.displaySelectorHighlight.translationX,
                    targetX
                ).apply {
                    duration = 160
                    interpolator = FastOutSlowInInterpolator()
                }.start()
            }
            lastSelectedDisplayId = selectedDisplayId
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
