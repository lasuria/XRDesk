package com.deskcontrol

import android.graphics.Color
import org.json.JSONObject

/**
 * Data class representing a full set of colors for the application theme.
 */
data class ThemeColors(
    // Background
    var background: Int = 0xFFFFFFFF.toInt(),
    
    // Surfaces
    var surfaceCard: Int = 0xFFEEF2F5.toInt(),
    var surfaceDialog: Int = 0xFFFFFFFF.toInt(),
    var surfaceBottomSheet: Int = 0xFFFFFFFF.toInt(),
    var surfaceToolbar: Int = 0xFFFFFFFF.toInt(),
    
    // Text
    var textPrimary: Int = 0xFF1B1B1B.toInt(),
    var textSecondary: Int = 0xFF6B7280.toInt(),
    var textDisabled: Int = 0xFF9CA3AF.toInt(),
    
    // Icons
    var iconPrimary: Int = 0xFF1B1B1B.toInt(),
    var iconSecondary: Int = 0xFF6B7280.toInt(),
    var iconDisabled: Int = 0xFF9CA3AF.toInt(),
    
    // Accent
    var accentColor: Int = 0xFF0B57D0.toInt(),
    var accentText: Int = 0xFFFFFFFF.toInt(),
    
    // Dividers
    var divider: Int = 0xFFE2E8F0.toInt(),
    var outline: Int = 0xFFD7DDE4.toInt(),
    
    // System UI
    var statusBar: Int = 0xFFFFFFFF.toInt(),
    var navigationBar: Int = 0xFFFFFFFF.toInt(),
    var lightStatusBarIcons: Boolean = true,
    var lightNavigationBarIcons: Boolean = true,
    
    // Extra
    var colorError: Int = 0xFFB00020.toInt(),
    var colorWarning: Int = 0xFFFFAB00.toInt(),
    var colorSuccess: Int = 0xFF4CAF50.toInt(),
    var colorInfo: Int = 0xFF2196F3.toInt(),
    
    // Monochrome Mode
    var isMonochrome: Boolean = false,
) {
    /**
     * Applies monochrome mode logic if enabled.
     * Overwrites foreground colors with textPrimary.
     */
    fun getEffectiveColors(): ThemeColors {
        if (!isMonochrome) return this
        return copy(
            textSecondary = textPrimary,
            textDisabled = textPrimary,
            iconPrimary = textPrimary,
            iconSecondary = textPrimary,
            iconDisabled = textPrimary,
            accentColor = textPrimary,
            outline = textPrimary,
            divider = textPrimary,
        )
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("background", String.format("#%06X", 0xFFFFFF and background))
        json.put("surfaceCard", String.format("#%06X", 0xFFFFFF and surfaceCard))
        json.put("surfaceDialog", String.format("#%06X", 0xFFFFFF and surfaceDialog))
        json.put("surfaceBottomSheet", String.format("#%06X", 0xFFFFFF and surfaceBottomSheet))
        json.put("surfaceToolbar", String.format("#%06X", 0xFFFFFF and surfaceToolbar))
        json.put("textPrimary", String.format("#%06X", 0xFFFFFF and textPrimary))
        json.put("textSecondary", String.format("#%06X", 0xFFFFFF and textSecondary))
        json.put("textDisabled", String.format("#%06X", 0xFFFFFF and textDisabled))
        json.put("iconPrimary", String.format("#%06X", 0xFFFFFF and iconPrimary))
        json.put("iconSecondary", String.format("#%06X", 0xFFFFFF and iconSecondary))
        json.put("iconDisabled", String.format("#%06X", 0xFFFFFF and iconDisabled))
        json.put("accentColor", String.format("#%06X", 0xFFFFFF and accentColor))
        json.put("accentText", String.format("#%06X", 0xFFFFFF and accentText))
        json.put("divider", String.format("#%06X", 0xFFFFFF and divider))
        json.put("outline", String.format("#%06X", 0xFFFFFF and outline))
        json.put("statusBar", String.format("#%06X", 0xFFFFFF and statusBar))
        json.put("navigationBar", String.format("#%06X", 0xFFFFFF and navigationBar))
        json.put("lightStatusBarIcons", lightStatusBarIcons)
        json.put("lightNavigationBarIcons", lightNavigationBarIcons)
        json.put("colorError", String.format("#%06X", 0xFFFFFF and colorError))
        json.put("colorWarning", String.format("#%06X", 0xFFFFFF and colorWarning))
        json.put("colorSuccess", String.format("#%06X", 0xFFFFFF and colorSuccess))
        json.put("colorInfo", String.format("#%06X", 0xFFFFFF and colorInfo))
        json.put("isMonochrome", isMonochrome)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ThemeColors {
            val json = JSONObject(jsonStr)
            return ThemeColors(
                background = Color.parseColor(json.getString("background")),
                surfaceCard = Color.parseColor(json.getString("surfaceCard")),
                surfaceDialog = Color.parseColor(json.getString("surfaceDialog")),
                surfaceBottomSheet = Color.parseColor(json.getString("surfaceBottomSheet")),
                surfaceToolbar = Color.parseColor(json.getString("surfaceToolbar")),
                textPrimary = Color.parseColor(json.getString("textPrimary")),
                textSecondary = Color.parseColor(json.getString("textSecondary")),
                textDisabled = Color.parseColor(json.getString("textDisabled")),
                iconPrimary = Color.parseColor(json.getString("iconPrimary")),
                iconSecondary = Color.parseColor(json.getString("iconSecondary")),
                iconDisabled = Color.parseColor(json.getString("iconDisabled")),
                accentColor = Color.parseColor(json.getString("accentColor")),
                accentText = Color.parseColor(json.getString("accentText")),
                divider = Color.parseColor(json.getString("divider")),
                outline = Color.parseColor(json.getString("outline")),
                statusBar = Color.parseColor(json.getString("statusBar")),
                navigationBar = Color.parseColor(json.getString("navigationBar")),
                lightStatusBarIcons = json.getBoolean("lightStatusBarIcons"),
                lightNavigationBarIcons = json.getBoolean("lightNavigationBarIcons"),
                colorError = Color.parseColor(json.getString("colorError")),
                colorWarning = Color.parseColor(json.getString("colorWarning")),
                colorSuccess = Color.parseColor(json.getString("colorSuccess")),
                colorInfo = Color.parseColor(json.getString("colorInfo")),
                isMonochrome = json.optBoolean("isMonochrome", false),
            )
        }
    }
}
