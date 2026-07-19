package com.deskcontrol

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
                background = 0xFFFFFFFF.toInt(),
                surfaceCard = 0xFFEEF2F5.toInt(),
                surfaceDialog = 0xFFFFFFFF.toInt(),
                surfaceBottomSheet = 0xFFFFFFFF.toInt(),
                surfaceToolbar = 0xFFFFFFFF.toInt(),
                textPrimary = 0xFF1B1B1B.toInt(),
                textSecondary = 0xFF6B7280.toInt(),
                accentColor = 0xFF0B57D0.toInt(),
                accentText = 0xFFFFFFFF.toInt(),
                divider = 0xFFE2E8F0.toInt(),
                outline = 0xFFD7DDE4.toInt(),
                statusBar = 0xFFFFFFFF.toInt(),
                navigationBar = 0xFFFFFFFF.toInt(),
                lightStatusBarIcons = true,
                lightNavigationBarIcons = true
            )
            SettingsStore.THEME_DARK -> ThemeColors(
                background = 0xFF111318.toInt(),
                surfaceCard = 0xFF1E2025.toInt(),
                surfaceDialog = 0xFF1E2025.toInt(),
                surfaceBottomSheet = 0xFF1E2025.toInt(),
                surfaceToolbar = 0xFF111318.toInt(),
                textPrimary = 0xFFE2E2E9.toInt(),
                textSecondary = 0xFFC4C6D0.toInt(),
                accentColor = 0xFFD3E3FD.toInt(),
                accentText = 0xFF041E49.toInt(),
                divider = 0xFF44474F.toInt(),
                outline = 0xFF44474F.toInt(),
                statusBar = 0xFF111318.toInt(),
                navigationBar = 0xFF111318.toInt(),
                lightStatusBarIcons = false,
                lightNavigationBarIcons = false
            )
            SettingsStore.THEME_AMOLED -> ThemeColors(
                background = 0xFF000000.toInt(),
                surfaceCard = 0xFF111111.toInt(),
                surfaceDialog = 0xFF111111.toInt(),
                surfaceBottomSheet = 0xFF111111.toInt(),
                surfaceToolbar = 0xFF000000.toInt(),
                textPrimary = 0xFFE3E3E3.toInt(),
                textSecondary = 0xFFAFAFAF.toInt(),
                accentColor = 0xFFE3E3E3.toInt(),
                accentText = 0xFF000000.toInt(),
                divider = 0xFF2A2A2A.toInt(),
                outline = 0xFF2A2A2A.toInt(),
                statusBar = 0xFF000000.toInt(),
                navigationBar = 0xFF000000.toInt(),
                lightStatusBarIcons = false,
                lightNavigationBarIcons = false
            )
            SettingsStore.THEME_MATERIAL_YOU -> ThemeColors(
                background = 0xFFFDF8FD.toInt(),
                surfaceCard = 0xFFF3EDF7.toInt(),
                surfaceDialog = 0xFFF3EDF7.toInt(),
                surfaceBottomSheet = 0xFFF3EDF7.toInt(),
                surfaceToolbar = 0xFFFDF8FD.toInt(),
                textPrimary = 0xFF1D1B20.toInt(),
                textSecondary = 0xFF49454F.toInt(),
                accentColor = 0xFF6750A4.toInt(),
                accentText = 0xFFFFFFFF.toInt(),
                divider = 0xFFCAC4D0.toInt(),
                outline = 0xFF79747E.toInt(),
                statusBar = 0xFFFDF8FD.toInt(),
                navigationBar = 0xFFFDF8FD.toInt(),
                lightStatusBarIcons = true,
                lightNavigationBarIcons = true
            )
            else -> getPreset(SettingsStore.THEME_LIGHT)
        }
    }
}
