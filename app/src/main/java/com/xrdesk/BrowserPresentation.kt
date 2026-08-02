package com.xrdesk

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.xrdesk.databinding.PresentationBrowserBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(UnstableApi::class)
class BrowserPresentation(
    outerContext: Context, 
    display: Display,
    private val webView: WebView
) : Presentation(outerContext, display) {

    private lateinit var binding: PresentationBrowserBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val presentationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null
    
    private var cursorX = 0f
    private var cursorY = 0f
    private var lastMoveTime = 0L
    private var currentCursorMaxSize = 0
    private val hideCursorRunnable = Runnable { binding.virtualCursor.isVisible = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PresentationBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure window can receive focus for IME
        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        
        setupWebViewContainer()
        setupCursorObservation()
        
        // Initialize cursor position to center
        binding.root.post {
            cursorX = binding.root.width / 2f
            cursorY = binding.root.height / 2f
            updateCursorPosition()
            
            // Force renderer wake-up and layout pass
            webView.onResume()
            webView.requestFocus()
            webView.requestLayout()
            webView.invalidate()
        }
    }

    private fun setupWebViewContainer() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        binding.xrWebViewContainer.addView(webView, params)
        webView.requestFocus()
    }

    private fun setupCursorObservation() {
        updateJob?.cancel()
        updateJob = presentationScope.launch {
            combine(
                SettingsStore.cursorScaleFlow,
                SettingsStore.cursorColorFlow,
                SettingsStore.cursorAlphaFlow
            ) { scale, color, alpha ->
                Triple(scale, color, alpha)
            }.collect { (scale, color, alpha) ->
                updateCursorAppearance(scale, color, alpha)
            }
        }
    }

    private fun updateCursorAppearance(scale: Float, color: Int, alpha: Float) {
        if (!::binding.isInitialized) return
        
        // Match the logic from ControlAccessibilityService for consistency
        val physicalWidth = display.mode.physicalWidth
        val physicalHeight = display.mode.physicalHeight
        val minDim = min(physicalWidth, physicalHeight).toFloat()
        
        // Base size calculation tied to display density and user scale
        val baseSize = (minDim * 0.012f * scale).toInt().coerceIn(10, 26)
        
        binding.virtualCursor.setBaseSizePx(baseSize)
        binding.virtualCursor.setArrowColor(color)
        binding.virtualCursor.alpha = alpha
        
        // Ensure size is updated in LayoutParams as well
        currentCursorMaxSize = (baseSize * CursorOverlayView.MAX_SCALE).toInt().coerceAtLeast(baseSize)
        binding.virtualCursor.layoutParams = binding.virtualCursor.layoutParams.apply {
            width = currentCursorMaxSize
            height = currentCursorMaxSize
        }
        
        // Re-position because hotspot offset changed with size
        updateCursorPosition()
    }

    fun updateUrl(url: String?) {
        // No-op: Address bar removed from XR
    }

    fun receiveCursorMove(dx: Float, dy: Float) {
        if (!::binding.isInitialized) return
        val gain = 1.8f 
        cursorX = (cursorX + dx * gain).coerceIn(0f, binding.root.width.toFloat())
        cursorY = (cursorY + dy * gain).coerceIn(0f, binding.root.height.toFloat())
        
        val now = android.os.SystemClock.uptimeMillis()
        val dt = if (lastMoveTime > 0) now - lastMoveTime else 0L
        lastMoveTime = now
        
        mainHandler.post { 
            updateCursorPosition()
            binding.virtualCursor.onCursorMoved(dx * gain, dy * gain, dt)
            showCursor() 
        }
    }

    private fun updateCursorPosition() {
        if (!::binding.isInitialized) return
        
        // Calculate offset for hotspot (tip of arrow)
        val size = currentCursorMaxSize.toFloat()
        val offsetX = size * CursorOverlayView.HOTSPOT_FRACTION_X
        val offsetY = size * CursorOverlayView.HOTSPOT_FRACTION_Y
        
        // Align hotspot to cursor coordinates
        binding.virtualCursor.x = cursorX - offsetX
        binding.virtualCursor.y = cursorY - offsetY

        // Broadcast to global HUD monitor for edge trigger activation
        HUDSystemMonitor.publishCursor(cursorX, cursorY)
    }

    private fun showCursor() {
        if (!::binding.isInitialized) return
        binding.virtualCursor.isVisible = true
        mainHandler.removeCallbacks(hideCursorRunnable)
        mainHandler.postDelayed(hideCursorRunnable, 3000L)
    }

    fun receiveScroll(dy: Float) {
        if (!::binding.isInitialized) return
        mainHandler.post { webView.evaluateJavascript("window.scrollBy(0, ${dy.toInt()})", null) }
    }

    fun receiveClick() {
        if (!::binding.isInitialized) return
        mainHandler.post {
            val downTimeVal = android.os.SystemClock.uptimeMillis()
            
            // Redirect clicks to player if active, otherwise webview
            val target = if (binding.xrPlayerLayer.isVisible) binding.xrPlayerView else webView
            
            // Dispatch ACTION_DOWN
            val downEvent = MotionEvent.obtain(downTimeVal, downTimeVal, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0)
            target.dispatchTouchEvent(downEvent)
            downEvent.recycle()
            
            // Dispatch ACTION_UP with delay to ensure tap registration
            mainHandler.postDelayed({
                val upTime = android.os.SystemClock.uptimeMillis()
                val upEvent = MotionEvent.obtain(downTimeVal, upTime, MotionEvent.ACTION_UP, cursorX, cursorY, 0)
                target.dispatchTouchEvent(upEvent)
                upEvent.recycle()
            }, 50L) 
            
            showCursor()
        }
    }

    fun dispatchKeyToWebView(event: KeyEvent) {
        mainHandler.post {
            webView.dispatchKeyEvent(event)
            Log.d("XR Keyboard", "Forwarded key: ${event.keyCode} action: ${event.action}")
        }
    }

    fun commitTextToWebView(text: String) {
        mainHandler.post {
            // Using execCommand('insertText') is the most compatible way to trigger 
            // input events in web fields while maintaining cursor position.
            val escapedText = text.replace("'", "\\'").replace("\n", "\\n")
            val script = "document.execCommand('insertText', false, '$escapedText');"
            webView.evaluateJavascript(script, null)
            Log.d("XR Keyboard", "Injected text: $text")
        }
    }

    fun deleteTextFromWebView() {
        mainHandler.post {
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            Log.d("XR Keyboard", "Injected Backspace")
        }
    }

    fun zoomIn() {
        mainHandler.post { 
            webView.zoomIn()
        }
    }

    fun zoomOut() {
        mainHandler.post { 
            webView.zoomOut()
        }
    }

    fun getWebViewMetrics(callback: (String) -> Unit) {
        mainHandler.post {
            val script = """
                (function() { 
                    return JSON.stringify({ 
                        innerWidth: window.innerWidth, 
                        innerHeight: window.innerHeight,
                        clientWidth: document.documentElement.clientWidth, 
                        devicePixelRatio: window.devicePixelRatio,
                        userAgent: navigator.userAgent,
                        isMobile: (navigator.userAgentData ? navigator.userAgentData.mobile : /Mobi|Android/i.test(navigator.userAgent))
                    }); 
                })()
            """.trimIndent()
            webView.evaluateJavascript(script) { result ->
                // Strip extra quotes from JS return
                val clean = if (result?.startsWith("\"") == true && result.endsWith("\"")) {
                    result.substring(1, result.length - 1).replace("\\\"", "\"")
                } else result
                callback(clean ?: "{}")
            }
        }
    }

    
    fun showPlayer(player: Player, title: String?) {
        mainHandler.post {
            if (!::binding.isInitialized) return@post
            binding.xrPlayerView.player = player
            binding.xrPlayerLayer.isVisible = true
            binding.xrBrowserLayer.isVisible = false
            showCursor()
        }
    }
    
    fun hidePlayer() {
        mainHandler.post {
            if (!::binding.isInitialized) return@post
            binding.xrPlayerLayer.isVisible = false
            binding.xrBrowserLayer.isVisible = true
            binding.xrPlayerView.player = null
        }
    }

    fun detachWebView(): WebView {
        if (::binding.isInitialized) {
            binding.xrWebViewContainer.removeView(webView)
        }
        return webView
    }

    override fun onStop() {
        updateJob?.cancel()
        super.onStop()
    }
}
