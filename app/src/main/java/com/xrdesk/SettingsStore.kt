package com.xrdesk

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsStore {
    private const val PREFS_NAME = "xrdesk_settings"
    private const val PREF_APP_LANGUAGE = "app_language"
    private const val LANGUAGE_SYSTEM = "system"
    private const val PREF_SCROLL_SPEED_SCALE = "tp_scroll_scale"

    const val THEME_LIGHT = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
    const val THEME_DARK = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
    const val THEME_AMOLED = 3
    const val THEME_CUSTOM = 4

    const val DPAD_HIDDEN = 0
    const val DPAD_ABOVE = 1
    const val DPAD_BELOW = 2

    // HUD MODES
    const val HUD_MODE_FULL_INFO = 0
    const val HUD_MODE_COMPACT_BAR = 1
    const val HUD_MODE_COMPACT_CARD = 2
    // Mode 3 (Vertical Panel) was removed in RC4

    // HUD POSITIONS
    const val HUD_POS_TOP = 0
    const val HUD_POS_BOTTOM = 1

    var nightMode = THEME_LIGHT
        private set
    var dPadPosition = DPAD_ABOVE
        private set
    var cursorScale = 1.5f
        private set
    var cursorAlpha = 1.0f
        private set
    var cursorHideDelayMs = 2500L
        private set
    var cursorColor = 0xFFFFFFFF.toInt()
        private set
    var appLanguageTag = LANGUAGE_SYSTEM
        private set
    var keepScreenOn = true
        private set
    var touchpadAutoDimEnabled = true
        private set
    var touchpadDimLevel = 0.05f
        private set
    var touchpadIntroShown = false
        private set
    var touchpadScrollSpeed = 1.0f
        private set
    var touchpadScrollInverted = true
        private set
    var touchpadScrollStepDp = 6.0f
        private set
    var touchpadDirectScrollGestureEnabled = false
        private set
    var touchpadDirectScrollGain = 1.0f
        private set
    var touchpadDirectScrollStepDp = 32.0f
        private set
    var touchpadAutoFocusEnabled = false
        private set
    var switchBarEnabled = false
        private set
    var switchBarScale = 1.0f
        private set
    var touchpadAutoLockEnabled = false
        private set
    var touchpadAutoLockTimeoutMs = 60_000L
        private set

    // HUD SETTINGS
    var hudEnabled = false
        private set
    var developerModeUnlocked = false
        private set
    var hudMode = HUD_MODE_FULL_INFO
        private set
    var hudPosition = HUD_POS_TOP
        private set
    var hudSizeDp = 80f
        private set
    var hudActivationZoneDp = 20f
        private set
    var hudHideDelayMs = 3000L
        private set
    var hudStatusPanelEnabled = true
        private set
    var hudNotificationsEnabled = true
        private set
    var hudNotificationSizeDp = 240f
        private set
    var appNotificationsEnabled = true
        private set
    var appNotificationDuration = 0 // 0 = Short, 1 = Long
        private set

    // SESSION-ONLY (Temporary Overrides)
    var originalHudNotificationsState = true
        private set
    var temporaryOverrideActive = false
        private set
    private var isSessionCaptured = false

    // REACTIVE FLOWS
    private val _hudEnabledFlow = MutableStateFlow(false)
    val hudEnabledFlow = _hudEnabledFlow.asStateFlow()

    private val _developerModeUnlockedFlow = MutableStateFlow(false)
    val developerModeUnlockedFlow = _developerModeUnlockedFlow.asStateFlow()

    private val _hudModeFlow = MutableStateFlow(HUD_MODE_FULL_INFO)
    val hudModeFlow = _hudModeFlow.asStateFlow()

    private val _hudPositionFlow = MutableStateFlow(HUD_POS_TOP)
    val hudPositionFlow = _hudPositionFlow.asStateFlow()

    private val _hudSizeFlow = MutableStateFlow(80f)
    val hudSizeFlow = _hudSizeFlow.asStateFlow()

    private val _hudActivationZoneFlow = MutableStateFlow(20f)
    val hudActivationZoneFlow = _hudActivationZoneFlow.asStateFlow()

    // DEBUG
    var developerMode = false
        private set
    var hudDebugAlwaysShow = false
        private set
    var hudDebugHighlightZone = false
        private set
    var hudDebugShowBounds = false
        private set

    private val _hudDebugAlwaysShowFlow = MutableStateFlow(false)
    val hudDebugAlwaysShowFlow = _hudDebugAlwaysShowFlow.asStateFlow()

    private val _hudDebugHighlightZoneFlow = MutableStateFlow(false)
    val hudDebugHighlightZoneFlow = _hudDebugHighlightZoneFlow.asStateFlow()

    private val _hudDebugShowBoundsFlow = MutableStateFlow(false)
    val hudDebugShowBoundsFlow = _hudDebugShowBoundsFlow.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        android.util.Log.d("SettingsStore", "init: loading preferences")
        
        nightMode = prefs.getInt("night_mode", THEME_LIGHT)
        cursorScale = prefs.getFloat("cursor_scale", 1.5f)
        cursorAlpha = prefs.getFloat("cursor_alpha", 1.0f)
        cursorHideDelayMs = prefs.getLong("cursor_hide_delay_ms", 2500L)
        cursorColor = prefs.getInt("cursor_color", 0xFFFFFFFF.toInt())
        appLanguageTag = prefs.getString(PREF_APP_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        keepScreenOn = prefs.getBoolean("keep_screen_on", true)
        touchpadAutoDimEnabled = prefs.getBoolean("touchpad_auto_dim", true)
        touchpadDimLevel = prefs.getFloat("touchpad_dim_level", 0.05f)
        touchpadIntroShown = prefs.getBoolean("touchpad_intro_shown", false)
        touchpadScrollSpeed = prefs.getFloat(PREF_SCROLL_SPEED_SCALE, 1.0f)
        touchpadScrollInverted = prefs.getBoolean("tp_scroll_invert", true)
        touchpadDirectScrollGestureEnabled = prefs.getBoolean("tp_scroll_direct_gesture", false)
        touchpadDirectScrollGain = prefs.getFloat("tp_scroll_direct_gain", 1.0f)
        touchpadDirectScrollStepDp = prefs.getFloat("tp_scroll_direct_step_dp", 32.0f)
        touchpadAutoFocusEnabled = prefs.getBoolean("tp_auto_focus", false)
        touchpadScrollStepDp = prefs.getFloat("tp_scroll_step_dp", 6.0f)
        switchBarEnabled = prefs.getBoolean("switch_bar_enabled", false)
        switchBarScale = prefs.getFloat("switch_bar_scale", 1.0f)
        touchpadAutoLockEnabled = prefs.getBoolean("tp_auto_lock_enabled", false)
        touchpadAutoLockTimeoutMs = prefs.getLong("tp_auto_lock_timeout", 60_000L)
        
        hudEnabled = prefs.getBoolean("hud_enabled", false)
        developerModeUnlocked = prefs.getBoolean("developer_unlocked", false)
        val savedMode = prefs.getInt("hud_mode", HUD_MODE_FULL_INFO)
        // Migration: If old Vertical Panel (3) was selected, move to Full Info (0)
        hudMode = if (savedMode == 3) HUD_MODE_FULL_INFO else savedMode
        hudPosition = prefs.getInt("hud_position", HUD_POS_TOP)
        hudSizeDp = prefs.getFloat("hud_size_dp", 80f)
        hudActivationZoneDp = prefs.getFloat("hud_activation_zone", 20f)
        hudHideDelayMs = prefs.getLong("hud_hide_delay", 3000L)
        hudStatusPanelEnabled = prefs.getBoolean("hud_status_panel_enabled", true)
        hudNotificationSizeDp = prefs.getFloat("hud_notification_size_dp", 240f)
        hudNotificationsEnabled = prefs.getBoolean("hud_notifications_enabled", true)
        appNotificationsEnabled = prefs.getBoolean("app_notifications_enabled", true)
        appNotificationDuration = prefs.getInt("app_notification_duration", 0)

        // Reset temporary session state on init (non-persistent)
        temporaryOverrideActive = false
        isSessionCaptured = false
        
        // Cleanup legacy persistent session flags if they exist from older versions
        if (prefs.contains("temp_override_active")) {
            android.util.Log.d("SettingsStore", "init: cleaning up legacy session flags")
            persist(context) {
                remove("temp_override_active")
                remove("original_hud_notif_state")
            }
        }

        android.util.Log.d("SettingsStore", "init: hudEnabled=$hudEnabled hudNotificationsEnabled=$hudNotificationsEnabled")

        developerMode = prefs.getBoolean("developer_mode", false)
        hudDebugAlwaysShow = prefs.getBoolean("hud_debug_always_show", false)
        hudDebugHighlightZone = prefs.getBoolean("hud_debug_highlight_zone", false)
        hudDebugShowBounds = prefs.getBoolean("hud_debug_show_bounds", false)

        dPadPosition = prefs.getInt("dpad_position", DPAD_ABOVE)

        syncHudFlows()
    }

    fun setNightMode(context: Context, value: Int) {
        nightMode = value
        persist(context) { putInt("night_mode", value) }
        ThemeEngine.applyPreset(context, value)
        val modeToApply = when (value) {
            THEME_AMOLED -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            THEME_CUSTOM -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> value
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(modeToApply)
        XRDeskApp.recreateAllActivities()
    }

    fun setCursorScale(context: Context, value: Float) {
        cursorScale = value
        persist(context) { putFloat("cursor_scale", value) }
        ControlAccessibilityService.requestCursorAppearanceRefresh()
    }

    fun setCursorAlpha(context: Context, value: Float) {
        cursorAlpha = value
        persist(context) { putFloat("cursor_alpha", value) }
        ControlAccessibilityService.requestCursorAppearanceRefresh()
    }

    fun setCursorColor(context: Context, value: Int) {
        cursorColor = value
        persist(context) { putInt("cursor_color", value) }
        ControlAccessibilityService.requestCursorAppearanceRefresh()
    }

    fun setCursorHideDelay(context: Context, valueMs: Long) {
        cursorHideDelayMs = valueMs
        persist(context) { putLong("cursor_hide_delay_ms", valueMs) }
    }

    fun setKeepScreenOn(context: Context, enabled: Boolean) {
        keepScreenOn = enabled
        persist(context) { putBoolean("keep_screen_on", enabled) }
    }

    fun setTouchpadAutoDimEnabled(context: Context, enabled: Boolean) {
        touchpadAutoDimEnabled = enabled
        persist(context) { putBoolean("touchpad_auto_dim", enabled) }
    }

    fun setTouchpadDimLevel(context: Context, value: Float) {
        touchpadDimLevel = value
        persist(context) { putFloat("touchpad_dim_level", value) }
    }

    fun setTouchpadScrollInverted(context: Context, inverted: Boolean) {
        touchpadScrollInverted = inverted
        persist(context) { putBoolean("tp_scroll_invert", inverted) }
    }

    fun setTouchpadScrollSpeed(context: Context, value: Float) {
        touchpadScrollSpeed = value
        persist(context) { putFloat(PREF_SCROLL_SPEED_SCALE, value) }
    }

    fun setTouchpadScrollStepDp(context: Context, value: Float) {
        touchpadScrollStepDp = value
        persist(context) { putFloat("tp_scroll_step_dp", value) }
    }

    fun setTouchpadDirectScrollGestureEnabled(context: Context, enabled: Boolean) {
        touchpadDirectScrollGestureEnabled = enabled
        persist(context) { putBoolean("tp_scroll_direct_gesture", enabled) }
    }

    fun setTouchpadDirectScrollGain(context: Context, value: Float) {
        touchpadDirectScrollGain = value
        persist(context) { putFloat("tp_scroll_direct_gain", value) }
    }

    fun setTouchpadDirectScrollStepDp(context: Context, value: Float) {
        touchpadDirectScrollStepDp = value
        persist(context) { putFloat("tp_scroll_direct_step_dp", value) }
    }

    fun setTouchpadAutoFocusEnabled(context: Context, enabled: Boolean) {
        touchpadAutoFocusEnabled = enabled
        persist(context) { putBoolean("tp_auto_focus", enabled) }
    }

    fun setSwitchBarEnabled(context: Context, enabled: Boolean) {
        switchBarEnabled = enabled
        persist(context) { putBoolean("switch_bar_enabled", enabled) }
        ControlAccessibilityService.requestSwitchBarRefresh()
    }

    fun setSwitchBarScale(context: Context, value: Float) {
        switchBarScale = value
        persist(context) { putFloat("switch_bar_scale", value) }
        ControlAccessibilityService.requestSwitchBarRefresh()
    }

    fun setTouchpadAutoLockEnabled(context: Context, enabled: Boolean) {
        touchpadAutoLockEnabled = enabled
        persist(context) { putBoolean("tp_auto_lock_enabled", enabled) }
    }

    fun setTouchpadAutoLockTimeout(context: Context, valueMs: Long) {
        touchpadAutoLockTimeoutMs = valueMs
        persist(context) { putLong("tp_auto_lock_timeout", valueMs) }
    }

    fun setDPadPosition(context: Context, value: Int) {
        dPadPosition = value
        persist(context) { putInt("dpad_position", value) }
    }

    fun setTouchpadSmoothing(context: Context, value: Float) {
        persist(context) { putFloat("tp_smoothing", value) }
    }

    fun setTouchpadDragBoost(context: Context, value: Float) {
        persist(context) { putFloat("tp_drag_boost", value) }
    }

    fun setAppLanguage(context: Context, languageTag: String) {
        appLanguageTag = languageTag
        persist(context) { putString(PREF_APP_LANGUAGE, languageTag) }
        applyAppLanguage()
    }

    fun applyAppLanguage() {
        val locales = if (appLanguageTag == LANGUAGE_SYSTEM) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(appLanguageTag)
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
    }

    fun getCustomThemeColors(context: Context): ThemeColors {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("custom_theme_json", null)
        return if (json != null) ThemeColors.fromJson(json) else ThemeEngine.getPreset(THEME_DARK)
    }
    
    fun setCustomThemeColors(context: Context, colors: ThemeColors) {
        persist(context) { putString("custom_theme_json", colors.toJson()) }
    }

    fun setHudEnabled(context: Context, enabled: Boolean) {
        hudEnabled = enabled
        persist(context) { putBoolean("hud_enabled", enabled) }
        syncHudFlows()
    }

    fun setDeveloperModeUnlocked(context: Context, unlocked: Boolean) {
        developerModeUnlocked = unlocked
        persist(context) { putBoolean("developer_unlocked", unlocked) }
        syncHudFlows()
    }

    fun setHudMode(context: Context, mode: Int) {
        hudMode = mode
        persist(context) { putInt("hud_mode", mode) }
        syncHudFlows()
    }

    fun setHudPosition(context: Context, position: Int) {
        hudPosition = position
        persist(context) { putInt("hud_position", position) }
        syncHudFlows()
    }

    fun setHudSize(context: Context, sizeDp: Float) {
        // Snap to 5dp steps to match Slider configuration and prevent crashes
        val snapped = kotlin.math.round(sizeDp / 5f) * 5f
        hudSizeDp = snapped.coerceIn(60f, 120f)
        persist(context) { putFloat("hud_size_dp", hudSizeDp) }
        syncHudFlows()
    }

    fun setHudActivationZone(context: Context, zoneDp: Float) {
        hudActivationZoneDp = zoneDp
        persist(context) { putFloat("hud_activation_zone", zoneDp) }
        _hudActivationZoneFlow.value = zoneDp
    }

    fun setHudHideDelay(context: Context, delayMs: Long) {
        // Snap to 500ms steps to match Slider configuration and prevent crashes
        val snapped = kotlin.math.round(delayMs / 500.0).toLong() * 500
        hudHideDelayMs = snapped.coerceIn(1000, 10000)
        persist(context) { putLong("hud_hide_delay", hudHideDelayMs) }
    }

    fun setAppNotificationsEnabled(context: Context, enabled: Boolean) {
        appNotificationsEnabled = enabled
        persist(context) { putBoolean("app_notifications_enabled", enabled) }
    }

    fun setAppNotificationDuration(context: Context, duration: Int) {
        appNotificationDuration = duration
        persist(context) { putInt("app_notification_duration", duration) }
    }

    fun setHudNotificationsEnabled(context: Context, enabled: Boolean) {
        // Global setter: Persistent
        persist(context) { putBoolean("hud_notifications_enabled", enabled) }
        
        // Update the effective state immediately
        hudNotificationsEnabled = enabled
        
        if (isSessionCaptured) {
            // Update baseline AND clear override since user intentionally changed global setting
            originalHudNotificationsState = enabled
            temporaryOverrideActive = false
        }
        
        syncHudFlows()
    }

    fun initializeNotificationSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val globalState = prefs.getBoolean("hud_notifications_enabled", true)
        
        hudNotificationsEnabled = globalState
        originalHudNotificationsState = globalState
        isSessionCaptured = true
        temporaryOverrideActive = false
        
        android.util.Log.d("SettingsStore", "initializeNotificationSession: globalState=$globalState")
        syncHudFlows()
    }



    fun toggleTemporaryHudNotifications() {
        // Session setter: Memory-only
        val newState = !hudNotificationsEnabled
        hudNotificationsEnabled = newState
        temporaryOverrideActive = (newState != originalHudNotificationsState)
        
        android.util.Log.d("SettingsStore", "toggleTemporaryHudNotifications: newState=$newState tempOverrideActive=$temporaryOverrideActive")
        syncHudFlows()
    }

    fun restoreOriginalHudNotificationState() {
        // Session restorer: Memory-only
        android.util.Log.d("SettingsStore", "restoreOriginalHudNotificationState CALLED: sessionCaptured=$isSessionCaptured tempActive=$temporaryOverrideActive")
        
        if (!isSessionCaptured) return
        
        hudNotificationsEnabled = originalHudNotificationsState
        temporaryOverrideActive = false
        isSessionCaptured = false
        
        android.util.Log.d("SettingsStore", "restoreOriginalHudNotificationState EXECUTED: restored to $hudNotificationsEnabled")
        syncHudFlows()
    }

    fun setHudDebugAlwaysShow(context: Context, enabled: Boolean) {
        hudDebugAlwaysShow = enabled
        persist(context) { putBoolean("hud_debug_always_show", enabled) }
        _hudDebugAlwaysShowFlow.value = enabled
    }

    fun setHudDebugHighlightZone(context: Context, enabled: Boolean) {
        hudDebugHighlightZone = enabled
        persist(context) { putBoolean("hud_debug_highlight_zone", enabled) }
        _hudDebugHighlightZoneFlow.value = enabled
    }

    fun setHudDebugShowBounds(context: Context, enabled: Boolean) {
        hudDebugShowBounds = enabled
        persist(context) { putBoolean("hud_debug_show_bounds", enabled) }
        _hudDebugShowBoundsFlow.value = enabled
    }

    fun resetHUDSettings(context: Context) {
        hudEnabled = false
        hudMode = HUD_MODE_FULL_INFO
        hudPosition = HUD_POS_TOP
        hudSizeDp = 80f
        hudActivationZoneDp = 20f
        hudHideDelayMs = 3000L
        hudStatusPanelEnabled = true
        hudNotificationSizeDp = 240f
        hudNotificationsEnabled = true
        
        developerMode = false
        hudDebugAlwaysShow = false
        hudDebugHighlightZone = false
        hudDebugShowBounds = false

        persist(context) {
            putBoolean("hud_enabled", false)
            putInt("hud_mode", HUD_MODE_FULL_INFO)
            putInt("hud_position", HUD_POS_TOP)
            putFloat("hud_size_dp", 80f)
            putFloat("hud_activation_zone", 20f)
            putLong("hud_hide_delay", 3000L)
            putBoolean("hud_status_panel_enabled", true)
            putFloat("hud_notification_size_dp", 240f)
            putBoolean("hud_notifications_enabled", true)
            
            putBoolean("developer_mode", false)
            putBoolean("hud_debug_always_show", false)
            putBoolean("hud_debug_highlight_zone", false)
            putBoolean("hud_debug_show_bounds", false)
        }
        syncHudFlows()
    }

    fun setTouchpadIntroShown(context: Context) {
        touchpadIntroShown = true
        persist(context) { putBoolean("touchpad_intro_shown", true) }
    }

    private fun syncHudFlows() {
        android.util.Log.d("SettingsStore", "syncHudFlows: hudEnabled=$hudEnabled hudNotifsEnabled=$hudNotificationsEnabled")
        if (_hudEnabledFlow.value != hudEnabled) _hudEnabledFlow.value = hudEnabled
        if (_developerModeUnlockedFlow.value != developerModeUnlocked) _developerModeUnlockedFlow.value = developerModeUnlocked
        if (_hudModeFlow.value != hudMode) _hudModeFlow.value = hudMode
        if (_hudPositionFlow.value != hudPosition) _hudPositionFlow.value = hudPosition
        if (_hudSizeFlow.value != hudSizeDp) _hudSizeFlow.value = hudSizeDp
        if (_hudActivationZoneFlow.value != hudActivationZoneDp) _hudActivationZoneFlow.value = hudActivationZoneDp
        if (_hudDebugAlwaysShowFlow.value != hudDebugAlwaysShow) _hudDebugAlwaysShowFlow.value = hudDebugAlwaysShow
        if (_hudDebugHighlightZoneFlow.value != hudDebugHighlightZone) _hudDebugHighlightZoneFlow.value = hudDebugHighlightZone
        if (_hudDebugShowBoundsFlow.value != hudDebugShowBounds) _hudDebugShowBoundsFlow.value = hudDebugShowBounds
    }

    private fun persist(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply(block).apply()
    }
}
