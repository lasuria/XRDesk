# Fix Conflicting Overloads in ControlAccessibilityService

The build is failing because a large block of code (approximately 394 lines) was accidentally duplicated at the end of `ControlAccessibilityService.kt`. This duplication includes several functions like `dumpAllWindowsDebug`, `injectNativeKeyPoC`, `navigateFocus`, etc., which leads to "Conflicting overloads" errors.

## Proposed Changes

### [ControlAccessibilityService.kt](file:///C:/Users/lasur/StudioProjects/deskcontrol/app/src/main/java/com/deskcontrol/ControlAccessibilityService.kt)

#### [MODIFY] [ControlAccessibilityService.kt](file:///C:/Users/lasur/StudioProjects/deskcontrol/app/src/main/java/com/deskcontrol/ControlAccessibilityService.kt)
- Remove the redundant block of functions starting from the second occurrence of `dumpAllWindowsDebug()` at line 2041 until line 2434.
- Ensure the class-closing brace at line 2435 is preserved.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify that the conflicting overloads error is resolved and the project builds successfully.

### Manual Verification
- None required beyond successful compilation for this specific fix.
