package com.xrdesk

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Central monitor for system state and events used by the HUD.
 * Exposes data via modern reactive Kotlin Flows.
 */
object HUDSystemMonitor {

    data class BatteryInfo(
        val level: Int, 
        val isCharging: Boolean, 
        val chargeTimeRemaining: Long = -1,
        val dischargeTimeRemaining: Long = -1,
    )
    data class ConnectivityInfo(
        val wifiEnabled: Boolean, 
        val wifiFrequency: Int = 0,
        val wifiStandard: Int = 0, // WIFI_STANDARD_ enum
        val wifiLinkSpeed: Int = 0, // Mbps
        val wifiSsid: String? = null,
        val bluetoothEnabled: Boolean,
        val bluetoothDeviceName: String? = null,
        val bluetoothDeviceCount: Int = 0,
        val bluetoothDeviceBattery: Int = -1, // Percent
        val airplaneMode: Boolean = false
    )
    data class MobileInfo(val operatorName: String?, val networkType: String?)
    data class CursorEvent(val x: Float, val y: Float)

    private val _batteryState = MutableStateFlow(BatteryInfo(level = 0, isCharging = false, chargeTimeRemaining = -1, dischargeTimeRemaining = -1))
    val batteryState = _batteryState.asStateFlow()

    private val _connectivityState = MutableStateFlow(ConnectivityInfo(false, 0, 0, 0, null, false))
    val connectivityState = _connectivityState.asStateFlow()

    private val _mobileState = MutableStateFlow(MobileInfo(null, null))
    val mobileState = _mobileState.asStateFlow()

    private val _timeState = MutableStateFlow(System.currentTimeMillis())
    val timeState = _timeState.asStateFlow()

    private val _cursorEvents = MutableSharedFlow<CursorEvent>(extraBufferCapacity = 1)
    val cursorEvents = _cursorEvents.asSharedFlow()

    private val isStarted = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    private val timeTicker = object : Runnable {
        override fun run() {
            _timeState.value = System.currentTimeMillis()
            handler.postDelayed(this, 1000L * 30) // Every 30 seconds is enough for HUD clock
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            appContext?.let { updateConnectivity(it) }
        }
        override fun onLost(network: android.net.Network) {
            appContext?.let { updateConnectivity(it) }
        }
        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
            appContext?.let { updateConnectivity(it) }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                BluetoothAdapter.ACTION_STATE_CHANGED -> updateConnectivity(context)
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> updateConnectivity(context)
            }
        }
    }

    fun start(context: Context) {
        if (!isStarted.compareAndSet(false, true)) return
        
        val app = context.applicationContext
        appContext = app

        // Initial updates
        updateConnectivity(app)
        updateMobile(app)
        updateTime()

        // Register receivers
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        app.registerReceiver(systemReceiver, filter)

        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(networkCallback)

        handler.post(timeTicker)
    }

    fun stop() {
        if (!isStarted.compareAndSet(true, false)) return
        appContext?.unregisterReceiver(systemReceiver)
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.unregisterNetworkCallback(networkCallback)
        handler.removeCallbacks(timeTicker)
        appContext = null
    }

    fun publishCursor(x: Float, y: Float) {
        _cursorEvents.tryEmit(CursorEvent(x, y))
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || 
                        status == BatteryManager.BATTERY_STATUS_FULL)
        
        var chargeTime = -1L
        var dischargeTime = -1L
        
        val bm = appContext?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (bm != null) {
            if (isCharging) {
                chargeTime = bm.computeChargeTimeRemaining()
            } else {
                // Discharge estimation: remaining_uAh / current_now_uA
                val currentNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) // uA
                val chargeCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // uAh
                
                // On some devices currentNow is positive even during discharge.
                // We assume if not charging, any current draw is discharge.
                if (currentNow != Long.MIN_VALUE && chargeCounter != Long.MIN_VALUE) {
                    val absCurrent = abs(currentNow.toDouble())
                    if (absCurrent > 500) { // At least 0.5mA draw
                        val hoursRemaining = chargeCounter.toDouble() / absCurrent
                        dischargeTime = (hoursRemaining * 3600 * 1000).toLong()
                    }
                }
            }
        }

        if (level != -1 && scale != -1) {
            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            _batteryState.value = BatteryInfo(batteryPct, isCharging, chargeTime, dischargeTime)
        }
    }

    private fun updateConnectivity(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val wifiEnabled = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        
        var wifiFreq = 0
        var wifiSpeed = 0
        var wifiSsid: String? = null
        var wifiStandard = 0
        if (wifiEnabled) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val info = wm.connectionInfo
                wifiFreq = info.frequency
                wifiSpeed = info.linkSpeed
                
                if (Build.VERSION.SDK_INT >= 30) {
                    // Map hardware standards to readable labels
                    wifiStandard = when (info.wifiStandard) {
                        ScanResult.WIFI_STANDARD_11AX -> 6
                        ScanResult.WIFI_STANDARD_11AC -> 5
                        ScanResult.WIFI_STANDARD_11N -> 4
                        else -> info.wifiStandard
                    }
                }
                
                // Location permission required for SSID on modern Android
                val hasLocation = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasLocation) {
                    wifiSsid = info.ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" }
                }
            } catch (e: Exception) {
                // Fallback for SecurityException or other Wi-Fi errors
            }
            
            // RC3 Fix: If SSID is null but Wi-Fi is active, show "Wi-Fi Connected" instead of fallthrough
            if (wifiSsid == null || wifiSsid == "Wi-Fi Connected") {
                wifiSsid = if (context.resources.configuration.locales[0].language == "ru") "Wi-Fi Подключено" else "Wi-Fi Connected"
            }
        }

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val btAdapter = bm.adapter
        val btEnabled = btAdapter?.isEnabled ?: false
        
        var primaryDeviceName: String? = null
        var connectedCount = 0
        var btBattery = -1

        val airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

        if (btEnabled) {
            try {
                val hasConnectPermission = if (Build.VERSION.SDK_INT >= 31) {
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else true

                if (hasConnectPermission) {
                    // Track truly connected devices across common profiles
                    val a2dp = bm.getConnectedDevices(BluetoothProfile.A2DP)
                    val hfp = bm.getConnectedDevices(BluetoothProfile.HEADSET)
                    
                    val allConnected = (a2dp + hfp).distinctBy { it.address }
                    connectedCount = allConnected.size
                    primaryDeviceName = allConnected.firstOrNull()?.name
                    
                    // Note: Real battery level often requires profile-specific intents or metadata
                }
            } catch (ignored: Exception) {
                // Fallback for security or hardware issues
            }
        }

        _connectivityState.value = ConnectivityInfo(
            wifiEnabled = wifiEnabled,
            wifiFrequency = wifiFreq,
            wifiStandard = wifiStandard,
            wifiLinkSpeed = wifiSpeed,
            wifiSsid = wifiSsid,
            bluetoothEnabled = btEnabled,
            bluetoothDeviceName = primaryDeviceName,
            bluetoothDeviceCount = connectedCount,
            bluetoothDeviceBattery = btBattery,
            airplaneMode = airplaneMode
        )
        updateMobile(context)
    }

    private fun updateMobile(context: Context) {
        try {
            // Check for permission before accessing TelephonyManager
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                _mobileState.value = MobileInfo(null, null)
                return
            }

            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val operator = tm.networkOperatorName
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
            val type = when (networkType) {
                android.telephony.TelephonyManager.NETWORK_TYPE_NR -> "5G"
                android.telephony.TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                android.telephony.TelephonyManager.NETWORK_TYPE_HSPA, android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP -> "H+"
                else -> null
            }
            _mobileState.value = MobileInfo(operator, type)
        } catch (e: Exception) {
            _mobileState.value = MobileInfo(null, null)
        }
    }

    private fun updateTime() {
        _timeState.value = System.currentTimeMillis()
    }
}
