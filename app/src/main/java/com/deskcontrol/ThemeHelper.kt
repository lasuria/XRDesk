package com.deskcontrol

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Helper class to manually apply custom theme colors to activities and views.
 */
object ThemeHelper {

    fun applyTheme(activity: Activity) {
        val colors = ThemeEngine.getColors()
        
        // Window Background
        activity.window.setBackgroundDrawable(ColorDrawable(colors.background))
        
        // Status Bar & Nav Bar
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = colors.statusBar
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = colors.navigationBar
        
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = colors.lightStatusBarIcons
        controller.isAppearanceLightNavigationBars = colors.lightNavigationBarIcons
        
        // Root View
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        if (root != null && (root.childCount > 0)) {
            // The first child is usually the root layout defined in XML
            val mainLayout = root.getChildAt(0)
            mainLayout.setBackgroundColor(colors.background)
        }
        applyToViewHierarchy(root, colors)
    }

    private fun applyToViewHierarchy(view: View?, colors: ThemeColors) {
        if (view == null) return

        when (view) {
            is Toolbar -> {
                view.setBackgroundColor(colors.surfaceToolbar)
                view.setTitleTextColor(colors.textPrimary)
            }
            is MaterialCardView -> {
                view.setCardBackgroundColor(colors.surfaceCard)
                view.strokeColor = colors.outline
                if (view.strokeWidth <= 0) {
                    view.strokeWidth = dpToPx(view.context, 1)
                }
            }
            is MaterialButton -> {
                if (view.backgroundTintList?.isStateful == false || (view.backgroundTintList == null)) {
                    view.backgroundTintList = ColorStateList.valueOf(colors.accentColor)
                    view.setTextColor(colors.accentText)
                }
            }
            is MaterialSwitch -> {
                view.thumbTintList = ColorStateList.valueOf(colors.accentColor)
            }
            is ViewGroup -> {
                val id = view.id
                val bg = view.background
                
                // Specific Check for Card-like containers
                val isKnownCard = id == R.id.statusCard || id == R.id.btnPickApp || 
                                 id == R.id.btnTouchpad || id == R.id.btnSettings ||
                                 id == R.id.rowTheme || id == R.id.rowLanguage ||
                                 id == R.id.rowTouchpad || id == R.id.rowCursor ||
                                 id == R.id.rowDock || id == R.id.rowDiagnostics ||
                                 id == R.id.rowAbout || id == R.id.btnOpenEditor ||
                                 id == R.id.themeRadioGroup
                
                if (isKnownCard) {
                    view.background = createCardDrawable(view.context, colors, clickable = view.isClickable)
                } else if (bg != null && (bg is GradientDrawable || bg is RippleDrawable)) {
                    // It's a shaped container, likely a card from XML we haven't listed by ID
                    view.background = createCardDrawable(view.context, colors, clickable = view.isClickable)
                } else if (view !is android.widget.ScrollView && view !is android.widget.ListView) {
                    // Check if it's a divider layout
                    if (view.layoutParams != null && (view.layoutParams.height in 1..dpToPx(view.context, 2))) {
                        view.setBackgroundColor(colors.divider)
                    }
                }
                
                // Recurse into children
                for (i in 0 until view.childCount) {
                    applyToViewHierarchy(view.getChildAt(i), colors)
                }
            }
            is TextView -> {
                // If it's a small view acting as a divider
                if (view.layoutParams != null && (view.layoutParams.height in 1..dpToPx(view.context, 2))) {
                    view.setBackgroundColor(colors.divider)
                } else {
                    if (view.currentTextColor == 0xFF6B7280.toInt() || (view.alpha < 1f)) {
                        view.setTextColor(colors.textSecondary)
                    } else {
                        view.setTextColor(colors.textPrimary)
                    }
                }
            }
            else -> {
                // Simple View (likely a divider)
                if (view.id != android.R.id.content && view.layoutParams != null) {
                    val height = view.layoutParams.height
                    if (height > 0 && (height <= dpToPx(view.context, 2))) {
                        view.setBackgroundColor(colors.divider)
                    }
                }
            }
        }
    }

    private fun createCardDrawable(context: android.content.Context, colors: ThemeColors, clickable: Boolean): android.graphics.drawable.Drawable {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(colors.surfaceCard)
            setStroke(dpToPx(context, 1), colors.outline)
            cornerRadius = dpToPx(context, 28).toFloat()
        }
        
        return if (clickable) {
            val rippleColor = ColorStateList.valueOf((colors.accentColor and 0x00FFFFFF) or 0x20000000)
            RippleDrawable(rippleColor, shape, shape)
        } else {
            shape
        }
    }

    private fun dpToPx(context: android.content.Context, dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
    }
}
