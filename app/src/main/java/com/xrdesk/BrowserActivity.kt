package com.xrdesk

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.xrdesk.databinding.ActivityBrowserBinding
import com.xrdesk.databinding.DialogTrackSelectionBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

@OptIn(UnstableApi::class)
class BrowserActivity : AppCompatActivity(), DisplaySessionManager.Listener {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var webView: WebView
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var browserManager: BrowserManager
    private lateinit var resourceInterceptor: ResourceInterceptor
    private lateinit var legacyVideoDetector: LegacyVideoDetector
    private lateinit var videoResolver: VideoResolverManager
    private lateinit var inputBridge: BrowserInputBridge
    private lateinit var imeProxy: androidx.appcompat.widget.AppCompatEditText

    private var lastVerifiedPageUrl: String? = null

    // Touchpad tracking state
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private val tapThreshold = 15f
    
    private var isScrollMode = false
    private var isPinchMode = false
    private var initialPinchDist = 0f
    private var lastTwoFingerY = 0f

    // XR Mode State
    private var presentation: BrowserPresentation? = null
    private var isXrModeActive = false
    private var isTransitioning = false
    private var isPlayerActiveInXr = false
    private var isBrowserSuspended = false
    private lateinit var touchpadProcessor: TouchpadProcessor
    private var resolvedSource: PlayableSource? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            runOnUiThread { updatePlayPauseIcon(isPlaying) }
        }
        override fun onTracksChanged(tracks: Tracks) {
            runOnUiThread { updateRemotePlayerStatus() }
        }
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            runOnUiThread { updateRemotePlayerStatus() }
        }
    }

    // Power/Blackout State
    private var originalWindowBrightness: Float = -1f
    private var originalSystemBrightness: Float = 1f
    private var hasOriginalWindowBrightness = false
    private var dimmedThisSession = false
    private var dimAnimator: ValueAnimator? = null
    private var blackoutMoved = false
    private var blackoutDownX = 0f
    private var blackoutDownY = 0f

    private val playbackTicker = object : Runnable {
        override fun run() {
            if (isPlayerActiveInXr) {
                updatePlayerProgress()
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DiagnosticsLog.add("Crash", "BrowserActivity fatal: ${throwable.message}")
            oldHandler?.uncaughtException(thread, throwable)
        }

        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        ThemeHelper.applyTheme(this)
        applyEdgeToEdgePadding(binding.root)

        // XR-First WebView Initialization (Single instance for both screens)
        webView = WebView(this)
        
        binding.webViewContainer.addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        browserManager = BrowserManager(this)
        resourceInterceptor = ResourceInterceptor(this)
        touchpadProcessor = TouchpadProcessor(TouchpadTuning)
        
        setupVideoDetection()
        setupWebView()
        setupStandardControls()
        setupXrControls()
        setupBlackoutLogic()
        setupImeProxy()

        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC)

        // Explicitly reset transition state after control setup
        isTransitioning = false

        // Sync Notifications state reactively
        lifecycleScope.launch {
            SettingsStore.hudNotificationsEnabledFlow.collectLatest {
                updateXrNotifButtonUI()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.blackoutOverlay.isVisible) {
                    setBlackoutVisible(false)
                } else if (isPlayerActiveInXr) {
                    closeXrPlayer()
                } else if (isXrModeActive) {
                    toggleXrMode(false)
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        if (savedInstanceState != null) {
            val manualUrl = savedInstanceState.getString("manual_last_url")
            if (manualUrl != null) {
                webView.loadUrl(manualUrl)
            }
        } else if (webView.url == null) {
            webView.loadUrl("https://www.google.com")
        }
        
        DisplaySessionManager.addListener(this)
        updateXrButtonVisibility()
    }

    private fun setupVideoDetection() {
        legacyVideoDetector = LegacyVideoDetector { video ->
            runOnUiThread {
                if (!video.isArchived && video.pageUrl.startsWith("http")) {
                    lastVerifiedPageUrl = video.pageUrl
                }
            }
        }
        
        videoResolver = VideoResolverManager { source ->
            resolvedSource = source
            runOnUiThread {
                updateVideoButtonState()
                
                if (isXrModeActive) {
                    btnXrOpenVideoState(true)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        inputBridge = BrowserInputBridge { focused: Boolean ->
            if (isXrModeActive && focused) {
                requestKeyboardFocus()
            }
        }
        webView.addJavascriptInterface(legacyVideoDetector, "VideoDetector")
        webView.addJavascriptInterface(videoResolver, "VideoResolver")
        webView.addJavascriptInterface(inputBridge, "InputBridge")
        
        WebViewSettings.configure(webView)
        
        webView.webViewClient = object : WebViewClient() {
            private fun isTarget(url: String?): Boolean {
                val lastUrl = lastVerifiedPageUrl
                val onTargetPage = lastUrl != null && (lastUrl.contains("jut-su.net") || lastUrl.contains("jut.su"))
                val targetUrl = url != null && (url.contains("jut-su.net") || url.contains("jut.su"))
                return onTargetPage || targetUrl
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (isTarget(url)) {
                    val msg = "onPageStarted: $url"
                    DiagnosticsLog.add("Nav", msg)
                    Log.d("WebViewNav", msg)
                }
                binding.progressBar.isVisible = true
                binding.urlInput.setText(url)
                binding.remoteUrlInput.setText(url)
                
                // Track URL changes immediately
                if (url != null && url.startsWith("http")) {
                    lastVerifiedPageUrl = url
                }

                legacyVideoDetector.clear()
                videoResolver.clear()
                resolvedSource = null
                
                updateVideoButtonState()

                if (isXrModeActive) {
                    presentation?.updateUrl(url)
                    btnXrOpenVideoState(false)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (isTarget(url)) {
                    val visibilityLog = "Visibility: isShown=${view?.isShown}, vis=${view?.visibility}, winVis=${view?.windowVisibility}"
                    val msg = "onPageFinished: $url | $visibilityLog"
                    DiagnosticsLog.add("Nav", msg)
                    Log.d("WebViewNav", msg)
                }
                binding.progressBar.isVisible = false
                updateNavButtons()
                
                // Update verified URL on completion
                if (url != null && url.startsWith("http")) {
                    lastVerifiedPageUrl = url
                }
                if (isXrModeActive) presentation?.updateUrl(url)
                
                val script = VideoDetectionBridge.getInjectionScript()
                webView.evaluateJavascript(script, null)
                injectFocusDetection()
                
                // Diagnostic logging on page finish (XR-FIRST)
                WebViewSettings.logDiagnostics(webView)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // RETURN FALSE: This is what fixed navigation in the backup.
                // Let the WebView handle everything internally.
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return request?.let { resourceInterceptor.shouldIntercept(it) } ?: super.shouldInterceptRequest(view, request)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                val context = ResolveContext(
                    pageUrl = view?.url ?: "",
                    userAgent = view?.settings?.userAgentString ?: "",
                    cookies = CookieManager.getInstance().getCookie(view?.url)
                )
                url?.let { videoResolver.onNetworkResource(it, context) }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                AlertDialog.Builder(this@BrowserActivity)
                    .setTitle("SSL Error")
                    .setMessage("SSL certificate is invalid. Proceed anyway?")
                    .setPositiveButton("Proceed") { _, _ -> handler?.proceed() }
                    .setNegativeButton("Cancel") { _, _ -> handler?.cancel() }
                    .show()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                // CRITICAL FIX: Proper window transport handling from backup
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = webView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = "JS [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()} (line: ${consoleMessage?.lineNumber()})"
                DiagnosticsLog.add("Nav-JS", msg)
                return super.onConsoleMessage(consoleMessage)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
            }
        }
    }

    private fun injectFocusDetection() {
        val script = """
            (function() {
                if (window.FocusInjected) return;
                window.FocusInjected = true;
                
                function checkFocus(e) {
                    var isInput = e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable;
                    if (isInput && window.InputBridge) {
                        window.InputBridge.onInputFocused(true);
                    }
                }
                
                document.addEventListener('focusin', checkFocus, true);
                
                // Also check if something is already focused
                if (document.activeElement) {
                    checkFocus({target: document.activeElement});
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun setupStandardControls() {
        binding.btnExitBrowser.setOnClickListener { finish() }
        binding.btnEnterXr.setOnClickListener { toggleXrMode(true) }
        
        binding.btnMenu.setOnClickListener { 
            if (resolvedSource != null) {
                openPhonePlayer()
            }
        }

        updateVideoButtonState()

        binding.urlInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val url = browserManager.formatUrl(v.text.toString())
                webView.loadUrl(url)
                v.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupXrControls() {
        binding.btnExitXr.setOnClickListener { toggleXrMode(false) }
        binding.btnRemotePower.setOnClickListener { setBlackoutVisible(true) }
        binding.btnRemoteNotif.setOnClickListener {
            SettingsStore.toggleTemporaryHudNotifications()
            updateXrNotifButtonUI()
        }
        updateXrNotifButtonUI()
        
        binding.ivRemoteVideoStatus.setOnClickListener {
            if (!isPlayerActiveInXr && resolvedSource != null) {
                openXrPlayer()
            }
        }

        binding.btnRemoteBrowserBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        binding.remoteUrlInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val url = browserManager.formatUrl(v.text.toString())
                webView.loadUrl(url)
                v.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
        
        binding.btnPlayerClose.setOnClickListener { 
            MediaSessionManager.closePlayer(this)
            closeXrPlayer()
        }
        
        binding.btnPlayerPlayPause.setOnClickListener { 
            val p = MediaSessionManager.getPlayer(this)
            if (p.isPlaying) p.pause() else p.play()
        }
        binding.btnPlayerRewind.setOnClickListener { 
            val p = MediaSessionManager.getPlayer(this)
            p.seekTo(kotlin.math.max(0L, p.currentPosition - 10000))
        }
        binding.btnPlayerForward.setOnClickListener { 
            val p = MediaSessionManager.getPlayer(this)
            p.seekTo(kotlin.math.min(p.duration, p.currentPosition + 10000))
        }
        
        binding.btnPlayerQuality.setOnClickListener { showQualityMenu() }
        binding.btnPlayerSpeed.setOnClickListener { showSpeedMenu() }
        binding.btnPlayerAudio.setOnClickListener { showAudioMenu() }
        binding.btnPlayerSubs.setOnClickListener { showSubsMenu() }

        binding.playerSeekBar.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                MediaSessionManager.getPlayer(this@BrowserActivity).seekTo(slider.value.toLong())
            }
        })

        binding.cardTouchpad.setOnTouchListener { _, event ->
            if (!isXrModeActive) return@setOnTouchListener false
            val pres = presentation
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchpadProcessor.reset()
                    lastTouchX = event.x; lastTouchY = event.y
                    downX = event.x; downY = event.y
                    isScrollMode = false
                    isPinchMode = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                    initialPinchDist = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                    lastTwoFingerY = (event.getY(0) + event.getY(1)) / 2f
                    isScrollMode = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val currentDist = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                        if (!isPinchMode && abs(currentDist - initialPinchDist) > 40f) {
                            isPinchMode = true
                            isScrollMode = false
                            DiagnosticsLog.add("Touchpad", "PINCH_MODE: Start")
                        }
                        
                        if (isPinchMode) {
                            if (currentDist > initialPinchDist + 30f) {
                                pres?.zoomIn()
                                initialPinchDist = currentDist
                            } else if (currentDist < initialPinchDist - 30f) {
                                pres?.zoomOut()
                                initialPinchDist = currentDist
                            }
                            return@setOnTouchListener true
                        }
                        
                        if (isScrollMode) {
                            val currentY = (event.getY(0) + event.getY(1)) / 2f
                            val dy = lastTwoFingerY - currentY
                            lastTwoFingerY = currentY
                            pres?.receiveScroll(dy * SettingsStore.touchpadScrollSpeed * 2f)
                            return@setOnTouchListener true
                        }
                    }
                    
                    if (!isScrollMode && !isPinchMode) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        lastTouchX = event.x; lastTouchY = event.y
                        val out = touchpadProcessor.process(dx, dy, event.eventTime)
                        if (out.dx != 0f || out.dy != 0f) pres?.receiveCursorMove(out.dx, out.dy)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val wasSpecialMode = isScrollMode || isPinchMode
                    isScrollMode = false
                    isPinchMode = false
                    if (wasSpecialMode) return@setOnTouchListener true
                    
                    val dist = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()).toFloat()
                    Log.d("XR Keyboard", "Touchpad UP: dist=$dist threshold=$tapThreshold presActive=${presentation != null}")
                    if (dist < tapThreshold) {
                        Log.d("XR Keyboard", "Touchpad TAP: calling presentation.receiveClick()")
                        presentation?.receiveClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> { isScrollMode = false; isPinchMode = false }
            }
            true
        }
    }

    private fun btnXrOpenVideoState(ready: Boolean) {
        val color = if (ready) {
            com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorTertiary)
        } else {
            com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        }
        
        binding.ivRemoteVideoStatus.imageTintList = android.content.res.ColorStateList.valueOf(color)
        binding.ivRemoteVideoStatus.alpha = if (ready) 1.0f else 0.4f
        binding.ivRemoteVideoStatus.isEnabled = ready
        
        binding.tvXrVideoStatus.text = if (ready) getString(R.string.xr_video_found) else getString(R.string.xr_video_not_found)
        binding.tvXrVideoStatus.setTextColor(color)
        
        // Show status block only if player is not active
        binding.clusterVideoStatus.isVisible = !isPlayerActiveInXr
        binding.clusterPlayer.isVisible = isPlayerActiveInXr
    }

    private fun updateXrNotifButtonUI() {
        val enabled = SettingsStore.hudNotificationsEnabled
        binding.btnRemoteNotif.setImageResource(if (enabled) R.drawable.ic_bell else R.drawable.ic_bell_off)
        binding.btnRemoteNotif.alpha = if (enabled) 1.0f else 0.6f
    }

    private fun updateVideoButtonState() {
        val ready = resolvedSource != null
        binding.btnMenu.isEnabled = ready
        binding.btnMenu.isClickable = ready
        binding.btnMenu.alpha = if (ready) 1.0f else 0.5f
        
        val colorAttr = if (ready) com.google.android.material.R.attr.colorTertiary else com.google.android.material.R.attr.colorOnSurfaceVariant
        val color = com.google.android.material.color.MaterialColors.getColor(binding.root, colorAttr)
        binding.btnMenu.imageTintList = android.content.res.ColorStateList.valueOf(color)
    }

    override fun onDisplayChanged(info: DisplaySessionManager.ExternalDisplayInfo?) {
        runOnUiThread {
            updateXrButtonVisibility()
            if (isXrModeActive) {
                binding.clusterVideoStatus.isVisible = !isPlayerActiveInXr
                binding.clusterPlayer.isVisible = isPlayerActiveInXr
            }
            if (info != null && !isXrModeActive && SettingsStore.autoEnterXrMode) toggleXrMode(true)
            else if (info == null && isXrModeActive) {
                restoreOriginalBrightness()
                setBlackoutVisible(false)
                toggleXrMode(false)
            }
            updateXrNotifButtonUI()
        }
    }
    override fun onDisplaysUpdated(displays: List<DisplaySessionManager.ExternalDisplayInfo>, selectedDisplayId: Int?) {
        runOnUiThread { updateXrButtonVisibility() }
    }

    private fun updateXrButtonVisibility() {
        val info = DisplaySessionManager.getExternalDisplayInfo()
        val connected = info != null
        
        binding.btnEnterXr.apply {
            isVisible = !isXrModeActive
            isEnabled = connected
            alpha = if (connected) 1.0f else 0.35f
        }
    }

    private fun toggleXrMode(active: Boolean) {
        if (isFinishing || isDestroyed || isTransitioning || isXrModeActive == active) return
        isTransitioning = true
        try {
            if (active) {
                val info = DisplaySessionManager.getExternalDisplayInfo() ?: return
                val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val display = dm.getDisplay(info.displayId) ?: return

                (webView.parent as? ViewGroup)?.removeView(webView)
                
                presentation?.dismiss()
                presentation = BrowserPresentation(this, display, webView)
                presentation?.setOnDismissListener { if (isXrModeActive && !isTransitioning) runOnUiThread { toggleXrMode(false) } }
                presentation?.show()
                
                binding.standardBrowserUi.isVisible = false
                binding.remoteBrowserUi.isVisible = true
                isXrModeActive = true
                binding.remoteUrlInput.setText(webView.url)
                
                // Move WebView to Presentation (Context remains fixed XR-First)
                WebViewSettings.logDiagnostics(webView, "XR-FIRST (TRANSITION)")
                
                // Sync video status immediately
                btnXrOpenVideoState(resolvedSource != null)
            } else {
                if (isPlayerActiveInXr) {
                    val p = MediaSessionManager.getPlayer(this)
                    if (p.isPlaying) {
                        presentation?.hidePlayer()
                        openPhonePlayer()
                    } else closeXrPlayer()
                }
                presentation?.let { it.detachWebView(); it.dismiss() }
                presentation = null
                resumeBrowser()
                (webView.parent as? ViewGroup)?.removeView(webView)
                binding.webViewContainer.addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                binding.remoteBrowserUi.isVisible = false
                binding.standardBrowserUi.isVisible = true
                isXrModeActive = false
                
                WebViewSettings.logDiagnostics(webView, "XR-FIRST (RETURN)")
            }
        } catch (e: Exception) {
            if (webView.parent == null) binding.webViewContainer.addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } finally {
            isTransitioning = false
            updateXrButtonVisibility()
        }
    }

    private fun openPhonePlayer() {
        val source = resolvedSource ?: return
        val intent = Intent(this, PlayerActivity::class.java).apply { putExtra("source", source) }
        startActivity(intent)
    }

    private fun openXrPlayer() {
        val source = resolvedSource ?: return
        suspendBrowser()
        MediaSessionManager.prepare(this, source)
        val player = MediaSessionManager.getPlayer(this)
        player.addListener(playerListener)
        presentation?.showPlayer(player, source.title ?: source.url.substringAfterLast("/"))
        
        isPlayerActiveInXr = true
        binding.clusterVideoStatus.isVisible = false
        binding.clusterPlayer.isVisible = true
        
        mainHandler.post(playbackTicker)
        player.play()
        updateRemotePlayerStatus()
    }

    private fun closeXrPlayer() {
        if (isPlayerActiveInXr) {
             try {
                 val player = MediaSessionManager.getPlayer(this)
                 player.removeListener(playerListener)
             } catch (e: Exception) {}
        }
        MediaSessionManager.closePlayer(this)
        presentation?.hidePlayer()
        resumeBrowser()
        
        isPlayerActiveInXr = false
        binding.clusterPlayer.isVisible = false
        binding.clusterVideoStatus.isVisible = true
        
        mainHandler.removeCallbacks(playbackTicker)
        
        // Refresh status icon state
        btnXrOpenVideoState(resolvedSource != null)
    }

    private fun updatePlayerProgress() {
        val p = MediaSessionManager.getPlayer(this)
        val pos = p.currentPosition
        val dur = p.duration
        if (dur > 0) {
            binding.playerSeekBar.valueTo = dur.toFloat()
            binding.playerSeekBar.value = pos.toFloat().coerceIn(0f, dur.toFloat())
            binding.tvPlayerPosition.text = formatTime(pos)
            binding.tvPlayerDuration.text = formatTime(dur)
        }
        updatePlayPauseIcon(p.isPlaying)
        updateRemotePlayerStatus()
    }

    private fun updateRemotePlayerStatus() {
        val p = MediaSessionManager.getPlayer(this)
        val quality = MediaPrefsStore.getVideoQuality(this)
        binding.btnPlayerQuality.text = quality
        binding.btnPlayerSpeed.text = String.format(Locale.getDefault(), "%.1fx", p.playbackParameters.speed)
        
        val tracks = p.currentTracks
        val hasAudio = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        
        // CC is active if any text track is selected
        val subsActive = textGroups.any { it.isSelected }
        
        binding.btnPlayerAudio.isEnabled = hasAudio
        binding.btnPlayerAudio.alpha = if (hasAudio) 1.0f else 0.4f
        
        binding.btnPlayerSubs.isEnabled = textGroups.isNotEmpty()
        binding.btnPlayerSubs.alpha = if (subsActive) 1.0f else 0.4f
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayerPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBlackoutLogic() {
        binding.blackoutOverlay.setOnTouchListener { view, event ->
            if (!binding.blackoutOverlay.isVisible) return@setOnTouchListener false
            if (event.pointerCount > 1) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    blackoutDownX = event.x; blackoutDownY = event.y
                    blackoutMoved = false
                    view.translationY = 0f
                    binding.blackoutHint.alpha = 1f
                    binding.blackoutHint.isVisible = true
                    restoreOriginalBrightness()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - blackoutDownY
                    if (abs(dy) > tapThreshold) blackoutMoved = true
                    if (blackoutMoved) view.translationY = dy.coerceAtMost(0f)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dy = event.y - blackoutDownY
                    if (dy < -200f && abs(dy) > abs(event.x - blackoutDownX)) {
                        setBlackoutVisible(false)
                    } else {
                        view.animate().translationY(0f).setDuration(200).start()
                        mainHandler.postDelayed({ binding.blackoutHint.isVisible = false }, 2000)
                    }
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupImeProxy() {
        // We override the view to provide our bridge InputConnection
        imeProxy = object : androidx.appcompat.widget.AppCompatEditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                val ic = super.onCreateInputConnection(outAttrs) ?: return null
                return object : InputConnectionWrapper(ic, true) {
                    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        if (isXrModeActive && text != null) {
                            presentation?.commitTextToWebView(text.toString())
                            return true
                        }
                        return super.commitText(text, newCursorPosition)
                    }

                    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                        if (isXrModeActive && beforeLength > 0) {
                            presentation?.deleteTextFromWebView()
                            return true
                        }
                        return super.deleteSurroundingText(beforeLength, afterLength)
                    }

                    override fun sendKeyEvent(event: KeyEvent?): Boolean {
                        if (isXrModeActive && event != null) {
                            presentation?.dispatchKeyToWebView(event)
                            return true
                        }
                        return super.sendKeyEvent(event)
                    }
                }
            }
        }
        
        imeProxy.id = binding.hiddenImeProxy.id
        imeProxy.layoutParams = binding.hiddenImeProxy.layoutParams
        imeProxy.alpha = binding.hiddenImeProxy.alpha
        imeProxy.background = null
        imeProxy.inputType = android.text.InputType.TYPE_CLASS_TEXT
        imeProxy.imeOptions = EditorInfo.IME_ACTION_SEARCH
        
        val parent = binding.hiddenImeProxy.parent as ViewGroup
        val index = parent.indexOfChild(binding.hiddenImeProxy)
        parent.removeViewAt(index)
        parent.addView(imeProxy, index)

        imeProxy.setOnFocusChangeListener { v, hasFocus ->
            Log.d("XR Keyboard", "Proxy Focus Change: $hasFocus")
            if (hasFocus) {
                WindowCompat.getInsetsController(window, v).show(WindowInsetsCompat.Type.ime())
            }
        }

        imeProxy.setOnEditorActionListener { _, _, _ ->
            if (isXrModeActive) {
                presentation?.dispatchKeyToWebView(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                presentation?.dispatchKeyToWebView(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            false
        }
    }

    fun requestKeyboardFocus() {
        Log.d("XR Keyboard", "Activity: requestKeyboardFocus called")
        runOnUiThread {
            imeProxy.requestFocus()
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.restartInput(imeProxy)
            WindowCompat.getInsetsController(window, imeProxy).show(WindowInsetsCompat.Type.ime())
            Log.d("XR Keyboard", "Activity: Proxy focus hasFocus=${imeProxy.hasFocus()}")
        }
    }

    private fun setBlackoutVisible(visible: Boolean) {
        if (binding.blackoutOverlay.isVisible == visible) return
        
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        if (visible) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            dimWindowBrightness()
        } else {
            restoreOriginalBrightness()
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
        binding.blackoutOverlay.isVisible = visible
    }

    private fun dimWindowBrightness() {
        if (!hasOriginalWindowBrightness) captureOriginalBrightness()
        val target = SettingsStore.touchpadDimLevel
        val start = getEstimatedCurrentBrightness()
        dimAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = 400
            addUpdateListener { applyWindowBrightness(it.animatedValue as Float) }
            start()
        }
    }

    private fun restoreOriginalBrightness() {
        if (!hasOriginalWindowBrightness) return
        applyWindowBrightness(originalWindowBrightness)
        hasOriginalWindowBrightness = false
    }

    private fun captureOriginalBrightness() {
        originalWindowBrightness = window.attributes.screenBrightness
        originalSystemBrightness = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) { 1f }
        hasOriginalWindowBrightness = true
    }

    private fun applyWindowBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0f, 1f) }
    }

    private fun getEstimatedCurrentBrightness(): Float {
        val win = window.attributes.screenBrightness
        return if (win >= 0f) win else originalSystemBrightness
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, getString(R.string.browser_home))
        if (resolvedSource != null) popup.menu.add(0, 4, 0, "▶ Open Video")
        if (SettingsStore.developerModeUnlocked && SettingsStore.browserDiagnosticsEnabled) popup.menu.add(0, 3, 0, getString(R.string.browser_diagnostics))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> webView.loadUrl("https://www.google.com")
                3 -> showDiagnostics()
                4 -> openPhonePlayer()
            }; true
        }; popup.show()
    }

    private fun showDiagnostics() {
        browserManager.getExtendedDiagnostics(webView, legacyVideoDetector, videoResolver) { diag ->
            val dialogBinding = com.xrdesk.databinding.DialogBrowserDiagnosticsBinding.inflate(layoutInflater)
            val info = StringBuilder()
            
            info.append("--- XR WEBVIEW STATE ---\n\n")
            val dm = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val displayInfo = DisplaySessionManager.getExternalDisplayInfo()
            val display = dm.getDisplay(displayInfo?.displayId ?: -1)
            
            info.append("Display: ${if (isXrModeActive) "External (${display?.name ?: "Unknown"})" else "Phone"}\n")
            info.append("Mode: ${if (isXrModeActive) "TABLET" else "MOBILE"}\n")
            info.append("Resolution: ${display?.mode?.physicalWidth ?: webView.width}x${display?.mode?.physicalHeight ?: webView.height}\n")
            info.append("WebView Width: ${webView.width}px\n\n")

            info.append("UA String: ${webView.settings.userAgentString}\n\n")

            presentation?.getWebViewMetrics { metricsJson ->
                runOnUiThread {
                    try {
                        val metrics = org.json.JSONObject(metricsJson)
                        info.append("navigator.userAgent: ${metrics.optString("userAgent")}\n")
                        info.append("innerWidth: ${metrics.optInt("innerWidth")}\n")
                        info.append("clientWidth: ${metrics.optInt("documentWidth")}\n")
                        info.append("devicePixelRatio: ${metrics.optDouble("devicePixelRatio")}\n\n")
                    } catch(e: Exception) {}
                    
                    info.append("Current URL: ${diag["currentUrl"]}\n")
                    info.append("Browser Version: ${diag["version"]}\n")
                    
                    dialogBinding.textGeneralInfo.text = info.toString()
                }
            }
            
            val abDiag = AdBlockEngine.isBlocked("test.com", false)
            dialogBinding.textJSMetrics.text = "AdBlock: Active"
            
            AlertDialog.Builder(this)
                .setTitle(R.string.browser_diagnostics_title)
                .setView(dialogBinding.root)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun showQualityMenu() {
        val p = MediaSessionManager.getPlayer(this)
        val videoGroup = p.currentTracks.groups.find { it.type == C.TRACK_TYPE_VIDEO } ?: return
        val dialogBinding = DialogTrackSelectionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.player_video_quality).setView(dialogBinding.root).setPositiveButton(android.R.string.ok, null).create()
        
        dialogBinding.dialogHeader.isVisible = false
        dialogBinding.trackSelectionContainer.removeAllViews()
        
        val group = RadioGroup(this)
        val autoBtn = RadioButton(this).apply { 
            text = getString(R.string.player_auto)
            isChecked = p.trackSelectionParameters.maxVideoWidth == Int.MAX_VALUE
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        autoBtn.setOnClickListener { 
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearVideoSizeConstraints().build()
            MediaPrefsStore.saveVideoQuality(this, "Auto")
            updateRemotePlayerStatus()
            dialog.dismiss()
        }
        group.addView(autoBtn)
        
        for (i in 0 until videoGroup.length) {
            val format = videoGroup.getTrackFormat(i)
            val btn = RadioButton(this).apply { 
                text = String.format(Locale.getDefault(), "%dp", format.height)
                isChecked = !autoBtn.isChecked && videoGroup.isTrackSelected(i)
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
            btn.setOnClickListener { 
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(format.width, format.height)
                    .setMinVideoSize(format.width, format.height)
                    .build()
                MediaPrefsStore.saveVideoQuality(this, format.height.toString())
                updateRemotePlayerStatus()
                dialog.dismiss()
            }
            group.addView(btn)
        }
        dialogBinding.trackSelectionContainer.addView(group)
        dialog.show()
        adjustDialogWidth(dialog)
    }

    private fun showSpeedMenu() {
        val p = MediaSessionManager.getPlayer(this)
        val dialogBinding = DialogTrackSelectionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.player_speed).setView(dialogBinding.root).setPositiveButton(android.R.string.ok, null).create()
        
        dialogBinding.dialogHeader.isVisible = false
        dialogBinding.trackSelectionContainer.removeAllViews()
        
        val group = RadioGroup(this)
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
            val btn = RadioButton(this).apply { 
                text = if (speed == 1.0f) getString(R.string.player_speed_normal) else String.format(Locale.getDefault(), "%.2fx", speed)
                isChecked = kotlin.math.abs(p.playbackParameters.speed - speed) < 0.1f
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
            btn.setOnClickListener { 
                p.setPlaybackSpeed(speed)
                updateRemotePlayerStatus()
                dialog.dismiss()
            }
            group.addView(btn)
        }
        dialogBinding.trackSelectionContainer.addView(group)
        dialog.show()
        adjustDialogWidth(dialog)
    }

    private fun showAudioMenu() {
        val p = MediaSessionManager.getPlayer(this)
        val dialogBinding = DialogTrackSelectionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.player_audio).setView(dialogBinding.root).setPositiveButton(android.R.string.ok, null).create()
        
        dialogBinding.dialogHeader.isVisible = false
        dialogBinding.trackSelectionContainer.removeAllViews()
        
        val group = RadioGroup(this)
        p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { trackGroup ->
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getTrackFormat(i)
                val btn = RadioButton(this).apply { 
                    text = format.language?.let { code -> 
                        when(code.lowercase()){
                            "ru","rus"->getString(R.string.player_lang_ru)
                            "en","eng"->getString(R.string.player_lang_en)
                            else->Locale(code).getDisplayLanguage(Locale.getDefault())
                        } 
                    } ?: "Unknown"
                    isChecked = trackGroup.isTrackSelected(i)
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                }
                btn.setOnClickListener { 
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setPreferredAudioLanguage(format.language)
                        .build()
                    MediaPrefsStore.saveAudioLanguage(this@BrowserActivity, format.language)
                    updateRemotePlayerStatus()
                    dialog.dismiss()
                }
                group.addView(btn)
            }
        }
        dialogBinding.trackSelectionContainer.addView(group)
        dialog.show()
        adjustDialogWidth(dialog)
    }

    private fun showSubsMenu() {
        val p = MediaSessionManager.getPlayer(this)
        val dialogBinding = DialogTrackSelectionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.player_subtitles).setView(dialogBinding.root).setPositiveButton(android.R.string.ok, null).create()
        
        dialogBinding.dialogHeader.isVisible = false
        dialogBinding.trackSelectionContainer.removeAllViews()
        
        val group = RadioGroup(this)
        val textGroups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val offBtn = RadioButton(this).apply { 
            text = getString(R.string.player_off)
            isChecked = textGroups.none { it.isSelected }
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        offBtn.setOnClickListener { 
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setPreferredTextLanguage(null)
                .build()
            MediaPrefsStore.saveSubtitlePreference(this, false, null)
            updateRemotePlayerStatus()
            dialog.dismiss()
        }
        group.addView(offBtn)
        
        textGroups.forEach { trackGroup ->
            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getTrackFormat(i)
                val btn = RadioButton(this).apply { 
                    text = format.language?.let { code -> 
                        when(code.lowercase()){
                            "ru","rus"->getString(R.string.player_lang_ru)
                            "en","eng"->getString(R.string.player_lang_en)
                            else->Locale(code).getDisplayLanguage(Locale.getDefault())
                        } 
                    } ?: "Unknown"
                    isChecked = trackGroup.isTrackSelected(i)
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                }
                btn.setOnClickListener { 
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(format.language)
                        .build()
                    MediaPrefsStore.saveSubtitlePreference(this@BrowserActivity, true, format.language)
                    updateRemotePlayerStatus()
                    dialog.dismiss()
                }
                group.addView(btn)
            }
        }
        dialogBinding.trackSelectionContainer.addView(group)
        dialog.show()
        adjustDialogWidth(dialog)
    }

    private fun adjustDialogWidth(dialog: AlertDialog) {
        val width = (resources.displayMetrics.widthPixels * 0.45).toInt().coerceAtMost(dpToPx(500))
        dialog.window?.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSecs = ms / 1000
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    private fun updateNavButtons() {}

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        Log.d("WebViewNav", "Activity onResume")
        
        // Sync status bar visibility with current blackout state
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (binding.blackoutOverlay.isVisible) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
        
        if (!isPlayerActiveInXr) {
            resumeBrowser()
        }
        updateXrButtonVisibility()
    }

    override fun onPause() {
        Log.d("WebViewNav", "Activity onPause")
        suspendBrowser()
        super.onPause()
    }

    private fun suspendBrowser() {
        if (isBrowserSuspended) return
        Log.d("BrowserLifecycle", "Suspending browser")
        webView.onPause()
        webView.pauseTimers()
        webView.visibility = View.INVISIBLE
        isBrowserSuspended = true
    }

    private fun resumeBrowser() {
        if (!isBrowserSuspended) return
        Log.d("BrowserLifecycle", "Resuming browser")
        webView.onResume()
        webView.resumeTimers()
        webView.visibility = View.VISIBLE
        isBrowserSuspended = false
    }

    override fun onDestroy() {
        DisplaySessionManager.removeListener(this)
        presentation?.let { it.detachWebView(); it.dismiss() }
        MediaSessionManager.release()
        super.onDestroy()
    }
}
