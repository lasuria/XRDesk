package com.xrdesk

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.Display

/**
 * Detection priority:
 * 1. USB device detection (highest priority)
 * 2. DisplayManager
 * 3. Fallback
 */
object XrDeviceDetector {

    private const val TAG = "XRDetector"

    enum class ExternalDisplayType {
        XR_GLASSES,
        MONITOR,
        NONE
    }

    // Known XR Hardware Database
    private val KNOWN_XR_VENDORS = setOf(
        0x3318, // XREAL / Nreal
        0x35CA  // VITURE
    )

    private val KNOWN_XR_PRODUCTS = setOf(
        Pair(0x3318, 0x0424), // XREAL Air
        Pair(0x3318, 0x0428), // XREAL Air 2
        Pair(0x3318, 0x0426), // XREAL Air 2 Ultra
        Pair(0x3318, 0x0432), // XREAL Air 2 Pro
        Pair(0x35CA, 0x1011), // VITURE One
        Pair(0x35CA, 0x1013), // VITURE Lite
        Pair(0x35CA, 0x1019), // VITURE Pro
        Pair(0x35CA, 0x1010)  // VITURE Bootloader
    )

    /**
     * Determines the type of connected external display.
     */
    fun getExternalDisplayType(context: Context): ExternalDisplayType {
        Log.d(TAG, "XRDetector: Starting detection...")

        // 1. USB Device Detection (Priority 1)
        if (detectXrViaUsb(context)) {
            Log.i(TAG, "XR detected via USB")
            return ExternalDisplayType.XR_GLASSES
        }

        // 2. DisplayManager Detection (Priority 2)
        if (detectExternalDisplay(context)) {
            Log.i(TAG, "External display detected. Treat as MONITOR")
            return ExternalDisplayType.MONITOR
        }

        Log.d(TAG, "No external display connected")
        return ExternalDisplayType.NONE
    }

    fun isXrGlassesConnected(context: Context): Boolean {
        return getExternalDisplayType(context) == ExternalDisplayType.XR_GLASSES
    }

    fun isExternalMonitorConnected(context: Context): Boolean {
        return getExternalDisplayType(context) == ExternalDisplayType.MONITOR
    }

    private fun detectXrViaUsb(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val deviceList = usbManager.deviceList

        if (deviceList.isEmpty()) {
            Log.d(TAG, "USB: No devices found")
            return false
        }

        for (device in deviceList.values) {
            val vid = device.vendorId
            val pid = device.productId
            val manufacturer = device.manufacturerName ?: "Unknown"
            val product = device.productName ?: "Unknown"

            Log.d(TAG, "USB Device found: Vendor=0x${Integer.toHexString(vid).uppercase()}, " +
                    "Product=0x${Integer.toHexString(pid).uppercase()}, " +
                    "Manufacturer=$manufacturer, Product=$product")

            // Check if VID is in known vendors OR if specific VID/PID pair is known
            if (KNOWN_XR_VENDORS.contains(vid) || KNOWN_XR_PRODUCTS.contains(Pair(vid, pid))) {
                Log.i(TAG, "Matched known XR hardware: $manufacturer $product")
                return true
            }
        }
        return false
    }

    private fun detectExternalDisplay(context: Context): Boolean {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
        val displays = displayManager.getDisplays()
        
        // Return true if there is any display other than the default one
        return displays.any { it.displayId != Display.DEFAULT_DISPLAY }
    }
}
