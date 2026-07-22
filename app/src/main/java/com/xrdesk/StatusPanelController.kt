package com.xrdesk

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Modern HUD Engine (RC4 Final). 
 * Implements minimalist modes: Full Info, Compact Bar, Compact Card.
 * Features zero-margin edge triggers and hardware-backed Wi-Fi standard detection.
 */
class StatusPanelController(
    private val context: Context,
    private val container: HUDContainer,
    private val isPreview: Boolean = false
) {
    private val themedContext = if (isPreview) context else ContextThemeWrapper(context, R.style.Theme_XRDesk)

    private object Palette {
        const val BG_CARD = 0xCC1A1C1E.toInt()
        const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        const val TEXT_SECONDARY = 0xB3FFFFFF.toInt()
        const val STROKE = 0x33FFFFFF.toInt()
        const val DIVIDER = 0x44FFFFFF.toInt()
        const val DEBUG_ZONE = 0x330000FF.toInt()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var rootLayout: FrameLayout? = null
    private var isVisible = false
    private val hideRunnable = Runnable { hide() }

    private val interpolator = FastOutSlowInInterpolator()
    private val animDuration = 320L

    private var currentData = getSampleData()

    data class HUDData(
        val time: String,
        val batteryPct: Int,
        val isCharging: Boolean,
        val wifiSsid: String?,
        val wifiFreq: Int,
        val wifiStandard: Int,
        val btLabel: String,
        val operator: String?,
        val networkType: String?,
        val airplaneMode: Boolean
    )

    init {
        initUI()
        
        if (isPreview) {
            refreshUI()
        } else {
            observeData()
        }
        observeSettings()
        if (isPreview) show(immediate = true)
    }

    private fun initUI() {
        rootLayout = FrameLayout(themedContext).apply {
            alpha = 0f
            visibility = View.GONE
        }
        container.addView(rootLayout!!, createRootParams())
    }

    private fun observeData() {
        scope.launch {
            HUDSystemMonitor.batteryState.collectLatest { info ->
                currentData = currentData.copy(batteryPct = info.level, isCharging = info.isCharging)
                refreshUI()
            }
        }
        scope.launch {
            HUDSystemMonitor.connectivityState.collectLatest { info ->
                currentData = currentData.copy(
                    wifiSsid = info.wifiSsid,
                    wifiFreq = info.wifiFrequency,
                    wifiStandard = info.wifiStandard,
                    airplaneMode = info.airplaneMode,
                    btLabel = formatBtLabel(info.bluetoothEnabled, info.bluetoothDeviceCount, info.bluetoothDeviceName)
                )
                refreshUI()
            }
        }
        scope.launch {
            HUDSystemMonitor.mobileState.collectLatest { info ->
                currentData = currentData.copy(operator = info.operatorName, networkType = info.networkType)
                refreshUI()
            }
        }
        scope.launch {
            HUDSystemMonitor.timeState.collectLatest { ts ->
                currentData = currentData.copy(time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts)))
                refreshUI()
            }
        }
        scope.launch {
            HUDSystemMonitor.cursorEvents.collectLatest { 
                checkEdgeTrigger(it.x, it.y) 
            }
        }
    }

    private fun observeSettings() {
        scope.launch { SettingsStore.hudModeFlow.collectLatest { refreshUI() } }
        scope.launch { SettingsStore.hudPositionFlow.collectLatest { refreshUI() } }
        scope.launch { SettingsStore.hudSizeFlow.collectLatest { refreshUI() } }
        scope.launch { SettingsStore.hudActivationZoneFlow.collectLatest { refreshUI() } }
        scope.launch { SettingsStore.hudDebugAlwaysShowFlow.collectLatest { 
            if (it) show(immediate = true) else hide()
        } }
        scope.launch { SettingsStore.hudDebugHighlightZoneFlow.collectLatest { refreshUI() } }
        scope.launch { SettingsStore.hudDebugShowBoundsFlow.collectLatest { refreshUI() } }
    }

    fun refreshUI() {
        val root = rootLayout ?: return
        root.removeAllViews()
        
        // Fix: Preview must always be visible regardless of global hudEnabled setting
        if (!isPreview && !SettingsStore.hudEnabled && !SettingsStore.hudDebugAlwaysShow) {
            hide(immediate = true)
            return
        }

        // Fix: Force visible for preview since hide() might have been called
        if (isPreview) {
            root.visibility = View.VISIBLE
            root.alpha = 1f
        }

        if (SettingsStore.hudDebugHighlightZone) {
            drawActivationZoneDebug(root)
        }

        val mode = SettingsStore.hudMode
        when (mode) {
            SettingsStore.HUD_MODE_FULL_INFO -> buildFullInfo(root, currentData)
            else -> buildCompactStatus(root, currentData)
        }

        if (SettingsStore.hudDebugShowBounds) {
            root.setBackgroundResource(R.drawable.debug_red_border)
        } else {
            root.background = null
        }
    }

    private fun getScale(): Float {
        if (!isPreview) return 1.0f
        val w = container.getWidth()
        if (w <= 0) return 0.22f
        return w.toFloat() / 1920f
    }

    private fun drawActivationZoneDebug(root: FrameLayout) {
        val density = context.resources.displayMetrics.density
        val scale = getScale()
        val zonePx = (SettingsStore.hudActivationZoneDp * density * scale).toInt()
        val color = Palette.DEBUG_ZONE 
        
        listOf(Gravity.TOP, Gravity.BOTTOM, Gravity.START, Gravity.END).forEach { side ->
            val v = View(themedContext).apply { setBackgroundColor(color) }
            val lp = FrameLayout.LayoutParams(
                if (side == Gravity.TOP || side == Gravity.BOTTOM) ViewGroup.LayoutParams.MATCH_PARENT else zonePx,
                if (side == Gravity.START || side == Gravity.END) ViewGroup.LayoutParams.MATCH_PARENT else zonePx
            ).apply { gravity = side }
            root.addView(v, lp)
        }
    }

    fun buildFullInfo(root: FrameLayout, data: HUDData) {
        val density = context.resources.displayMetrics.density
        val scale = getScale()
        val size = SettingsStore.hudSizeDp * scale
        val fontSizeClock = size * 0.44f 
        val fontSizeSub = size * 0.22f
        val iconSize = size * 0.35f 
        val margin = (24 * density * scale).toInt()

        val trCard = createGlassCard(0)
        val trLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val px = (24 * density * scale).toInt()
            val py = (12 * density * scale).toInt()
            setPadding(px, py, px, py)
        }
        trCard.addView(trLayout)
        addText(trLayout, data.time, fontSizeClock, true)
        addDivider(trLayout, fontSizeClock)
        
        val wifiSsid = data.wifiSsid
        val airplaneMode = data.airplaneMode
        val networkIcon: Int
        val networkLabel: String
        
            when {
                airplaneMode -> {
                    networkIcon = R.drawable.ic_airplane
                    networkLabel = context.getString(R.string.net_airplane)
                }
                !wifiSsid.isNullOrBlank() -> {
                    networkIcon = R.drawable.ic_wifi
                    networkLabel = wifiSsid
                }
                data.operator != null -> {
                    networkIcon = R.drawable.ic_mobile
                    networkLabel = data.operator
                }
                else -> {
                    networkIcon = R.drawable.ic_mobile
                    networkLabel = context.getString(R.string.net_none)
                }
            }
        
        addIcon(trLayout, networkIcon, iconSize)
        addDivider(trLayout, fontSizeClock)
        
        if (data.btLabel != "Выкл") {
            addIcon(trLayout, R.drawable.ic_bluetooth, iconSize)
            addDivider(trLayout, fontSizeClock)
        }
        addIcon(trLayout, if (data.isCharging) R.drawable.ic_bolt else R.drawable.ic_battery, iconSize)
        addSpace(trLayout, (4 * density * scale).toInt())
        addText(trLayout, "${data.batteryPct}%", fontSizeSub, alpha = 0.85f)
        root.addView(trCard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(margin, margin, margin, margin)
        })

        val blCard = createGlassCard(0)
        val blLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            val px = (24 * density * scale).toInt()
            val py = (18 * density * scale).toInt()
            setPadding(px, py, px, py)
        }
        blCard.addView(blLayout)
        addText(blLayout, networkLabel, fontSizeSub, true)
        if (!wifiSsid.isNullOrBlank()) {
            addText(blLayout, getWifiVersionLabel(data), fontSizeSub * 0.75f, alpha = 0.75f)
        } else if (data.operator != null) {
            addText(blLayout, data.networkType ?: "Mobile Data", fontSizeSub * 0.75f, alpha = 0.75f)
        }
        root.addView(blCard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(margin, margin, margin, margin)
        })
    }

    fun buildCompactStatus(root: FrameLayout, data: HUDData) {
        val density = themedContext.resources.displayMetrics.density
        val scale = getScale()
        val size = SettingsStore.hudSizeDp * scale
        val heightPx = (size * 1.3f * density).toInt() 
        val margin = (24 * density * scale).toInt()
        val card = createGlassCard(heightPx)
        
        val mainRow = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val ph = (16 * density * scale).toInt() 
            val pv = (8 * density * scale).toInt()
            setPadding(ph, pv, ph, pv)
        }
        card.addView(mainRow)

        // 1. Clock (Fixed)
        val timeTv = TextView(themedContext).apply {
            text = data.time
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.35f)
            setTextColor(Palette.TEXT_PRIMARY)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, (14 * density * scale).toInt(), 0)
        }
        mainRow.addView(timeTv)

        // 2. WiFi / Mobile (Flexible)
        val wifiLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            
            val wifiSsid = data.wifiSsid
            val airplaneMode = data.airplaneMode
            val wifiIcon: Int
            val wifiLabel: String
            
            when {
                airplaneMode -> {
                    wifiIcon = R.drawable.ic_airplane
                    wifiLabel = context.getString(R.string.net_airplane)
                }
                !wifiSsid.isNullOrBlank() -> {
                    wifiIcon = R.drawable.ic_wifi
                    wifiLabel = wifiSsid
                }
                data.operator != null -> {
                    wifiIcon = R.drawable.ic_mobile
                    wifiLabel = context.getString(R.string.net_cellular)
                }
                else -> {
                    wifiIcon = R.drawable.ic_mobile
                    wifiLabel = context.getString(R.string.net_none)
                }
            }
            
            addIcon(this, wifiIcon, size * 0.38f) // Slightly larger icons
            addSpace(this, (8 * density * scale).toInt())
            TextView(themedContext).apply {
                text = wifiLabel
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.25f)
                setTextColor(Palette.TEXT_PRIMARY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                addView(this)
            }
        }
        mainRow.addView(wifiLayout)

        // 3. Bluetooth (Flexible) - Hide if "Выкл"
        if (data.btLabel != "Выкл") {
            addSpace(mainRow, (16 * density * scale).toInt())
            val btLayout = LinearLayout(themedContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addIcon(this, R.drawable.ic_bluetooth, size * 0.38f)
                addSpace(this, (8 * density * scale).toInt())
                TextView(themedContext).apply {
                    text = data.btLabel
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.25f)
                    setTextColor(Palette.TEXT_PRIMARY)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    alpha = 0.9f
                    addView(this)
                }
            }
            mainRow.addView(btLayout)
        }

        addSpace(mainRow, (16 * density * scale).toInt())

        // 4. Battery (Fixed)
        val battLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val battIcon = if (data.isCharging) R.drawable.ic_bolt else R.drawable.ic_battery
            addIcon(this, battIcon, size * 0.38f)
            addSpace(this, (6 * density * scale).toInt())
            addText(this, "${data.batteryPct}%", size * 0.24f)
        }
        mainRow.addView(battLayout)

        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, heightPx).apply {
            gravity = getMainCardGravity()
            setMargins(margin, margin, margin, margin)
        })
    }

    private fun createGlassCard(heightPx: Int): MaterialCardView {
        val density = themedContext.resources.displayMetrics.density
        val scale = getScale()
        return MaterialCardView(themedContext).apply {
            cardElevation = 0f
            strokeWidth = (1 * density * scale).toInt().coerceAtLeast(1)
            strokeColor = Palette.STROKE
            setCardBackgroundColor(Palette.BG_CARD) 
            radius = if (heightPx > 0) heightPx.toFloat() * HUDConstants.RADIUS_RATIO else 24f * density * scale
        }
    }

    private fun addText(parent: ViewGroup, text: String, sizeSp: Float, bold: Boolean = false, alpha: Float = 1f) {
        val tv = TextView(themedContext).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Palette.TEXT_PRIMARY)
            this.alpha = alpha
            if (bold) setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        parent.addView(tv)
    }

    private fun addIcon(parent: ViewGroup, res: Int, sizePx: Float) {
        val iv = ImageView(themedContext).apply {
            setImageResource(res)
            layoutParams = LinearLayout.LayoutParams(sizePx.toInt(), sizePx.toInt())
            setColorFilter(Palette.TEXT_PRIMARY)
        }
        parent.addView(iv)
    }

    private fun addDivider(parent: ViewGroup, sizeSp: Float) {
        val v = View(themedContext).apply {
            val scale = getScale()
            val w = (themedContext.resources.displayMetrics.density * scale * 1).toInt().coerceAtLeast(1)
            layoutParams = LinearLayout.LayoutParams(w, (sizeSp * 0.8f).toInt()).apply {
                marginEnd = (sizeSp * 0.5f).toInt()
                marginStart = (sizeSp * 0.5f).toInt()
            }
            setBackgroundColor(Palette.DIVIDER)
        }
        parent.addView(v)
    }

    private fun addSpace(parent: ViewGroup, size: Int, vertical: Boolean = false) {
        val v = View(themedContext)
        parent.addView(v, if (vertical) LinearLayout.LayoutParams(1, size) else LinearLayout.LayoutParams(size, 1))
    }

    private fun getWifiVersionLabel(data: HUDData): String {
        return when (data.wifiStandard) {
            6 -> "Wi-Fi 6 (ax)"
            5 -> "Wi-Fi 5 (ac)"
            4 -> "Wi-Fi 4 (n)"
            else -> "Connected"
        }
    }

    private fun formatBtLabel(enabled: Boolean, count: Int, name: String?): String = when {
        !enabled -> "Выкл"
        count == 0 -> "Вкл"
        count == 1 -> name ?: "1 устройство"
        else -> "$count устройства"
    }

    private fun getSampleData() = HUDData(
        time = "12:45",
        batteryPct = 86,
        isCharging = false,
        wifiSsid = "Home_WiFi",
        wifiFreq = 5000,
        wifiStandard = 5,
        btLabel = "AirPods Pro",
        operator = "Moldcell",
        networkType = "4G",
        airplaneMode = false
    )

    private fun createRootParams(): WindowManager.LayoutParams {
        val info = HUDManager.getDebugInfo()
        val params = WindowManager.LayoutParams(
            info?.width ?: 1920,
            info?.height ?: 1080,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        return params
    }

    private fun getMainCardGravity(): Int {
        val pos = SettingsStore.hudPosition
        return if (pos == SettingsStore.HUD_POS_BOTTOM) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    private fun checkEdgeTrigger(x: Float, y: Float) {
        if (!SettingsStore.hudEnabled || !SettingsStore.hudStatusPanelEnabled || SettingsStore.hudDebugAlwaysShow) return
        val w = container.getWidth()
        val h = container.getHeight()
        if (w <= 0 || h <= 0) return
        val info = HUDManager.getDebugInfo()
        val density = info?.densityDpi?.toFloat()?.div(160f) ?: context.resources.displayMetrics.density
        val zonePx = SettingsStore.hudActivationZoneDp * density
        val overshoot = 20f * density
        
        if (x >= -overshoot && x <= zonePx || x >= (w - zonePx) && x <= (w + overshoot) || 
            y >= -overshoot && y <= zonePx || y >= (h - zonePx) && y <= (h + overshoot)) {
            show()
        }
    }

    fun show(immediate: Boolean = false) {
        val root = rootLayout ?: return
        isVisible = true
        root.visibility = View.VISIBLE
        handler.removeCallbacks(hideRunnable)
        // Fix: Do not schedule auto-hide for preview
        if (!isPreview && (!SettingsStore.hudEnabled || !SettingsStore.hudDebugAlwaysShow)) {
            handler.postDelayed(hideRunnable, SettingsStore.hudHideDelayMs)
        }
        if (immediate) { 
            root.animate().cancel()
            root.alpha = 1f 
            return 
        }
        root.animate().cancel()
        root.animate().alpha(1f)
            .setDuration(animDuration)
            .setInterpolator(interpolator)
            .setListener(null)
            .start()
    }

    fun hide(immediate: Boolean = false) {
        val root = rootLayout ?: return
        if (SettingsStore.hudDebugAlwaysShow) return 
        isVisible = false
        if (immediate) { 
            root.animate().cancel()
            root.alpha = 0f
            root.visibility = View.GONE 
            return 
        }
        root.animate().cancel()
        root.animate().alpha(0f)
            .setDuration(animDuration)
            .setInterpolator(interpolator)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { 
                    root.visibility = View.GONE 
                }
            }).start()
    }

    fun destroy() {
        handler.removeCallbacks(hideRunnable)
        scope.cancel()
        rootLayout?.let { container.removeView(it) }
        rootLayout = null
    }

    fun getDebugStatus(): String {
        val info = HUDManager.getDebugInfo()
        return buildString {
            appendLine("HUD Enabled: ${SettingsStore.hudEnabled}")
            appendLine("Visible: ${if (isVisible) "YES" else "NO"}")
            appendLine("Mode: ${getModeName(SettingsStore.hudMode)}")
            appendLine("Position: ${getPositionName(SettingsStore.hudPosition)}")
            if (info != null) {
                appendLine("Display ID: ${info.displayId}")
                appendLine("Normalized: ${info.width} x ${info.height}")
                appendLine("Rotation: ${getRotationString(info.rotation)}")
            }
            appendLine("Scale: ${"%.2f".format(getScale())}")
            appendLine("AlwaysShow: ${SettingsStore.hudDebugAlwaysShow}")
        }
    }

    private fun getRotationString(rot: Int) = when(rot) {
        0 -> "0°"
        1 -> "90°"
        2 -> "180°"
        3 -> "270°"
        else -> "$rot"
    }

    private fun getModeName(mode: Int) = when(mode) {
        SettingsStore.HUD_MODE_FULL_INFO -> "Full Information"
        SettingsStore.HUD_MODE_COMPACT_BAR -> "Compact Bar"
        SettingsStore.HUD_MODE_COMPACT_CARD -> "Compact Card"
        else -> "Unknown"
    }

    private fun getPositionName(pos: Int) = when(pos) {
        SettingsStore.HUD_POS_TOP -> "Top"
        SettingsStore.HUD_POS_BOTTOM -> "Bottom"
        SettingsStore.HUD_POS_LEFT -> "Left"
        SettingsStore.HUD_POS_RIGHT -> "Right"
        else -> "Unknown"
    }
}
