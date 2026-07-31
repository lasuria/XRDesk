# Implementation Plan - Fix Hardware Volume Buttons in XR Video Player

This plan addresses the bug where hardware volume buttons are non-functional only when the video player is running on an external XR display.

## Problem Analysis

The investigation pinpointed that `BrowserActivity.kt` explicitly intercepts volume key events when the XR player is active:

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (isXrModeActive && isPlayerActiveInXr) {
        val p = MediaSessionManager.getPlayer(this)
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { p.increaseDeviceVolume(); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { p.decreaseDeviceVolume(); return true }
        }
    }
    return super.onKeyDown(keyCode, event)
}
```

By returning `true`, the activity consumes these events, which prevents the Android system from:
1. Displaying the native volume slider UI.
2. Handling the volume change at the OS level (which is more reliable for external audio routing).

In contrast, `PlayerActivity.kt` (the normal player) does not have this override, allowing the system to handle volume keys naturally, which explains why it works correctly.

## Proposed Changes

### [Component] Browser Activity (XR Player Host)

#### [MODIFY] [BrowserActivity.kt](file:///C:/Users/lasur/StudioProjects/XRDesk/app/src/main/java/com/xrdesk/BrowserActivity.kt)
1. **Enable Native Routing**: Add `setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC)` to `onCreate`. This ensures that even if the activity is in a complex state (XR mode), the hardware buttons are dedicated to media volume.
2. **Remove Interception**: Delete the volume key handling in `onKeyDown`. By allowing these keys to pass to `super.onKeyDown()`, the system will trigger the native volume UI and adjust the audio stream correctly.

### [Component] Player Activity (Normal Player)

#### [MODIFY] [PlayerActivity.kt](file:///C:/Users/lasur/StudioProjects/XRDesk/app/src/main/java/com/xrdesk/PlayerActivity.kt)
- **Best Practice**: Add `setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC)` to `onCreate` to ensure consistent behavior, even though it currently works.

## User Review Required

> [!IMPORTANT]
> The fix relies on delegating volume control back to the Android OS. This is the standard way to ensure the volume UI appears on the phone while audio is playing (either on phone or via glasses). The manual `p.increaseDeviceVolume()` calls are unnecessary because the player is already configured with `USAGE_MEDIA` audio attributes in `MediaSessionManager`.

## Verification Plan

### Manual Verification
1. **Normal Player**: Verify Volume Up/Down works in `PlayerActivity` on phone.
2. **XR Player**:
   - Launch XR Mode in `BrowserActivity`.
   - Start a video in the glasses.
   - Press Volume Up/Down on the phone.
   - **Expected**: System volume UI appears on the phone, and audio volume in the glasses changes.
3. **Regression Check**: Ensure D-Pad and other hardware keys still work in both activities.
