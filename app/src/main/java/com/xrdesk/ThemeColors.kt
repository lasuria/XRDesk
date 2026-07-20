package com.xrdesk

import android.graphics.Color
import org.json.JSONObject

/**
 * Simplified data class representing the essential colors for the application theme.
 * Consolidates 23+ legacy properties into 8-10 key Material 3 / Pixel roles.
 */
data class ThemeColors(
    var background: Int = 0xFFFFFFFF.toInt(),
    var surface: Int = 0xFFF3F4F9.toInt(), // Surface Container Low
    var textPrimary: Int = 0xFF191C20.toInt(),
    var textSecondary: Int = 0xFF44474F.toInt(),
    var accent: Int = 0xFF0B57D0.toInt(),
    var onAccent: Int = 0xFFFFFFFF.toInt(),
    var divider: Int = 0xFFD8DAE0.toInt(),
    var lightStatusIcons: Boolean = true,
    var lightNavIcons: Boolean = true,
    var colorError: Int = 0xFFB3261E.toInt(), // Material Red
    var colorSuccess: Int = 0xFF4CAF50.toInt(), // Material Green
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
            accent = textPrimary,
            divider = textPrimary,
        )
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("background", String.format("#%06X", 0xFFFFFF and background))
        json.put("surface", String.format("#%06X", 0xFFFFFF and surface))
        json.put("textPrimary", String.format("#%06X", 0xFFFFFF and textPrimary))
        json.put("textSecondary", String.format("#%06X", 0xFFFFFF and textSecondary))
        json.put("accent", String.format("#%06X", 0xFFFFFF and accent))
        json.put("onAccent", String.format("#%06X", 0xFFFFFF and onAccent))
        json.put("divider", String.format("#%06X", 0xFFFFFF and divider))
        json.put("lightStatusIcons", lightStatusIcons)
        json.put("lightNavIcons", lightNavIcons)
        json.put("colorError", String.format("#%06X", 0xFFFFFF and colorError))
        json.put("colorSuccess", String.format("#%06X", 0xFFFFFF and colorSuccess))
        json.put("isMonochrome", isMonochrome)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ThemeColors {
            val json = JSONObject(jsonStr)
            return ThemeColors(
                background = Color.parseColor(json.getString("background")),
                surface = Color.parseColor(json.getString("surface")),
                textPrimary = Color.parseColor(json.getString("textPrimary")),
                textSecondary = Color.parseColor(json.getString("textSecondary")),
                accent = Color.parseColor(json.getString("accent")),
                onAccent = Color.parseColor(json.getString("onAccent")),
                divider = Color.parseColor(json.getString("divider")),
                lightStatusIcons = json.getBoolean("lightStatusIcons"),
                lightNavIcons = json.getBoolean("lightNavIcons"),
                colorError = Color.parseColor(json.optString("colorError", "#B3261E")),
                colorSuccess = Color.parseColor(json.optString("colorSuccess", "#4CAF50")),
                isMonochrome = json.optBoolean("isMonochrome", false),
            )
        }
    }
}
