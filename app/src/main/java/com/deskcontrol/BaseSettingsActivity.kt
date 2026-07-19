package com.deskcontrol

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.slider.Slider

abstract class BaseSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        ThemeHelper.applyTheme(this)
    }

    protected fun setupToolbar(toolbarId: Int, title: String) {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(toolbarId)
        toolbar.title = title
        toolbar.setNavigationOnClickListener { finish() }
    }

    protected fun applyEdgeToEdge(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    protected fun snapToStep(value: Float, start: Float, step: Float): Float {
        val steps = kotlin.math.round((value - start) / step).toInt()
        val snapped = start + steps * step
        return (kotlin.math.round(snapped * 1000f) / 1000f)
    }
    
    protected fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
