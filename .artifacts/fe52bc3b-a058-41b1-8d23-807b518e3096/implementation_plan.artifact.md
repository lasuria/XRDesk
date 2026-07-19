# Implementation Plan - XR Device Detection

This plan implements a robust detection system for XR glasses (XREAL, VITURE, etc.) and regular external monitors using both USB device inspection and `DisplayManager`.

## Proposed Changes

### 1. Detection Core

#### [NEW] [XrDeviceDetector.kt](file:///C:/Users/lasur/StudioProjects/deskcontrol/app/src/main/java/com/deskcontrol/XrDeviceDetector.kt)
- **Singleton Pattern**: A clean Kotlin `object` for global access.
- **Enum `ExternalDisplayType`**: Defines `XR_GLASSES`, `MONITOR`, and `NONE`.
- **USB Detection**: Uses `UsbManager` to scan connected devices against a built-in database of Vendor IDs (VID) and Product IDs (PID).
    - **Database**:
        - XREAL/Nreal: VID `0x3318`
        - VITURE: VID `0x35CA`
        - Specific PIDs for Air, Air 2, One, Lite, Pro, etc.
- **Display Detection**: Fallback to `DisplayManager` to detect any other external display as a `MONITOR`.
- **Exposed API**:
    - `getExternalDisplayType(context: Context): ExternalDisplayType`
    - `isXrGlassesConnected(context: Context): Boolean`
    - `isExternalMonitorConnected(context: Context): Boolean`
- **Logging**: Detailed Logcat reports on USB discovery and final detection results.

### 2. Integration

#### [MODIFY] [MainActivity.kt](file:///C:/Users/lasur/StudioProjects/deskcontrol/app/src/main/java/com/deskcontrol/MainActivity.kt)
- Update `updateDisplayInfoUI()` to use `XrDeviceDetector` for determining the icon and status label.
- This replaces the keyword-based detection with a more accurate USB-level detection.

#### [MODIFY] [DisplaySessionManager.kt](file:///C:/Users/lasur/StudioProjects/deskcontrol/app/src/main/java/com/deskcontrol/DisplaySessionManager.kt)
- Optionally integrate detection logs or provide access to the detected type if needed for session management.

## Verification Plan

### Automated Tests
- Build verification: `./gradlew :app:assembleDebug`.

### Manual Verification
- **Logcat Audit**: Connect different devices and check the "XRDetector" tag for accurate identification.
- **UI Check**:
    - Verify `ic_xr_glasses` appears for XREAL/VITURE hardware.
    - Verify `ic_external_monitor` appears for standard HDMI/USB-C monitors.
    - Verify `ic_xr_display` remains the default for disconnected states.
