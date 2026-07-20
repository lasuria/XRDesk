package com.xrdesk

import android.content.Context

/**
 * Singleton engine for managing and applying application themes.
 */
object ThemeEngine {

    private var currentColors: ThemeColors = getPreset(SettingsStore.THEME_LIGHT)

    fun init(context: Context) {
        val themeMode = SettingsStore.nightMode
        currentColors = if (themeMode == SettingsStore.THEME_CUSTOM) {
            SettingsStore.getCustomThemeColors(context)
        } else {
            getPreset(themeMode)
        }
    }

    fun getColors(): ThemeColors = currentColors.getEffectiveColors()

    fun updateCustomColors(context: Context, colors: ThemeColors) {
        currentColors = colors
        SettingsStore.setCustomThemeColors(context, colors)
    }

    fun applyPreset(context: Context, themeMode: Int) {
        currentColors = if (themeMode == SettingsStore.THEME_CUSTOM) {
            SettingsStore.getCustomThemeColors(context)
        } else {
            getPreset(themeMode)
        }
    }

    fun getPreset(mode: Int): ThemeColors {
        return when (mode) {
            SettingsStore.THEME_LIGHT -> ThemeColors(
                background = 0xFFF8F9FF.toInt(),
                surface = 0xFFF3F4F9.toInt(),
                textPrimary = 0xFF191C20.toInt(),
                textSecondary = 0xFF44474F.toInt(),
                accent = 0xFF0B57D0.toInt(),
                onAccent = 0xFFFFFFFF.toInt(),
                divider = 0xFFD8DAE0.toInt(),
                lightStatusIcons = true,
                lightNavIcons = true,
                colorError = 0xFFB3261E.toInt(),
                colorSuccess = 0xFF4CAF50.toInt()
            )
            SettingsStore.THEME_DARK -> ThemeColors(
                background = 0xFF111318.toInt(),
                surface = 0xFF1A1C1E.toInt(),
                textPrimary = 0xFFE2E2E6.toInt(),
                textSecondary = 0xFFC4C6D0.toInt(),
                accent = 0xFFD3E3FD.toInt(),
                onAccent = 0xFF041E49.toInt(),
                divider = 0xFF44474F.toInt(),
                lightStatusIcons = false,
                lightNavIcons = false,
                colorError = 0xFFF2B8B5.toInt(),
                colorSuccess = 0xFF81C784.toInt()
            )
            SettingsStore.THEME_AMOLED -> ThemeColors(
                background = 0xFF000000.toInt(),
                surface = 0xFF111111.toInt(),
                textPrimary = 0xFFE3E3E3.toInt(),
                textSecondary = 0xFFAFAFAF.toInt(),
                accent = 0xFFE3E3E3.toInt(),
                onAccent = 0xFF000000.toInt(),
                divider = 0xFF2A2A2A.toInt(),
                lightStatusIcons = false,
                lightNavIcons = false,
                colorError = 0xFFF2B8B5.toInt(),
                colorSuccess = 0xFF81C784.toInt()
            )
            else -> getPreset(SettingsStore.THEME_LIGHT)
        }
    }
}
