package com.xrdesk

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.xrdesk.databinding.ActivityThemeEditorBinding
import com.xrdesk.databinding.ItemColorPreferenceBinding
import com.xrdesk.databinding.ItemSwitchPreferenceBinding

/**
 * Activity for editing the Custom theme colors.
 * Supports monochrome mode and importing/exporting theme JSON.
 * Simplified to 8 key Material 3 / Pixel roles.
 */
class ThemeEditorActivity : BaseSettingsActivity() {

    private lateinit var binding: ActivityThemeEditorBinding
    private lateinit var customColors: ThemeColors

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar(R.id.settingsToolbar, getString(R.string.theme_editor_title))
        applyEdgeToEdge(binding.root)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.settingsToolbar)
        toolbar.inflateMenu(R.menu.theme_editor_menu)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_apply) {
                applyAndExit()
                true
            } else {
                false
            }
        }

        customColors = SettingsStore.getCustomThemeColors(this)
        
        setupMonochrome()
        populateCategories()
        updatePreview()

        binding.btnResetLight.setOnClickListener {
            customColors = ThemeEngine.getPreset(SettingsStore.THEME_LIGHT)
            applyLoadedColors()
        }
        binding.btnResetDark.setOnClickListener {
            customColors = ThemeEngine.getPreset(SettingsStore.THEME_DARK)
            applyLoadedColors()
        }
        binding.btnResetAmoled.setOnClickListener {
            customColors = ThemeEngine.getPreset(SettingsStore.THEME_AMOLED)
            applyLoadedColors()
        }
        binding.btnResetAll.setOnClickListener {
            customColors = ThemeEngine.getPreset(SettingsStore.THEME_LIGHT)
            applyLoadedColors()
        }

        binding.btnExport.setOnClickListener {
            val json = customColors.toJson()
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.theme_json_label), json))
            Toast.makeText(this, getString(R.string.theme_export_success), Toast.LENGTH_SHORT).show()
        }

        binding.btnImport.setOnClickListener {
            showImportDialog()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                save()
                XRDeskApp.recreateAllActivities()
                finish()
            }
        })
    }

    private fun showImportDialog() {
        val input = android.widget.EditText(this)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.theme_import_title)
            .setMessage(R.string.theme_import_hint)
            .setView(input)
            .setPositiveButton(R.string.theme_btn_import) { _, _ ->
                try {
                    customColors = ThemeColors.fromJson(input.text.toString())
                    applyLoadedColors()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.theme_import_error), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.color_picker_cancel, null)
            .show()
    }

    private fun applyLoadedColors() {
        binding.switchMonochrome.isChecked = customColors.isMonochrome
        populateCategories()
        updatePreview()
        save()
    }

    private fun setupMonochrome() {
        binding.switchMonochrome.isChecked = customColors.isMonochrome
        binding.switchMonochrome.setOnCheckedChangeListener { _, isChecked ->
            customColors.isMonochrome = isChecked
            populateCategories()
            updatePreview()
            save()
        }
    }

    private fun populateCategories() {
        binding.containerSurfaces.removeAllViews()
        binding.containerContent.removeAllViews()
        binding.containerAccent.removeAllViews()
        binding.containerSystemUI.removeAllViews()

        // Surfaces
        addColorItem(binding.containerSurfaces, getString(R.string.theme_item_background), customColors.background) { 
            customColors.background = it 
        }
        addColorItem(binding.containerSurfaces, getString(R.string.theme_item_cards), customColors.surface) { 
            customColors.surface = it 
        }

        // Content
        addColorItem(binding.containerContent, getString(R.string.theme_item_text_primary), customColors.textPrimary) { 
            customColors.textPrimary = it 
        }
        if (!customColors.isMonochrome) {
            addColorItem(binding.containerContent, getString(R.string.theme_item_text_secondary), customColors.textSecondary) { 
                customColors.textSecondary = it 
            }
            addColorItem(binding.containerContent, getString(R.string.theme_item_divider), customColors.divider) { 
                customColors.divider = it 
            }
        }

        // Accent
        if (!customColors.isMonochrome) {
            addColorItem(binding.containerAccent, getString(R.string.theme_item_accent_color), customColors.accent) { 
                customColors.accent = it 
            }
        }
        addColorItem(binding.containerAccent, getString(R.string.theme_item_accent_text), customColors.onAccent) { 
            customColors.onAccent = it 
        }

        // System UI
        addSwitchItem(binding.containerSystemUI, getString(R.string.theme_item_light_status_icons), customColors.lightStatusIcons) { 
            customColors.lightStatusIcons = it 
        }
        addSwitchItem(binding.containerSystemUI, getString(R.string.theme_item_light_nav_icons), customColors.lightNavIcons) { 
            customColors.lightNavIcons = it 
        }
    }

    private fun addColorItem(container: LinearLayout, title: String, color: Int, onSelected: (Int) -> Unit) {
        if (container.childCount > 0) {
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(1)
                ).apply {
                    marginStart = dpToPx(40)
                }
                setBackgroundColor(customColors.divider)
            }
            container.addView(divider)
        }

        val itemBinding = ItemColorPreferenceBinding.inflate(LayoutInflater.from(this), container, false)
        itemBinding.title.text = title
        itemBinding.colorPreview.setBackgroundColor(color)
        itemBinding.root.setOnClickListener {
            ColorPickerDialog(this, color) { newColor ->
                onSelected(newColor)
                itemBinding.colorPreview.setBackgroundColor(newColor)
                updatePreview()
                save()
            }.show()
        }
        container.addView(itemBinding.root)
    }

    private fun addSwitchItem(container: LinearLayout, title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
        if (container.childCount > 0) {
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(1)
                ).apply {
                    marginStart = dpToPx(40)
                }
                setBackgroundColor(customColors.divider)
            }
            container.addView(divider)
        }

        val itemBinding = ItemSwitchPreferenceBinding.inflate(LayoutInflater.from(this), container, false)
        itemBinding.title.text = title
        itemBinding.switchWidget.isChecked = checked
        itemBinding.switchWidget.setOnCheckedChangeListener { _, isChecked ->
            onChecked(isChecked)
            updatePreview()
            save()
        }
        itemBinding.root.setOnClickListener {
            itemBinding.switchWidget.toggle()
        }
        container.addView(itemBinding.root)
    }

    private fun updatePreview() {
        val colors = customColors.getEffectiveColors()
        val preview = binding.layoutPreview
        
        preview.previewCard.setCardBackgroundColor(colors.background)
        preview.previewCard.strokeColor = colors.divider
        
        preview.previewToolbar.setBackgroundColor(colors.background)
        preview.previewToolbar.setTitleTextColor(colors.textPrimary)
        
        preview.previewContentCard.setCardBackgroundColor(colors.surface)
        preview.previewContentCard.strokeColor = colors.divider
        
        preview.previewTitle.setTextColor(colors.textPrimary)
        preview.previewSubtitle.setTextColor(colors.textSecondary)
        preview.previewDivider.setBackgroundColor(colors.divider)
        
        preview.previewIconPrimary.imageTintList = ColorStateList.valueOf(colors.textPrimary)
        preview.previewLabelPrimary.setTextColor(colors.textPrimary)
        
        preview.previewIconSecondary.imageTintList = ColorStateList.valueOf(colors.textSecondary)
        preview.previewLabelSecondary.setTextColor(colors.textSecondary)
        
        preview.previewButton.backgroundTintList = ColorStateList.valueOf(colors.accent)
        preview.previewButton.setTextColor(colors.onAccent)
        
        preview.previewSwitch.thumbTintList = ColorStateList.valueOf(colors.accent)
    }

    private fun save() {
        ThemeEngine.updateCustomColors(this, customColors)
    }

    private fun applyAndExit() {
        save()
        XRDeskApp.recreateAllActivities()
        finish()
    }
}
