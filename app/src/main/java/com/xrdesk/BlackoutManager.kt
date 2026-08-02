package com.xrdesk

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Manages a programmatic full-screen black overlay to dim the device screen completely.
 * Intercepts all touch events and manages system bar visibility temporarily.
 */
class BlackoutManager(private var activity: Activity?) {

    private var overlay: FrameLayout? = null
    private var hintContainer: LinearLayout? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private val hideHintRunnable = Runnable { fadeOutHint() }
    
    // Captured state for restoration
    private var originalSystemBarsBehavior: Int = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    private var originalStatusBarsVisible: Boolean = true
    private var originalNavBarsVisible: Boolean = true
    
    @Suppress("DEPRECATION")
    private var originalSystemUiVisibility: Int = 0
    private var originallyFitsSystemWindows: Boolean = true

    // Gesture state
    private var touchDownX = 0f
    private var touchDownY = 0f

    /**
     * Shows the blackout overlay and hides system bars.
     * Captures the current window state to restore it later.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        val currentActivity = activity ?: return
        if (isVisible()) return

        val window = currentActivity.window
        val decorView = window.decorView
        val controller = WindowCompat.getInsetsController(window, decorView)

        // 1. Capture original state
        originalSystemBarsBehavior = controller.systemBarsBehavior
        
        val insets = ViewCompat.getRootWindowInsets(decorView)
        originalStatusBarsVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        originalNavBarsVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        
        @Suppress("DEPRECATION")
        originalSystemUiVisibility = decorView.systemUiVisibility
        
        // Heuristic to detect if DecorFitsSystemWindows was already false (edge-to-edge)
        @Suppress("DEPRECATION")
        originallyFitsSystemWindows = (originalSystemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_STABLE) == 0

        // 2. Configure Window for Blackout
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 3. Create and add Overlay
        val blackoutView = FrameLayout(currentActivity).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Intercept all touches and detect swipe up
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.rawX
                        touchDownY = event.rawY
                        if (SettingsStore.blackoutShowHint) showHint()
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val diffX = event.rawX - touchDownX
                        val diffY = event.rawY - touchDownY
                        val density = currentActivity.resources.displayMetrics.density
                        val thresholdPx = 100 * density
                        
                        // Sync hint alpha with swipe progress
                        if (SettingsStore.blackoutShowHint && diffY < 0) {
                            val progress = (Math.abs(diffY) / (thresholdPx * 0.8f)).coerceIn(0f, 1f)
                            hintContainer?.alpha = SettingsStore.blackoutHintOpacity * (1f - progress)
                        }
                        
                        if (diffY < -thresholdPx && Math.abs(diffY) > Math.abs(diffX)) {
                            hide()
                        }
                    }
                }
                true 
            }
        }

        if (SettingsStore.blackoutShowHint) {
            setupHint(currentActivity, blackoutView)
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        (decorView as ViewGroup).addView(blackoutView, params)
        overlay = blackoutView
        if (SettingsStore.blackoutShowHint) showHint()
    }

    private fun setupHint(context: Activity, container: FrameLayout) {
        if (hintContainer == null) {
            hintContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = false
                isFocusable = false
                
                val arrowView = TextView(context).apply {
                    tag = "arrow"
                    text = "↑"
                    gravity = Gravity.CENTER
                }
                val labelView = TextView(context).apply {
                    tag = "label"
                    gravity = Gravity.CENTER
                }
                addView(arrowView)
                addView(labelView)
            }
        }
        
        // Apply latest settings
        val fontSize = SettingsStore.blackoutHintFontSize
        val rawText = SettingsStore.blackoutHintText
        val hintText = if (rawText.isBlank()) context.getString(R.string.blackout_hint_default) else rawText

        hintContainer?.findViewWithTag<TextView>("arrow")?.apply {
            visibility = if (SettingsStore.blackoutShowArrow) View.VISIBLE else View.GONE
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize + 12f) // Keep arrow larger
        }
        hintContainer?.findViewWithTag<TextView>("label")?.apply {
            text = hintText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
        }
        
        // Remove from previous parent if any (reusing the same instance)
        (hintContainer?.parent as? ViewGroup)?.removeView(hintContainer)

        val density = context.resources.displayMetrics.density
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (80 * density).toInt() // Fallback
        }

        ViewCompat.setOnApplyWindowInsetsListener(hintContainer!!) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val margin = if (bars.bottom > 0) {
                bars.bottom + (24 * density).toInt()
            } else {
                (80 * density).toInt()
            }
            (v.layoutParams as FrameLayout.LayoutParams).bottomMargin = margin
            v.requestLayout()
            insets
        }

        container.addView(hintContainer, params)
    }

    private fun showHint() {
        handler.removeCallbacks(hideHintRunnable)
        hintContainer?.animate()?.cancel()
        hintContainer?.alpha = SettingsStore.blackoutHintOpacity
        
        val timeout = SettingsStore.blackoutHintTimeout
        if (timeout > 0) {
            handler.postDelayed(hideHintRunnable, timeout * 1000L)
        }
    }

    private fun fadeOutHint() {
        hintContainer?.animate()
            ?.alpha(0f)
            ?.setDuration(400L)
            ?.start()
    }

    /**
     * Hides the blackout overlay and restores the previous window state.
     */
    @SuppressLint("WrongConstant")
    fun hide() {
        val currentActivity = activity ?: return
        val currentOverlay = overlay ?: return
        
        handler.removeCallbacks(hideHintRunnable)
        hintContainer?.animate()?.cancel()

        val window = currentActivity.window
        val decorView = window.decorView
        val controller = WindowCompat.getInsetsController(window, decorView)

        // 1. Restore System UI state
        controller.systemBarsBehavior = originalSystemBarsBehavior
        
        if (originalStatusBarsVisible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }

        if (originalNavBarsVisible) {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }

        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = originalSystemUiVisibility
        WindowCompat.setDecorFitsSystemWindows(window, originallyFitsSystemWindows)

        // 2. Remove Overlay
        (decorView as ViewGroup).removeView(currentOverlay)
        overlay = null
    }

    /**
     * Checks if the blackout overlay is currently visible.
     */
    fun isVisible(): Boolean = overlay != null

    /**
     * Cleans up references and hides the overlay if it's visible.
     */
    fun destroy() {
        hide()
        activity = null
    }
}
