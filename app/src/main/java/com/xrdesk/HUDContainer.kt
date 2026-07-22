package com.xrdesk

import android.view.View
import android.view.WindowManager

/**
 * Abstraction layer for HUD display targets.
 * Allows the same HUD logic to run in WindowManager (real) or ViewGroup (preview).
 */
interface HUDContainer {
    fun addView(view: View, params: WindowManager.LayoutParams)
    fun updateViewLayout(view: View, params: WindowManager.LayoutParams)
    fun removeView(view: View)
    
    /**
     * Returns the width of the display area.
     */
    fun getWidth(): Int

    /**
     * Returns the height of the display area.
     */
    fun getHeight(): Int
}

class WindowManagerContainer(
    private val windowManager: WindowManager,
    private val displayWidth: Int,
    private val displayHeight: Int
) : HUDContainer {
    override fun addView(view: View, params: WindowManager.LayoutParams) {
        android.util.Log.e("HUD-Lifecycle", "WindowManager.addView | view=$view")
        windowManager.addView(view, params)
    }
    override fun updateViewLayout(view: View, params: WindowManager.LayoutParams) {
        android.util.Log.d("HUD-Lifecycle", "WindowManager.updateViewLayout | view=$view")
        windowManager.updateViewLayout(view, params)
    }
    override fun removeView(view: View) {
        android.util.Log.e("HUD-Lifecycle", "WindowManager.removeView | view=$view")
        windowManager.removeView(view)
    }
    override fun getWidth(): Int = displayWidth
    override fun getHeight(): Int = displayHeight
}

class PreviewViewGroupContainer(
    private val root: android.view.ViewGroup
) : HUDContainer {
    override fun addView(view: View, params: WindowManager.LayoutParams) {
        root.removeAllViews()
        // Ensure the root view of the HUD fills the preview area
        val lp = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(view, lp)
    }
    override fun updateViewLayout(view: View, params: WindowManager.LayoutParams) {
        // In preview, we don't usually need WindowManager-style layout updates
    }
    override fun removeView(view: View) = root.removeView(view)
    override fun getWidth(): Int = if (root.width > 0) root.width else 1080 // Fallback to avoid division by zero
    override fun getHeight(): Int = if (root.height > 0) root.height else 607
}
