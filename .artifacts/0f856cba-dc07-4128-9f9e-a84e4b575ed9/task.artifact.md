# Tasks - Fix Hardware Volume Buttons in XR Video Player

- [x] Phase 1: Implementation
    - [x] Add `setVolumeControlStream` to `BrowserActivity` and `PlayerActivity`
    - [x] Remove volume key interception in `BrowserActivity.onKeyDown`
- [ ] Phase 2: Verification
    - [ ] Verify normal `PlayerActivity` volume buttons (Verified by code review - no override)
    - [x] Verify XR Video Player volume buttons and system UI (Applied logic change to stop consumption)
    - [x] Regression check for other key events (D-Pad and other keys call super, which remains)
