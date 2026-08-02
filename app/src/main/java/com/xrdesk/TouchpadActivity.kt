package com.xrdesk

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import android.content.Intent
import android.os.Build
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.xrdesk.databinding.ActivityTouchpadBinding
import rikka.shizuku.Shizuku
import kotlin.math.abs

class TouchpadActivity : AppCompatActivity(), DisplaySessionManager.Listener {

    private lateinit var binding: ActivityTouchpadBinding
    private val processor = TouchpadProcessor(TouchpadTuning)
    private lateinit var blackoutManager: BlackoutManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var directStartCursor = android.graphics.PointF()
    private var touchSlopPx = 0f
    private var longPressCancelSlopPx = 0f
    private var longPressTimeout = 0
    private var longPressRunnable: Runnable? = null
    private lateinit var legacyScrollController: LegacyScrollController
    private lateinit var directScrollController: DirectScrollController
    private var activeScrollController = ActiveScrollController.NONE
    private var touchpadActive = false
    private var touchState = TouchState.IDLE
    private var suppressSingleUntilUp = false
    private lateinit var accessibilityGate: AccessibilityGateController
    private lateinit var backController: ControlSurfaceBackController

    private val appPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // App was launched successfully. We are back in TouchpadActivity.
            // Current UX doesn't require specific action here as AppLauncher already did the work.
            android.util.Log.d("TouchpadActivity", "App picker returned success")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("TouchpadActivity", "onCreate: hudEnabled=${SettingsStore.hudEnabled} hudNotificationsEnabled=${SettingsStore.hudNotificationsEnabled}")
        binding = ActivityTouchpadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        blackoutManager = BlackoutManager(this)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeHelper.applyTheme(this)
        applyEdgeToEdgePadding(binding.root)

        window.isNavigationBarContrastEnforced = false

        touchSlopPx = resources.displayMetrics.density * TOUCH_SLOP_DP
        longPressCancelSlopPx = resources.displayMetrics.density * LONG_PRESS_CANCEL_DP
        longPressTimeout = ViewConfiguration.getLongPressTimeout()
        legacyScrollController = LegacyScrollController(
            context = this,
            handler = handler,
            serviceProvider = { ControlAccessibilityService.current() }
        )
        directScrollController = DirectScrollController(
            context = this,
            touchpadSizeProvider = { binding.touchpadArea.width to binding.touchpadArea.height },
            serviceProvider = { ControlAccessibilityService.current() }
        )

        binding.touchpadBack.setOnClickListener {
            finish()
        }
  binding.touchpadLaunch.setOnClickListener {
            appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
        }
        binding.touchpadBlackout.setOnClickListener {
            blackoutManager.show()
        }
        binding.touchpadToggleNotif.setOnClickListener {
            SettingsStore.toggleTemporaryHudNotifications()
            updateNotifButtonUI()
        }
        binding.touchpadToolbar.setOnLongClickListener {
            toggleTuningPanel()
            true
        }

        accessibilityGate = AccessibilityGateController(
            activity = this,
            gate = binding.accessibilityGate,
            content = binding.touchpadContent,
            touchpadArea = binding.touchpadArea,
            tuningPanel = binding.tuningPanel,
            openSettingsButton = binding.btnOpenAccessibility,
            enableWithShizukuButton = binding.btnEnableAccessibilityShizuku,
            onEnabledChanged = { enabled ->
                if (!enabled) setTouchpadActive(false)
            },
            onShizukuStatusChanged = { alive ->
                val dpadAlpha = if (alive) 1f else 0.6f
                binding.dPadAbove.root.alpha = dpadAlpha
                binding.dPadBelow.root.alpha = dpadAlpha
            }
        )

        backController = ControlSurfaceBackController(
            activity = this,
            isControlActive = { touchpadActive },
            preBackHandler = { false }
        )

        binding.touchpadArea.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
        
        // Sync Notifications state reactively
        lifecycleScope.launch {
            SettingsStore.hudNotificationsEnabledFlow.collectLatest {
                updateNotifButtonUI()
            }
        }

        setupTuningControls()
        setupDPad()
        setTouchpadActive(false)
        showTouchpadIntroIfNeeded()
        
        updateNotifButtonUI()
    }

    override fun onStart() {
        super.onStart()
        DisplaySessionManager.addListener(this)
        accessibilityGate.onStart()
    }

    override fun onResume() {
        super.onResume()
        updateKeepScreenOn(true)
        updateNotifButtonUI()
        accessibilityGate.refresh()
        
        // Sync status bar visibility
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.statusBars())
        
        backController.warmUpOnResume()
    }

    override fun onPause() {
        cancelLongPress()
        exitScrollMode()
        updateKeepScreenOn(false)
        super.onPause()
    }

    override fun onStop() {
        DisplaySessionManager.removeListener(this)
        cancelLongPress()
        exitScrollMode()
        super.onStop()
    }

    override fun onDisplayChanged(info: DisplaySessionManager.ExternalDisplayInfo?) {
    }

    override fun onDestroy() {
        blackoutManager.destroy()
        cancelLongPress()
        exitScrollMode()
        accessibilityGate.onDestroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val rect = android.graphics.Rect()
            binding.touchpadArea.getGlobalVisibleRect(rect)
            val inTouchpad = rect.contains(event.rawX.toInt(), event.rawY.toInt())
            setTouchpadActive(inTouchpad)
            if (inTouchpad) {
                backController.warmUpOnActivation()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return super.onGenericMotionEvent(event)
    }

    private fun updateNotifButtonUI() {
        val enabled = SettingsStore.hudNotificationsEnabled
        binding.touchpadToggleNotif.setImageResource(if (enabled) R.drawable.ic_bell else R.drawable.ic_bell_off)
        binding.touchpadToggleNotif.alpha = if (enabled) 1f else 0.6f
    }

    private fun handleTouch(event: MotionEvent) {
        val service = serviceOrToast() ?: return
        if (suppressSingleUntilUp) {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    suppressSingleUntilUp = false
                    touchState = TouchState.IDLE
                    return
                }
                MotionEvent.ACTION_DOWN -> {
                    suppressSingleUntilUp = false
                }
                else -> return
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                processor.reset()
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                directStartCursor = service.getCursorPosition()
                touchState = TouchState.ONE_FINGER_DOWN
                scheduleLongPress(service)
                service.wakeCursor()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    cancelLongPress()
                    if (touchState == TouchState.DRAGGING) {
                        service.endContinuousGesture()
                    }
                    enterScrollMode(service, event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (touchState == TouchState.SCROLL_MODE && event.pointerCount >= 2) {
                    updateScrollMode(event)
                    return
                }

                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                val output = processor.process(dx, dy, event.eventTime)
                if (output.dx != 0f || output.dy != 0f) {
                    val boost = if (touchState == TouchState.DRAGGING) {
                        TouchpadTuning.dragBoost
                    } else {
                        1f
                    }
                    service.moveCursorBy(output.dx * boost, output.dy * boost)
                    if (touchState == TouchState.DRAGGING) {
                        val targetX = directStartCursor.x + (event.x - downX)
                        val targetY = directStartCursor.y + (event.y - downY)
                        service.updateContinuousGestureTo(targetX, targetY)
                    }
                }
                lastTouchX = event.x
                lastTouchY = event.y

                if (touchState == TouchState.ONE_FINGER_DOWN) {
                    val movedForLongPress = abs(event.x - downX) > longPressCancelSlopPx ||
                        abs(event.y - downY) > longPressCancelSlopPx
                    if (movedForLongPress) {
                        cancelLongPress()
                    }
                    val moved = abs(event.x - downX) > touchSlopPx ||
                        abs(event.y - downY) > touchSlopPx
                    if (moved) {
                        cancelLongPress()
                        touchState = TouchState.MOVING_CURSOR
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (touchState == TouchState.SCROLL_MODE && event.pointerCount <= 2) {
                    exitScrollMode()
                    suppressSingleUntilUp = true
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (touchState == TouchState.SCROLL_MODE) {
                    exitScrollMode()
                    return
                }
                if (touchState == TouchState.DRAGGING) {
                    service.endContinuousGesture()
                    touchState = TouchState.IDLE
                    return
                }
                val moved = abs(event.x - downX) > touchSlopPx ||
                    abs(event.y - downY) > touchSlopPx
                if (touchState == TouchState.ONE_FINGER_DOWN && !moved) {
                    service.tapAtCursor()
                }
                touchState = TouchState.IDLE
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                if (touchState == TouchState.DRAGGING) {
                    service.cancelContinuousGesture()
                }
                if (touchState == TouchState.SCROLL_MODE) {
                    exitScrollMode()
                }
                touchState = TouchState.IDLE
            }
        }
    }

    private fun resetTouchBaseline(event: MotionEvent) {}

    private fun scheduleLongPress(service: ControlAccessibilityService) {
        cancelLongPress()
        longPressRunnable = Runnable {
            if (touchState != TouchState.ONE_FINGER_DOWN) return@Runnable
            val moved = abs(lastTouchX - downX) > longPressCancelSlopPx ||
                abs(lastTouchY - downY) > longPressCancelSlopPx
            if (moved) return@Runnable
            touchState = TouchState.DRAGGING
            binding.touchpadArea.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            service.startContinuousGestureAtCursor()
        }
        handler.postDelayed(longPressRunnable!!, longPressTimeout.toLong())
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun enterScrollMode(service: ControlAccessibilityService, event: MotionEvent) {
        touchState = TouchState.SCROLL_MODE
        val useDirect = SettingsStore.touchpadDirectScrollGestureEnabled &&
            directScrollController.enter(service, event)
        if (useDirect) {
            activeScrollController = ActiveScrollController.DIRECT
            return
        }
        legacyScrollController.enter(service, event)
        activeScrollController = ActiveScrollController.LEGACY
    }

    private fun updateScrollMode(event: MotionEvent) {
        when (activeScrollController) {
            ActiveScrollController.DIRECT -> directScrollController.update(event)
            ActiveScrollController.LEGACY -> legacyScrollController.update(event)
            ActiveScrollController.NONE -> Unit
        }
    }

    private fun exitScrollMode() {
        if (touchState != TouchState.SCROLL_MODE) return
        touchState = TouchState.IDLE
        when (activeScrollController) {
            ActiveScrollController.DIRECT -> directScrollController.exit()
            ActiveScrollController.LEGACY -> legacyScrollController.exit()
            ActiveScrollController.NONE -> Unit
        }
        activeScrollController = ActiveScrollController.NONE
    }

    private fun toggleTuningPanel() {
        binding.tuningPanel.isVisible = !binding.tuningPanel.isVisible
    }

    private fun setupDPad() {
        val pos = SettingsStore.dPadPosition
        val dPadAbove = binding.dPadAbove.root
        val dPadBelow = binding.dPadBelow.root

        dPadAbove.isVisible = pos == SettingsStore.DPAD_ABOVE
        dPadBelow.isVisible = pos == SettingsStore.DPAD_BELOW

        val activeDPad = if (pos == SettingsStore.DPAD_ABOVE) binding.dPadAbove else binding.dPadBelow
        
        activeDPad.btnDpadUp.setOnClickListener { 
            android.util.Log.i("XRDesk", "BUTTON_CLICK: D-Pad UP")
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val service = ControlAccessibilityService.current()
            if (service == null) {
                android.util.Log.e("XRDesk", "BUTTON_ERROR: ControlAccessibilityService is NOT running/current is null")
            } else {
                service.navigateFocus(android.view.View.FOCUS_UP) 
            }
        }
        activeDPad.btnDpadDown.setOnClickListener { 
            android.util.Log.i("XRDesk", "BUTTON_CLICK: D-Pad DOWN")
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val service = ControlAccessibilityService.current()
            if (service == null) {
                android.util.Log.e("XRDesk", "BUTTON_ERROR: ControlAccessibilityService is NOT running/current is null")
            } else {
                service.navigateFocus(android.view.View.FOCUS_DOWN) 
            }
        }
        activeDPad.btnDpadLeft.setOnClickListener { 
            android.util.Log.i("XRDesk", "BUTTON_CLICK: D-Pad LEFT")
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val service = ControlAccessibilityService.current()
            if (service == null) {
                android.util.Log.e("XRDesk", "BUTTON_ERROR: ControlAccessibilityService is NOT running/current is null")
            } else {
                service.navigateFocus(android.view.View.FOCUS_LEFT) 
            }
        }
        activeDPad.btnDpadRight.setOnClickListener { 
            android.util.Log.i("XRDesk", "BUTTON_CLICK: D-Pad RIGHT")
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val service = ControlAccessibilityService.current()
            if (service == null) {
                android.util.Log.e("XRDesk", "BUTTON_ERROR: ControlAccessibilityService is NOT running/current is null")
            } else {
                service.navigateFocus(android.view.View.FOCUS_RIGHT) 
            }
        }
        activeDPad.btnDpadOk.setOnClickListener { 
            android.util.Log.i("XRDesk", "BUTTON_CLICK: D-Pad OK")
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val service = ControlAccessibilityService.current()
            if (service == null) {
                android.util.Log.e("XRDesk", "BUTTON_ERROR: ControlAccessibilityService is NOT running/current is null")
            } else {
                service.clickFocused() 
            }
        }

        // Expanded Remote Buttons
        activeDPad.btnRemotePlayPause.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            ControlAccessibilityService.current()?.injectKeyEvent(85) // KEYCODE_MEDIA_PLAY_PAUSE
        }
        activeDPad.btnRemotePlayPause.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            ControlAccessibilityService.current()?.injectKeyEvent(85, longPress = true)
            true
        }
        
        activeDPad.btnRemoteRewind.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            ControlAccessibilityService.current()?.injectKeyEvent(89) // KEYCODE_MEDIA_REWIND
        }
        activeDPad.btnRemoteRewind.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            ControlAccessibilityService.current()?.injectKeyEvent(89, longPress = true)
            true
        }

        activeDPad.btnRemoteFastForward.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            ControlAccessibilityService.current()?.injectKeyEvent(90) // KEYCODE_MEDIA_FAST_FORWARD
        }
        activeDPad.btnRemoteFastForward.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            ControlAccessibilityService.current()?.injectKeyEvent(90, longPress = true)
            true
        }
    }

    private fun setTouchpadActive(active: Boolean) {
        touchpadActive = active
        binding.touchpadArea.isActivated = active
    }

    private fun setupTuningControls() {
        configureSlider(
            binding.labelBaseGain,
            binding.sliderBaseGain,
            min = 0.4f,
            max = 2.4f,
            current = TouchpadTuning.baseGain,
            format = { getString(R.string.touchpad_base_gain_value, it) }
        ) { TouchpadTuning.baseGain = it }

        configureSlider(
            binding.labelAccel,
            binding.sliderAccel,
            min = 0.6f,
            max = 3.5f,
            current = TouchpadTuning.maxAccelGain,
            format = { getString(R.string.touchpad_acceleration_value, it) }
        ) { TouchpadTuning.maxAccelGain = it }

        configureSlider(
            binding.labelSpeed,
            binding.sliderSpeed,
            min = 0.6f,
            max = 2.8f,
            current = TouchpadTuning.speedForMaxAccel,
            format = { getString(R.string.touchpad_speed_for_max_accel_value, it) }
        ) { TouchpadTuning.speedForMaxAccel = it }

        configureSlider(
            binding.labelJitter,
            binding.sliderJitter,
            min = 0.1f,
            max = 2.0f,
            current = TouchpadTuning.jitterThresholdPx,
            format = { getString(R.string.touchpad_jitter_threshold_value, it) }
        ) { TouchpadTuning.jitterThresholdPx = it }

        configureSlider(
            binding.labelSmoothing,
            binding.sliderSmoothing,
            min = 0.05f,
            max = 0.85f,
            current = TouchpadTuning.emaAlpha,
            format = { getString(R.string.touchpad_smoothing_value, it) }
        ) { TouchpadTuning.emaAlpha = it }

        configureSlider(
            binding.labelScroll,
            binding.sliderScroll,
            min = 8f,
            max = 64f,
            current = TouchpadTuning.scrollStepPx,
            format = { getString(R.string.touchpad_scroll_step_value, it) }
        ) { TouchpadTuning.scrollStepPx = it }
    }

    private fun configureSlider(
        labelView: android.widget.TextView,
        slider: SeekBar,
        min: Float,
        max: Float,
        current: Float,
        format: (Float) -> String,
        onChange: (Float) -> Unit
    ) {
        slider.max = 1000
        val initial = ((current - min) / (max - min) * slider.max).toInt()
        slider.progress = initial.coerceIn(0, slider.max)
        labelView.text = format(current)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = min + (max - min) * (progress / slider.max.toFloat())
                labelView.text = format(value)
                onChange(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun serviceOrToast(): ControlAccessibilityService? {
        val service = ControlAccessibilityService.current()
        if (service == null) {
            ToastHelper.show(this, R.string.touchpad_accessibility_required_toast)
        }
        return service
    }

    private fun updateKeepScreenOn(visible: Boolean) {
        if (visible && SettingsStore.keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun showTouchpadIntroIfNeeded() {
        if (SettingsStore.touchpadIntroShown) return
        val message = getString(
            R.string.touchpad_intro_message,
            getString(R.string.touchpad_intro_gesture_move),
            getString(R.string.touchpad_intro_gesture_tap),
            getString(R.string.touchpad_intro_gesture_drag),
            getString(R.string.touchpad_intro_back_behavior),
            getString(R.string.touchpad_intro_exit_hint),
            "" // Placeholder for removed dim behavior
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.touchpad_intro_title)
            .setMessage(message)
            .setPositiveButton(R.string.touchpad_intro_got_it) { dialog, _ -> dialog.dismiss() }
            .show()
        SettingsStore.setTouchpadIntroShown(this)
    }

    companion object {
        private const val TOUCH_SLOP_DP = 8f
        private const val LONG_PRESS_CANCEL_DP = 3f
    }


    private enum class TouchState {
        IDLE,
        ONE_FINGER_DOWN,
        MOVING_CURSOR,
        DRAGGING,
        SCROLL_MODE
    }

    private enum class ActiveScrollController {
        NONE,
        LEGACY,
        DIRECT
    }
}
