package com.xrdesk

import android.content.Context
import android.os.Handler
import android.view.MotionEvent
import kotlin.math.abs

class LegacyScrollController(
    private val context: Context,
    private val handler: Handler,
    private val serviceProvider: () -> ControlAccessibilityService?
) : ScrollController {
    private var active = false
    private var scrollMidX = 0f
    private var scrollMidY = 0f
    private var scrollAccumulatorX = 0f
    private var scrollAccumulatorY = 0f
    private var scrollSpeedMultiplier = 1.0f
    private var virtualScrollX = 0f
    private var virtualScrollY = 0f

    override fun enter(service: ControlAccessibilityService, event: MotionEvent): Boolean {
        if (event.pointerCount < 2) return false
        val mid = scrollMidpoint(event)
        scrollMidX = mid.first
        scrollMidY = mid.second
        virtualScrollX = mid.first
        virtualScrollY = mid.second
        scrollAccumulatorX = 0f
        scrollAccumulatorY = 0f
        scrollSpeedMultiplier = SettingsStore.touchpadScrollSpeed
        active = true
        
        service.startContinuousScrollAtPoint(virtualScrollX, virtualScrollY)
        
        DiagnosticsLog.add("Touchpad", "scroll mode enter mid=(${virtualScrollX.toInt()},${virtualScrollY.toInt()}) speed=$scrollSpeedMultiplier")
        return true
    }

    override fun update(event: MotionEvent) {
        if (!active || event.pointerCount < 2) return
        val mid = scrollMidpoint(event)
        val dx = mid.first - scrollMidX
        val dy = mid.second - scrollMidY
        scrollMidX = mid.first
        scrollMidY = mid.second
        scrollAccumulatorX += dx
        scrollAccumulatorY += dy
        val service = serviceProvider() ?: return
        val density = context.resources.displayMetrics.density
        val threshold = 12f * density
        val vertical = abs(scrollAccumulatorY) >= abs(scrollAccumulatorX)
        
        if (vertical && abs(scrollAccumulatorY) >= threshold) {
            val direction = if (scrollAccumulatorY < 0) 1 else -1
            
            // Replicate exactly the legacy distance calculation to maintain "feel"
            // Note: We use a fixed distance for legacy discrete steps, but now we apply it to a path
            val distance = 48f * density * scrollSpeedMultiplier
            virtualScrollY += if (direction >= 0) -distance else distance
            
            service.updateContinuousScrollTo(virtualScrollX, virtualScrollY)
            
            scrollAccumulatorY = 0f
        } else if (!vertical && abs(scrollAccumulatorX) >= threshold) {
            val direction = if (scrollAccumulatorX < 0) 1 else -1
            val distance = 48f * density * scrollSpeedMultiplier
            virtualScrollX += if (direction >= 0) -distance else distance
            
            service.updateContinuousScrollTo(virtualScrollX, virtualScrollY)
            
            scrollAccumulatorX = 0f
        }
    }

    override fun exit() {
        if (!active) return
        active = false
        scrollAccumulatorX = 0f
        scrollAccumulatorY = 0f
        serviceProvider()?.endContinuousScroll()
        DiagnosticsLog.add("Touchpad", "scroll mode exit")
    }

    private fun scrollMidpoint(event: MotionEvent): Pair<Float, Float> {
        if (event.pointerCount < 2) return 0f to 0f
        var sumX = 0f
        var sumY = 0f
        val count = minOf(2, event.pointerCount)
        repeat(count) { index ->
            sumX += event.getX(index)
            sumY += event.getY(index)
        }
        return (sumX / count) to (sumY / count)
    }
}
