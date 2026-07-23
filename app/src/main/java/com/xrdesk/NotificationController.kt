package com.xrdesk

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.card.MaterialCardView
import java.util.*

/**
 * Manages the sequential notification queue on the external display.
 * Uses a fixed "Premium Dark Glass" theme matching the HUD.
 */
class NotificationController(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private val queue = ArrayDeque<HUDNotification>()
    private val handler = Handler(Looper.getMainLooper())
    private var isDisplaying = false
    
    private var currentCard: MaterialCardView? = null
    private val interpolator = FastOutSlowInInterpolator()
    private val animDuration = 280L
    private val slideOffsetDp = 6f

    fun post(notification: HUDNotification) {
        android.util.Log.d("NotificationController", "post: hudNotificationsEnabled=${SettingsStore.hudNotificationsEnabled}")
        if (!SettingsStore.hudNotificationsEnabled) return
        queue.add(notification)
        processQueue()
    }

    private fun processQueue() {
        if (isDisplaying || queue.isEmpty()) return
        val next = queue.poll() ?: return
        showNotification(next)
    }

    private fun showNotification(notif: HUDNotification) {
        isDisplaying = true
        val density = context.resources.displayMetrics.density
        val size = SettingsStore.hudNotificationSizeDp
        
        val card = createView(notif, size, density)
        currentCard = card
        
        val params = WindowManager.LayoutParams(
            (size * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = (32 * density).toInt() // Airy top margin

        windowManager.addView(card, params)
        
        card.alpha = 0f
        card.scaleX = 0.95f
        card.scaleY = 0.95f
        card.translationY = -slideOffsetDp * density
        
        card.animate()
            .alpha(1f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .translationY(0f)
            .setDuration(animDuration)
            .setInterpolator(interpolator)
            .withEndAction {
                handler.postDelayed({
                    hideNotification(card)
                }, 5000L)
            }
            .start()
    }

    private fun hideNotification(card: View) {
        card.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(animDuration)
            .setInterpolator(interpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runCatching { windowManager.removeView(card) }
                    isDisplaying = false
                    currentCard = null
                    processQueue()
                }
            })
            .start()
    }

    private fun createView(notif: HUDNotification, sizeDp: Float, density: Float): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = HUDConstants.NOTIF_RADIUS_DP * density
            cardElevation = 4f * density
            strokeWidth = (1 * density).toInt()
            strokeColor = 0x26FFFFFF // ~15% White frost border
            setCardBackgroundColor(0xCC1A1C1E.toInt()) // Fixed Premium Dark Glass
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (sizeDp * HUDConstants.NOTIF_PADDING_H_RATIO * density).toInt()
            val padV = (sizeDp * HUDConstants.NOTIF_PADDING_V_RATIO * density).toInt()
            setPadding(padH, padV, padH, padV)
            card.addView(this)
        }

        // Icon
        if (notif.icon != null) {
            val iconView = ImageView(context).apply {
                val iSize = (sizeDp * 0.15f * density).toInt()
                layoutParams = LinearLayout.LayoutParams(iSize, iSize)
                setImageDrawable(notif.icon)
            }
            rootLayout.addView(iconView)
        }

        // Text content
        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (14 * density).toInt()
            }

            val titleView = TextView(context).apply {
                text = notif.title ?: notif.appName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.065f)
                setTextColor(0xFFFFFFFF.toInt())
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            
            val bodyView = TextView(context).apply {
                text = notif.text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.055f)
                setTextColor(0xAAFFFFFF.toInt())
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                visibility = if (notif.text.isNullOrBlank()) View.GONE else View.VISIBLE
            }

            addView(titleView)
            addView(bodyView)
        }
        rootLayout.addView(textLayout)
        
        return card
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        queue.clear()
        currentCard?.let { 
            runCatching { windowManager.removeView(it) }
        }
        currentCard = null
        isDisplaying = false
    }
}
