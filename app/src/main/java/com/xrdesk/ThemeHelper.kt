package com.xrdesk

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Simplified helper class to manually apply custom theme colors to activities and views.
 * Uses 8 core roles to unify the visual language.
 */
object ThemeHelper {

    fun applyTheme(activity: Activity) {
        val colors = ThemeEngine.getColors()
        
        // Window Background
        activity.window.setBackgroundDrawable(ColorDrawable(colors.background))
        
        // Status Bar & Nav Bar
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = colors.background
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = colors.background
        
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = colors.lightStatusIcons
        controller.isAppearanceLightNavigationBars = colors.lightNavIcons
        
        // Root View
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        if (root != null && (root.childCount > 0)) {
            val mainLayout = root.getChildAt(0)
            mainLayout.setBackgroundColor(colors.background)
        }
        applyToViewHierarchy(root, colors)
    }

    private fun applyToViewHierarchy(view: View?, colors: ThemeColors) {
        if (view == null) return

        when (view) {
            is Toolbar -> {
                view.setBackgroundColor(colors.background)
                view.setTitleTextColor(colors.textPrimary)
            }
            is MaterialCardView -> {
                view.setCardBackgroundColor(colors.surface)
                view.strokeColor = colors.divider
                if (view.strokeWidth <= 0) {
                    view.strokeWidth = dpToPx(view.context, 1)
                }
            }
            is com.google.android.material.button.MaterialButton -> {
                val id = view.id
                if (id == R.id.btnExport || id == R.id.btnImport || 
                    id == R.id.btnResetLight || id == R.id.btnResetDark || id == R.id.btnResetAmoled) {
                    view.backgroundTintList = ColorStateList.valueOf(colors.accent)
                    view.setTextColor(colors.onAccent)
                    view.iconTint = ColorStateList.valueOf(colors.onAccent)
                }
            }
            is ViewGroup -> {
                val id = view.id
                val bg = view.background
                
                // Connection Status Card, Touchpad Launch, etc.
                val isKnownCard = id == R.id.statusCard || id == R.id.btnTouchpad || 
                                 id == R.id.btnSettings || id == R.id.touchpadLaunch
                
                if (isKnownCard) {
                    view.background = createCardDrawable(view.context, colors, clickable = view.isClickable)
                } else if (bg != null && (bg is GradientDrawable || bg is RippleDrawable)) {
                    // Exclude specific views that should remain flat even if they have a ripple/shape
                    val activityName = view.context?.javaClass?.simpleName ?: ""
                    val isFlatActivity = activityName == "ThemeEditorActivity" || activityName == "SettingsActivity" || 
                                       activityName == "SettingsLanguageActivity" || activityName == "SettingsThemeActivity" ||
                                       activityName == "SettingsTouchpadActivity" || activityName == "SettingsCursorActivity" ||
                                       activityName == "SettingsDockActivity" || activityName == "SettingsAboutActivity" ||
                                       activityName == "AboutXRDeskActivity" || activityName == "AboutBasedOnActivity" ||
                                       activityName == "DiagnosticsActivity" || activityName == "SettingsChangelogActivity"

                    if (!isKnownCard && !isFlatActivity) {
                        view.background = createCardDrawable(view.context, colors, clickable = view.isClickable)
                    }
                } else if (view !is android.widget.ScrollView && view !is android.widget.ListView) {
                    // Simple Divider View
                    if (view.layoutParams != null && (view.layoutParams.height in 1..dpToPx(view.context, 2))) {
                        view.setBackgroundColor(colors.divider)
                    }
                }
                
                for (i in 0 until view.childCount) {
                    applyToViewHierarchy(view.getChildAt(i), colors)
                }
            }
            is ImageView -> {
                val id = view.id
                // Secondary icons (alpha or specific ID)
                if (id == R.id.appLogo) {
                    view.imageTintList = null
                    view.colorFilter = null
                } else if (view.alpha < 1f || id == R.id.iconAccessibility) {
                    view.imageTintList = ColorStateList.valueOf(colors.textSecondary)
                } else if (id == R.id.iconDisplay) {
                    // Main display icon remains color-coded for connection
                } else {
                    view.imageTintList = ColorStateList.valueOf(colors.textPrimary)
                }
            }
            is TextView -> {
                if (view.layoutParams != null && (view.layoutParams.height in 1..dpToPx(view.context, 2))) {
                    view.setBackgroundColor(colors.divider)
                } else {
                    if (!view.isEnabled) {
                        view.setTextColor(colors.textSecondary)
                        view.alpha = 0.5f
                    } else if (view.alpha < 1f || view.currentTextColor == 0x8A000000.toInt() || view.currentTextColor == 0x8AFFFFFF.toInt()) {
                        view.setTextColor(colors.textSecondary)
                    } else {
                        view.setTextColor(colors.textPrimary)
                    }
                }
            }
        }
    }

    private fun createCardDrawable(context: android.content.Context, colors: ThemeColors, clickable: Boolean): android.graphics.drawable.Drawable {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(colors.surface)
            setStroke(dpToPx(context, 1), colors.divider)
            cornerRadius = dpToPx(context, 20).toFloat()
        }
        
        return if (clickable) {
            val rippleColor = ColorStateList.valueOf((colors.accent and 0x00FFFFFF) or 0x20000000)
            RippleDrawable(rippleColor, shape, shape)
        } else {
            shape
        }
    }

    private fun dpToPx(context: android.content.Context, dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
    }
}
