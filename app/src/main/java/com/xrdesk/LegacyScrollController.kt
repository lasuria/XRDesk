package com.xrdesk

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.abs

class LegacyScrollController(
    private val context: Context,
    private val handler: Handler,
    private val serviceProvider: () -> ControlAccessibilityService?
) : ScrollController {
    private var lastTwoFingerX = 0f
    private var lastTwoFingerY = 0f
    private var scrollAccumDx = 0f
    private var scrollAccumDy = 0f
    private var lastScrollEventTime = 0L

    override fun enter(service: ControlAccessibilityService, event: MotionEvent): Boolean {
        lastTwoFingerX = averageX(event)
        lastTwoFingerY = averageY(event)
        lastScrollEventTime = event.eventTime
        scrollAccumDx = 0f
        scrollAccumDy = 0f
        return true
    }

    override fun update(event: MotionEvent) {
        val currentX = averageX(event)
        val currentY = averageY(event)
        val deltaX = currentX - lastTwoFingerX
        val deltaY = currentY - lastTwoFingerY
        lastTwoFingerX = currentX
        lastTwoFingerY = currentY

        scrollAccumDx += deltaX
        scrollAccumDy += deltaY
        
        val service = serviceProvider() ?: return
        val userScale = SettingsStore.touchpadScrollSpeed
        val density = context.resources.displayMetrics.density
        
        // Return to simple, reliable threshold logic
        val stepThreshold = 12f * density / userScale.coerceAtLeast(0.1f)
        
        val direction = if (SettingsStore.touchpadScrollInverted) -1 else 1

        while (abs(scrollAccumDy) >= stepThreshold) {
            val stepDir = if (scrollAccumDy > 0) 1 else -1
            service.performScrollStep(
                direction = stepDir * direction,
                injectAnchorX = service.getCursorPosition().x,
                injectAnchorY = service.getCursorPosition().y,
                speedMultiplier = 1f,
                preferGesture = false,
                axis = ControlAccessibilityService.ScrollAxis.VERTICAL
            )
            scrollAccumDy -= stepDir * stepThreshold
        }

        while (abs(scrollAccumDx) >= stepThreshold) {
            val stepDir = if (scrollAccumDx > 0) 1 else -1
            service.performScrollStep(
                direction = stepDir * direction,
                injectAnchorX = service.getCursorPosition().x,
                injectAnchorY = service.getCursorPosition().y,
                speedMultiplier = 1f,
                preferGesture = false,
                axis = ControlAccessibilityService.ScrollAxis.HORIZONTAL
            )
            scrollAccumDx -= stepDir * stepThreshold
        }
        
        lastScrollEventTime = event.eventTime
    }

    override fun exit() {
        scrollAccumDx = 0f
        scrollAccumDy = 0f
    }

    private fun averageY(event: MotionEvent): Float {
        if (event.pointerCount == 0) return 0f
        var sum = 0f
        val count = minOf(2, event.pointerCount)
        repeat(count) { sum += event.getY(it) }
        return sum / count
    }

    private fun averageX(event: MotionEvent): Float {
        if (event.pointerCount == 0) return 0f
        var sum = 0f
        val count = minOf(2, event.pointerCount)
        repeat(count) { sum += event.getX(it) }
        return sum / count
    }
}
