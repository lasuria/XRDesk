package com.xrdesk

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import com.xrdesk.databinding.ActivityThemeEditorBinding
import com.xrdesk.databinding.ItemColorPreferenceBinding
import com.xrdesk.databinding.ItemSwitchPreferenceBinding

/**
 * Activity for editing the Custom theme colors.
 * Supports monochrome mode and importing/exporting theme JSON.
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
        binding.btnResetMaterialYou.setOnClickListener {
            customColors = ThemeEngine.getPreset(SettingsStore.THEME_MATERIAL_YOU)
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
        binding.containerBackground.removeAllViews()
        binding.containerSurfaces.removeAllViews()
        binding.containerText.removeAllViews()
        binding.containerIcons.removeAllViews()
        binding.containerAccent.removeAllViews()
        binding.containerDividers.removeAllViews()
        binding.containerSystemUI.removeAllViews()
        binding.containerExtra.removeAllViews()

        // Background
        addColorItem(binding.containerBackground, getString(R.string.theme_item_background), customColors.background) { 
            customColors.background = it 
        }

        // Surfaces
        addColorItem(binding.containerSurfaces, getString(R.string.theme_item_cards), customColors.surfaceCard) { customColors.surfaceCard = it }
        addColorItem(binding.containerSurfaces, getString(R.string.theme_item_dialogs), customColors.surfaceDialog) { customColors.surfaceDialog = it }
        addColorItem(binding.containerSurfaces, getString(R.string.theme_item_bottom_sheets), customColors.surfaceBottomSheet) { customColors.surfaceBottomSheet = it }
        addColorItem(binding.containerSurfaces, getString(R.string.theme_item_toolbar), customColors.surfaceToolbar) { customColors.surfaceToolbar = it }

        // Text
        addColorItem(binding.containerText, getString(R.string.theme_item_text_primary), customColors.textPrimary) { customColors.textPrimary = it }
        if (!customColors.isMonochrome) {
            addColorItem(binding.containerText, getString(R.string.theme_item_text_secondary), customColors.textSecondary) { customColors.textSecondary = it }
            addColorItem(binding.containerText, getString(R.string.theme_item_text_disabled), customColors.textDisabled) { customColors.textDisabled = it }
        }

        // Icons
        if (!customColors.isMonochrome) {
            addColorItem(binding.containerIcons, getString(R.string.theme_item_icon_primary), customColors.iconPrimary) { customColors.iconPrimary = it }
            addColorItem(binding.containerIcons, getString(R.string.theme_item_icon_secondary), customColors.iconSecondary) { customColors.iconSecondary = it }
            addColorItem(binding.containerIcons, getString(R.string.theme_item_icon_disabled), customColors.iconDisabled) { customColors.iconDisabled = it }
        }

        // Accent
        if (!customColors.isMonochrome) {
            addColorItem(binding.containerAccent, getString(R.string.theme_item_accent_color), customColors.accentColor) { customColors.accentColor = it }
        }
        addColorItem(binding.containerAccent, getString(R.string.theme_item_accent_text), customColors.accentText) { customColors.accentText = it }

        // Dividers
        if (!customColors.isMonochrome) {
            addColorItem(binding.containerDividers, getString(R.string.theme_item_outline), customColors.outline) { customColors.outline = it }
            addColorItem(binding.containerDividers, getString(R.string.theme_item_divider), customColors.divider) { customColors.divider = it }
        }

        // System UI
        addColorItem(binding.containerSystemUI, getString(R.string.theme_item_status_bar), customColors.statusBar) { customColors.statusBar = it }
        addColorItem(binding.containerSystemUI, getString(R.string.theme_item_navigation_bar), customColors.navigationBar) { customColors.navigationBar = it }
        addSwitchItem(binding.containerSystemUI, getString(R.string.theme_item_light_status_icons), customColors.lightStatusBarIcons) { customColors.lightStatusBarIcons = it }
        addSwitchItem(binding.containerSystemUI, getString(R.string.theme_item_light_nav_icons), customColors.lightNavigationBarIcons) { customColors.lightNavigationBarIcons = it }

        // Extra
        addColorItem(binding.containerExtra, getString(R.string.theme_item_error), customColors.colorError) { customColors.colorError = it }
        addColorItem(binding.containerExtra, getString(R.string.theme_item_warning), customColors.colorWarning) { customColors.colorWarning = it }
        addColorItem(binding.containerExtra, getString(R.string.theme_item_success), customColors.colorSuccess) { customColors.colorSuccess = it }
        addColorItem(binding.containerExtra, getString(R.string.theme_item_info), customColors.colorInfo) { customColors.colorInfo = it }
    }

    private fun addColorItem(container: LinearLayout, title: String, color: Int, onSelected: (Int) -> Unit) {
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
        preview.previewCard.strokeColor = colors.outline
        
        preview.previewToolbar.setBackgroundColor(colors.surfaceToolbar)
        preview.previewToolbar.setTitleTextColor(colors.textPrimary)
        
        preview.previewContentCard.setCardBackgroundColor(colors.surfaceCard)
        preview.previewContentCard.strokeColor = colors.outline
        
        preview.previewTitle.setTextColor(colors.textPrimary)
        preview.previewSubtitle.setTextColor(colors.textSecondary)
        preview.previewDivider.setBackgroundColor(colors.divider)
        
        preview.previewIconPrimary.imageTintList = ColorStateList.valueOf(colors.iconPrimary)
        preview.previewLabelPrimary.setTextColor(colors.textPrimary)
        
        preview.previewIconSecondary.imageTintList = ColorStateList.valueOf(colors.iconSecondary)
        preview.previewLabelSecondary.setTextColor(colors.textSecondary)
        
        preview.previewButton.backgroundTintList = ColorStateList.valueOf(colors.accentColor)
        preview.previewButton.setTextColor(colors.accentText)
        
        preview.previewSwitch.thumbTintList = ColorStateList.valueOf(colors.accentColor)
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
