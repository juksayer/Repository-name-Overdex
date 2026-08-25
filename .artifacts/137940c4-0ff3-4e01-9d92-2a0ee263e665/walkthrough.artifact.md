# Walkthrough - Match Calibration Shell Integration

I have corrected the presentation hierarchy of the Match Calibration workspace by removing the nested `ODXFiShell` from `MatchCalibrationScreen` and delegating physical control and LCD management to the shell provided by `MainActivity`.

## Changes Made

### UI Architecture Refinement

#### [MatchCalibrationScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt)
- **Removed Nested Shell**: The component no longer renders its own `ODXFiShell`.
- **Input Delegation**: Added registration lambdas for all physical controls (`onUp`, `onDown`, `onLeft`, `onRight`, `onA`, `onSelect`, `onStart`).
- **LCD Integration**: Added an `onLcdUpdate` callback to drive the shell's instrumentation display from within the screen logic.
- **State Preservation**: All calibration mechanics, including region cycling and coordinate transformation, remain owned by the screen component.

#### [MainActivity.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt)
- **Shell Ownership**: The shell provided in the `match_calibration` route now correctly captures physical inputs and passes them to the screen via the new registration pattern.
- **LCD Management**: The route now maintains state for `lcdLine1` and `lcdLine2`, which are updated by the screen and displayed on the outer shell.
- **Lifecycle Management**: Added a `DisposableEffect` to update the instrument's deployment state to `CALIBRATING` when entering the screen.

## Verification Results

### Integration Verification
- **Single Shell Pattern**: Verified that the component follows the exact ownership pattern used by the system calibration module.
- **Control Dispatch**: The D-pad and action buttons are correctly mapped to the internal calibration functions (move, resize, cycle region, toggle mode).
- **LCD Feedback**: The shell's LCD now correctly reflects the active calibration target and mode.

> [!NOTE]
> The visual presentation of the Match screenshot and overlays remains unchanged, ensuring full compatibility with existing calibration data.

render_diffs(file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt)
render_diffs(file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt)
