[1mdiff --git a/README.md b/README.md[m
[1mindex f3729a2..41a833a 100644[m
[1m--- a/README.md[m
[1m+++ b/README.md[m
[36m@@ -1,81 +1,115 @@[m
[31m-# DeskControl[m
[32m+[m[32m<div align="center">[m
 [m
[31m-DeskControl turns your phone into a touchpad and keyboard for a single app[m
[31m-running on a wired external display. It targets Android 11+ and uses an[m
[31m-AccessibilityService to render the cursor and inject input.[m
[32m+[m[32m# XRDesk[m
 [m
[31m-[中文说明](README_zh.md)[m
[32m+[m[32m### Modern Android Remote & Touchpad for External Displays and XR Glasses[m
 [m
[31m-## Highlights[m
[32m+[m[32mTurn your Android device into a touchpad and remote control for applications running on an external display.[m
 [m
[31m-- Launch any installed app onto a wired external display.[m
[31m-- Control the external app with a phone touchpad (move, click, drag).[m
[31m-- Per-display cursor overlay with auto-hide and tuning controls.[m
[31m-- Clean teardown when the external display disconnects.[m
[32m+[m[32m![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)[m
[32m+[m[32m![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)[m
[32m+[m[32m![License](https://img.shields.io/badge/License-GPL--3.0-blue)[m
 [m
[31m-## Requirements[m
[32m+[m[32m</div>[m
 [m
[31m-- Android 11+ (minSdk 30).[m
[31m-- Wired Type-C external display.[m
[31m-- Accessibility service enabled (required for cursor and input injection).[m
[32m+[m[32m---[m
 [m
[31m-## Quick Start[m
[32m+[m[32m## ✨ Features[m
 [m
[31m-1. Connect the wired external display.[m
[31m-2. Launch DeskControl and enable the accessibility service if prompted.[m
[31m-3. Pick an app to launch on the external display.[m
[31m-4. Open Touchpad and control the external app.[m
[32m+[m[32m- 🎮 Touchpad mode[m
[32m+[m[32m- 🕹️ Modern Remote Control interface[m
[32m+[m[32m- 🥽 XR glasses support[m
[32m+[m[32m- 🖥️ External monitor support[m
[32m+[m[32m- 🎨 Material Design 3 UI[m
[32m+[m[32m- 🌈 Material You[m
[32m+[m[32m- 🌙 AMOLED theme[m
[32m+[m[32m- ☀️ Light theme[m
[32m+[m[32m- 🌑 Dark theme[m
[32m+[m[32m- 🎨 Custom Theme Editor[m
[32m+[m[32m- ⚫ Monochrome mode[m
[32m+[m[32m- 💾 Theme import/export (JSON)[m
[32m+[m[32m- 🌍 Multi-language support[m
[32m+[m[32m- ▶️ Media controls[m
[32m+[m[32m- ⚡ Smooth and responsive interface[m
 [m
[31m-## Touchpad Usage[m
[32m+[m[32m---[m
 [m
[31m-- Move: slide one finger in the touchpad area.[m
[31m-- Click: tap once in the touchpad area.[m
[31m-- Slide & drag: double-tap, then slide (vibration confirms).[m
[31m-- Auto-dim: after 10s inside the touchpad area, the screen dims. It restores[m
[31m-  when you tap outside the touchpad area or leave the screen.[m
[31m-- Back: when the touchpad area is active, Back is forwarded to the external app.[m
[31m-- Exit: tap the top-left back arrow, or tap outside the touchpad area and then press Back.[m
[32m+[m[32m## 🥽 Supported Devices[m
 [m
[31m-## Build[m
[32m+[m[32m### XR Glasses[m
 [m
[31m-```bash[m
[31m-./gradlew assembleDebug[m
[31m-```[m
[32m+[m[32m- XREAL Air[m
[32m+[m[32m- XREAL Air 2[m
[32m+[m[32m- XREAL Air 2 Pro[m
[32m+[m[32m- XREAL Air 2 Ultra[m
[32m+[m[32m- VITURE One[m
[32m+[m[32m- VITURE Pro[m
[32m+[m
[32m+[m[32m### External Displays[m
[32m+[m
[32m+[m[32m- USB-C DisplayPort monitors[m
[32m+[m[32m- TVs[m
[32m+[m[32m- Portable displays[m
[32m+[m[32m- XR glasses[m
[32m+[m
[32m+[m[32m---[m
[32m+[m
[32m+[m[32m## 📋 Requirements[m
[32m+[m
[32m+[m[32m- Android 11 or newer (API 30+)[m
[32m+[m[32m- USB-C DisplayPort Alt Mode (for external displays)[m
[32m+[m
[32m+[m[32m---[m
 [m
[31m-Install the APK:[m
[32m+[m[32m## 🚀 Building[m
[32m+[m
[32m+[m[32mClone the repository:[m
 [m
 ```bash[m
[31m-adb install -r app/build/outputs/apk/debug/app-debug.apk[m
[32m+[m[32mgit clone https://github.com/lasuria/XRDesk.git[m
 ```[m
 [m
[31m-## Settings[m
[32m+[m[32mOpen the project in Android Studio and build it normally.[m
[32m+[m
[32m+[m[32m---[m
[32m+[m
[32m+[m[32m## 🔥 What's New Compared to DeskControl?[m
[32m+[m
[32m+[m[32m| Feature | DeskControl | XRDesk |[m
[32m+[m[32m|---------|:-----------:|:-------:|[m
[32m+[m[32m| Material Design 3 | ❌ | ✅ |[m
[32m+[m[32m| Material You | ❌ | ✅ |[m
[32m+[m[32m| AMOLED Theme | ❌ | ✅ |[m
[32m+[m[32m| Theme Editor | ❌ | ✅ |[m
[32m+[m[32m| Monochrome Mode | ❌ | ✅ |[m
[32m+[m[32m| Theme Import / Export | ❌ | ✅ |[m
[32m+[m[32m| Improved Remote UI | ❌ | ✅ |[m
[32m+[m[32m| Modern Settings UI | ❌ | ✅ |[m
[32m+[m
[32m+[m[32m---[m
[32m+[m
[32m+[m[32m## 🛠️ Technologies[m
[32m+[m
[32m+[m[32m- Kotlin[m
[32m+[m[32m- Android SDK[m
[32m+[m[32m- AndroidX[m
[32m+[m[32m- Material Design 3[m
[32m+[m[32m- ViewBinding[m
 [m
[31m-- Cursor size, opacity, color, and auto-hide delay.[m
[31m-- Touchpad sensitivity, acceleration, jitter, smoothing, and scroll step.[m
[31m-- Keep screen on while using touchpad.[m
[31m-- Auto-dim touchpad after 10 seconds (per-window brightness only).[m
[32m+[m[32m---[m
 [m
[31m-## Project Layout[m
[32m+[m[32m## 🤝 Credits[m
 [m
[31m-- `DisplaySessionManager`: external display tracking and selection.[m
[31m-- `AppLauncher`: launch routing and failure diagnostics.[m
[31m-- `TouchpadActivity`: touchpad UI and input logic.[m
[31m-- `ControlAccessibilityService`: cursor overlay and gesture/text injection.[m
[31m-- `CursorOverlayView`: cursor rendering and animation.[m
[31m-- `DiagnosticsActivity`: status and recent failure history.[m
[32m+[m[32mXRDesk is based on the open-source **DeskControl** project.[m
 [m
[31m-## Permissions and Notes[m
[32m+[m[32mThe original project provided the foundation, while XRDesk extends it with a redesigned interface, new customization options, XR-focused improvements, and additional functionality.[m
 [m
[31m-- Uses `AccessibilityService` for gesture injection and `ACTION_SET_TEXT`.[m
[31m-- Cursor overlay uses `TYPE_ACCESSIBILITY_OVERLAY` and is non-touchable.[m
[31m-- The overlay is attached to the external display via `createWindowContext`.[m
[32m+[m[32mSpecial thanks to the original DeskControl developers for making the project open source.[m
 [m
[31m-## Limitations[m
[32m+[m[32m---[m
 [m
[31m-- Android 11+ only.[m
[31m-- Requires device support for secondary-display activities.[m
[31m-- Some apps do not allow launch on a secondary display.[m
[32m+[m[32m## 📄 License[m
 [m
[31m-## License[m
[32m+[m[32mThis project is licensed under the **GNU General Public License v3.0**.[m
 [m
[31m-See `LICENSE`.[m
[32m+[m[32mSee the [LICENSE](LICENSE) file for details.[m
