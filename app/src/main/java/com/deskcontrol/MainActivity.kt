package com.deskcontrol

import android.content.Intent
import android.os.Bundle
import android.animation.ObjectAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.deskcontrol.databinding.ActivityMainBinding
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
        DiagnosticsLog.add("Main: create displayId=${display?.displayId ?: -1}")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeHelper.applyTheme(this)
        applyEdgeToEdgePadding(binding.root)

        binding.btnPickApp.setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
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

        if (displayType == XrDeviceDetector.ExternalDisplayType.NONE) {
            binding.iconDisplay.setImageResource(R.drawable.ic_xr_display)
            binding.statusDisplayValue.text = getString(R.string.external_display_not_connected)
            binding.deviceStatusLabel.isVisible = false
            
            // Apply neutral tint to the monitor icon
            val color = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
            binding.iconDisplay.setColorFilter(color)
        } else {
            // Pick the icon based on detected type
            if (displayType == XrDeviceDetector.ExternalDisplayType.XR_GLASSES) {
                binding.iconDisplay.setImageResource(R.drawable.ic_xr_glasses)
                binding.deviceStatusLabel.text = "XR Glasses Connected"
            } else {
                binding.iconDisplay.setImageResource(R.drawable.ic_external_monitor)
                binding.deviceStatusLabel.text = "External monitor connected"
            }
            
            // Format resolution with descriptive labels
            val resolutionText = availableDisplays.joinToString("\n") { 
                formatResolution(it.width, it.height)
            }
            binding.statusDisplayValue.text = resolutionText
            binding.statusDisplayValue.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
            
            binding.deviceStatusLabel.isVisible = true
            
            // Reset tint to allow mono vector handling
            binding.iconDisplay.setColorFilter(null)
            binding.iconDisplay.imageTintList = null
        }
    }

    private fun formatResolution(width: Int, height: Int): String {
        return when {
            width >= 3840 || height >= 2160 -> "4K (UltraHD)"
            width >= 2560 || height >= 1440 -> "2K (QHD)"
            width >= 1920 || height >= 1080 -> "1080p (FullHD)"
            width >= 1280 || height >= 720 -> "720p (HD)"
            else -> "${width}×${height}"
        }
    }

    private fun updateAccessibilityState() {
        val accessibilityEnabled = ControlAccessibilityService.isEnabled(this)
        
        binding.accessibilityContainer.isVisible = !accessibilityEnabled
        binding.accessibilityDivider.isVisible = !accessibilityEnabled
        
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
