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

    private var lastAppliedMode: Int? = null
    private var lastAppliedSize: Float = -1f
    private var lastAppliedPosition: Int = -1
    private var lastAppliedScale: Float = -1f
    private var lastAppliedDebugHighlight: Boolean = false
    private var lastAppliedDebugBounds: Boolean = false
    private var lastAppliedActivationZone: Float = -1f

    private var activeCache: HUDViewCache? = null

    private sealed class HUDViewCache {
        class Detailed(
            val trCard: MaterialCardView,
            val timeTv: TextView,
            val networkIconIv: ImageView,
            val networkDivider: View,
            val btIconIv: ImageView,
            val btDivider: View,
            val batteryIconIv: ImageView,
            val batteryPctTv: TextView,
            val blCard: MaterialCardView,
            val networkLabelTv: TextView,
            val networkDetailTv: TextView
        ) : HUDViewCache()

        class Compact(
            val card: MaterialCardView,
            val timeTv: TextView,
            val wifiIconIv: ImageView,
            val wifiLabelTv: TextView,
            val btLayout: LinearLayout,
            val btIconIv: ImageView,
            val btLabelTv: TextView,
            val batteryIconIv: ImageView,
            val batteryPctTv: TextView,
            val btSeparator: View
        ) : HUDViewCache()
    }

    private data class UIState(
        val time: String,
        val networkIcon: Int,
        val networkLabel: String,
        val networkDetail: String?,
        val batteryIcon: Int,
        val batteryPct: String,
        val showBluetooth: Boolean,
        val btLabel: String
    )

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

        val mode = SettingsStore.hudMode
        val size = SettingsStore.hudSizeDp
        val pos = SettingsStore.hudPosition
        val scale = getScale()
        val highlight = SettingsStore.hudDebugHighlightZone
        val showBounds = SettingsStore.hudDebugShowBounds
        val zone = SettingsStore.hudActivationZoneDp

        val needsRebuild = mode != lastAppliedMode || 
                          size != lastAppliedSize || 
                          pos != lastAppliedPosition || 
                          scale != lastAppliedScale || 
                          highlight != lastAppliedDebugHighlight ||
                          showBounds != lastAppliedDebugBounds ||
                          zone != lastAppliedActivationZone

        if (needsRebuild) {
            root.removeAllViews()
            if (highlight) {
                drawActivationZoneDebug(root)
            }

            activeCache = when (mode) {
                SettingsStore.HUD_MODE_FULL_INFO -> buildDetailedLayout(root)
                else -> buildCompactLayout(root)
            }

            if (showBounds) {
                root.setBackgroundResource(R.drawable.debug_red_border)
            } else {
                root.background = null
            }

            lastAppliedMode = mode
            lastAppliedSize = size
            lastAppliedPosition = pos
            lastAppliedScale = scale
            lastAppliedDebugHighlight = highlight
            lastAppliedDebugBounds = showBounds
            lastAppliedActivationZone = zone

            updateMutableProperties(force = true)
        } else {
            updateMutableProperties(force = false)
        }
    }

    private fun updateMutableProperties(force: Boolean) {
        val cache = activeCache ?: return
        val ui = getUIState(currentData)

        when (cache) {
            is HUDViewCache.Detailed -> {
                updateText(cache.timeTv, ui.time, force)
                updateImage(cache.networkIconIv, ui.networkIcon, force)
                
                val btVisibility = if (ui.showBluetooth) View.VISIBLE else View.GONE
                updateVisibility(cache.btIconIv, btVisibility, force)
                updateVisibility(cache.btDivider, btVisibility, force)
                
                updateImage(cache.batteryIconIv, ui.batteryIcon, force)
                updateText(cache.batteryPctTv, ui.batteryPct, force)
                
                updateText(cache.networkLabelTv, ui.networkLabel, force)
                updateVisibility(cache.networkDetailTv, if (ui.networkDetail != null) View.VISIBLE else View.GONE, force)
                ui.networkDetail?.let { updateText(cache.networkDetailTv, it, force) }
            }
            is HUDViewCache.Compact -> {
                updateText(cache.timeTv, ui.time, force)
                updateImage(cache.wifiIconIv, ui.networkIcon, force)
                updateText(cache.wifiLabelTv, ui.networkLabel, force)
                
                val btVisibility = if (ui.showBluetooth) View.VISIBLE else View.GONE
                updateVisibility(cache.btLayout, btVisibility, force)
                updateVisibility(cache.btSeparator, btVisibility, force)
                if (ui.showBluetooth) {
                    updateText(cache.btLabelTv, ui.btLabel, force)
                }
                
                updateImage(cache.batteryIconIv, ui.batteryIcon, force)
                updateText(cache.batteryPctTv, ui.batteryPct, force)
            }
        }
    }

    private fun updateText(view: TextView, text: String, force: Boolean) {
        if (force || view.text != text) {
            view.text = text
        }
    }

    private fun updateImage(view: ImageView, resId: Int, force: Boolean) {
        if (force || view.tag != resId) {
            view.setImageResource(resId)
            view.tag = resId
        }
    }

    private fun updateVisibility(view: View, visibility: Int, force: Boolean) {
        if (force || view.visibility != visibility) {
            view.visibility = visibility
        }
    }

    private fun getUIState(data: HUDData): UIState {
        val wifiSsid = data.wifiSsid
        val airplaneMode = data.airplaneMode
        val networkIcon: Int
        val networkLabel: String
        val networkDetail: String?
        
        when {
            airplaneMode -> {
                networkIcon = R.drawable.ic_airplane
                networkLabel = context.getString(R.string.net_airplane)
                networkDetail = null
            }
            !wifiSsid.isNullOrBlank() -> {
                networkIcon = R.drawable.ic_wifi
                networkLabel = wifiSsid
                networkDetail = getWifiVersionLabel(data)
            }
            data.operator != null -> {
                networkIcon = R.drawable.ic_mobile
                networkLabel = data.operator
                networkDetail = data.networkType ?: "Mobile Data"
            }
            else -> {
                networkIcon = R.drawable.ic_mobile
                networkLabel = context.getString(R.string.net_none)
                networkDetail = null
            }
        }

        val batteryIcon = if (data.isCharging) R.drawable.ic_bolt else R.drawable.ic_battery
        val showBluetooth = data.btLabel != "Выкл"

        return UIState(
            time = data.time,
            networkIcon = networkIcon,
            networkLabel = networkLabel,
            networkDetail = networkDetail,
            batteryIcon = batteryIcon,
            batteryPct = "${data.batteryPct}%",
            showBluetooth = showBluetooth,
            btLabel = data.btLabel
        )
    }

    private fun getScale(): Float {
        if (!isPreview) return 1.0f
        val w = container.getWidth()
        if (w <= 0) return 0.22f
        return w.toFloat() / 1920f
    }

    private fun rebuildHierarchy(root: FrameLayout, mode: Int, highlight: Boolean, showBounds: Boolean) {
        root.removeAllViews()
        activeCache = null

        if (highlight) {
            drawActivationZoneDebug(root)
        }

        activeCache = when (mode) {
            SettingsStore.HUD_MODE_FULL_INFO -> buildDetailedLayout(root)
            else -> buildCompactLayout(root)
        }

        if (showBounds) {
            root.setBackgroundResource(R.drawable.debug_red_border)
        } else {
            root.background = null
        }
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

    private fun buildDetailedLayout(root: FrameLayout): HUDViewCache.Detailed {
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
        
        val timeTv = addText(trLayout, "", fontSizeClock, true)
        addDivider(trLayout, fontSizeClock)
        
        val networkIconIv = addIcon(trLayout, R.drawable.ic_wifi, iconSize)
        val networkDivider = addDivider(trLayout, fontSizeClock)
        
        val btIconIv = addIcon(trLayout, R.drawable.ic_bluetooth, iconSize)
        val btDivider = addDivider(trLayout, fontSizeClock)
        
        val batteryIconIv = addIcon(trLayout, R.drawable.ic_battery, iconSize)
        addSpace(trLayout, (4 * density * scale).toInt())
        val batteryPctTv = addText(trLayout, "", fontSizeSub, alpha = 0.85f)
        
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
        val networkLabelTv = addText(blLayout, "", fontSizeSub, true)
        val networkDetailTv = addText(blLayout, "", fontSizeSub * 0.75f, alpha = 0.75f)
        
        root.addView(blCard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(margin, margin, margin, margin)
        })

        return HUDViewCache.Detailed(
            trCard = trCard,
            timeTv = timeTv,
            networkIconIv = networkIconIv,
            networkDivider = networkDivider,
            btIconIv = btIconIv,
            btDivider = btDivider,
            batteryIconIv = batteryIconIv,
            batteryPctTv = batteryPctTv,
            blCard = blCard,
            networkLabelTv = networkLabelTv,
            networkDetailTv = networkDetailTv
        )
    }

    private fun buildCompactLayout(root: FrameLayout): HUDViewCache.Compact {
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
        val timeTv = addText(mainRow, "", size * 0.35f, true)
        timeTv.setPadding(0, 0, (14 * density * scale).toInt(), 0)

        // 2. WiFi / Mobile (Flexible)
        val wifiLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val wifiIconIv = addIcon(wifiLayout, R.drawable.ic_wifi, size * 0.38f)
        addSpace(wifiLayout, (8 * density * scale).toInt())
        val wifiLabelTv = addText(wifiLayout, "", size * 0.25f)
        wifiLabelTv.maxLines = 1
        wifiLabelTv.ellipsize = android.text.TextUtils.TruncateAt.END
        mainRow.addView(wifiLayout)

        // 3. Bluetooth (Flexible)
        val btSeparator = addSpace(mainRow, (16 * density * scale).toInt())
        val btLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btIconIv = addIcon(btLayout, R.drawable.ic_bluetooth, size * 0.38f)
        addSpace(btLayout, (8 * density * scale).toInt())
        val btLabelTv = addText(btLayout, "", size * 0.25f)
        btLabelTv.alpha = 0.9f
        btLabelTv.maxLines = 1
        btLabelTv.ellipsize = android.text.TextUtils.TruncateAt.END
        mainRow.addView(btLayout)

        addSpace(mainRow, (16 * density * scale).toInt())

        // 4. Battery (Fixed)
        val battLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val batteryIconIv = addIcon(battLayout, R.drawable.ic_battery, size * 0.38f)
        addSpace(battLayout, (6 * density * scale).toInt())
        val batteryPctTv = addText(battLayout, "", size * 0.24f)
        mainRow.addView(battLayout)

        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, heightPx).apply {
            gravity = getMainCardGravity()
            setMargins(margin, margin, margin, margin)
        })

        return HUDViewCache.Compact(
            card = card,
            timeTv = timeTv,
            wifiIconIv = wifiIconIv,
            wifiLabelTv = wifiLabelTv,
            btLayout = btLayout,
            btIconIv = btIconIv,
            btLabelTv = btLabelTv,
            batteryIconIv = batteryIconIv,
            batteryPctTv = batteryPctTv,
            btSeparator = btSeparator
        )
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

    private fun addText(parent: ViewGroup, text: String, sizeSp: Float, bold: Boolean = false, alpha: Float = 1f): TextView {
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
        return tv
    }

    private fun addIcon(parent: ViewGroup, res: Int, sizePx: Float): ImageView {
        val iv = ImageView(themedContext).apply {
            setImageResource(res)
            layoutParams = LinearLayout.LayoutParams(sizePx.toInt(), sizePx.toInt())
            setColorFilter(Palette.TEXT_PRIMARY)
        }
        parent.addView(iv)
        return iv
    }

    private fun addDivider(parent: ViewGroup, sizeSp: Float): View {
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
        return v
    }

    private fun addSpace(parent: ViewGroup, size: Int, vertical: Boolean = false): View {
        val v = View(themedContext)
        parent.addView(v, if (vertical) LinearLayout.LayoutParams(1, size) else LinearLayout.LayoutParams(size, 1))
        return v
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
