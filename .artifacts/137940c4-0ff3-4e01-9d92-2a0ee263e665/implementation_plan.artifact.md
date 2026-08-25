# Implementation Plan - Match Calibration Shell Integration

Correct the presentation hierarchy of the `MatchCalibrationScreen` by removing its internal `ODXFiShell` and delegating shell control to the outer instance in `MainActivity`. This follows the established pattern used by `/system/calibration`.

## User Review Required

> [!IMPORTANT]
> `MatchCalibrationScreen` will no longer own its own `ODXFiShell`. It will instead provide its behavior to the shell provided by the navigation route in `MainActivity`.

## Proposed Changes

### UI Layer

#### [MODIFY] [MatchCalibrationScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt)
- **Remove** the internal `ODXFiShell` wrapper.
- **Add** registration parameters for physical controls, matching the pattern in `CalibrationScreen.kt`:
    - `onUp`, `onDown`, `onLeft`, `onRight`, `onA`, `onSelect`, `onStart` as `(() -> Unit) -> Unit`.
- **Add** `onLcdUpdate: (String?, String?) -> Unit` to delegate LCD text updates to the outer shell.
- **Input Handling**: Use `SideEffect` to register internal handlers with the provided registration lambdas.
- **LCD Integration**: Trigger `onLcdUpdate` whenever the `selectedRegion` or `mode` changes.
- **CRT Content**: Ensure the `Box` containing the reference image, aperture overlays, and workspace status information fills the available content area.
- **Preserve Mechanics**: Keep all existing calibration logic, `CalibrationManager` integration, and input-to-coordinate mapping.

#### [MODIFY] [MainActivity.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt)
- **Refactor** the `match_calibration` composable route:
    - Create `remember`ed state for LCD lines (`lcdLine1`, `lcdLine2`).
    - Create `remember`ed state for input handlers (up, down, left, right, a, select, start).
    - Pass these handlers and LCD lines into the `ODXFiShell`.
    - Pass the registration lambdas and `onLcdUpdate` into `MatchCalibrationScreen`.
    - Handle `onB` directly at the shell level to perform the `navController.popBackStack()`.

## Verification Plan

### Automated Tests
- N/A (UI layout change).

### Manual Verification
1. Navigate to **Researcher Mode** (UP, UP, DOWN, DOWN, LEFT, RIGHT, LEFT, RIGHT, B, A in settings).
2. Launch **Match Calibration**.
3. **Verify**: Only one `ODXFiShell` is visible.
4. **Verify**: Controls work as expected:
    - D-pad moves/resizes apertures.
    - [A] cycles regions.
    - [SELECT] toggles POSITION/SIZE.
    - [B] exits.
5. **Verify**: LCD display reflects the current region and mode.
6. **Verify**: Aperture overlays are visible on top of `celluloid-shot0001.jpg`.
