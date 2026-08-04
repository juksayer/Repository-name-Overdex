# Walkthrough - Git #278: Calibration Fallback Enforcement

This commit ensures that the instrument utilizes functional fallback regions by ignoring any legacy "zero-width" calibration data in the device's SharedPreferences.

## Changes

### Data Layer
- **[CalibrationManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/CalibrationManager.kt)**:
    - Refactored the `load()` method to perform validation on retrieved region widths.
    - If a region's width is `0f` (indicating an uninitialized or legacy empty save), the manager now explicitly enforces the hardcoded normalized fallbacks.
    - This breaks the deadlock where legacy zero-value preferences were preventing the new "factory defaults" from taking effect.

## Verification Results

### Automated Verification
- **Gradle Build**: Successful. The project compiles with the updated validation logic.

### Manual Verification Path
When deployed, `Logcat` should now immediately transition from:
```
D/COUNTDOWN: Calibration: false
```
to:
```
D/COUNTDOWN: Calibration: true
```
This confirms that `BattleCalibration.isCalibrated()` is returning true because the `enemyNameRegion` has been correctly populated with a non-zero fallback width, even if the user has never manually saved a calibration.
