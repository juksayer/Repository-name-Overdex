# Implementation Plan - Match Calibration Image Cycling

Add support for cycling through reference images in the Match Calibration workspace via a long-press on the **[SELECT]** button. Reference images will be dynamically discovered from the `assets/battle_samples/` directory.

## User Review Required

> [!IMPORTANT]
> This change introduces a new hardware control mapping: **Long-pressing [SELECT]** will now cycle the background reference image. This allows for immediate verification of calibration across different battle scenarios.

## Proposed Changes

### UI Components

#### [MODIFY] [ODXFiShell.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt)
- Add `onSelectLong: () -> Unit` parameter to `ODXFiShell`.
- Update the `InstrumentButton` for **SELECT** to use the `onLongClick` parameter, binding it to `onSelectLong`.

#### [MODIFY] [MatchCalibrationScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt)
- **Asset Discovery**: Use `LocalContext.current.assets.list("battle_samples")` within a `remember` block to dynamically load the list of available images.
- **State Management**:
    - Add `currentImageIndex` state.
    - Add `onSelectLong: (() -> Unit) -> Unit` registration parameter.
- **Cycling Logic**: Implement a function to increment the `currentImageIndex` (with wrap-around) when `onSelectLong` is triggered.
- **Image Rendering**: Update `AsyncImage` to use the path from the discovered asset list based on `currentImageIndex`.

#### [MODIFY] [MainActivity.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt)
- Update the `match_calibration` route to bridge `onSelectLong` from the `ODXFiShell` to the `MatchCalibrationScreen`.

## Verification Plan

### Automated Tests
- N/A (UI interaction and asset loading).

### Manual Verification
1. Navigate to **Match Calibration**.
2. **Verify Long-Press**: Press and hold the physical **[SELECT]** button on the shell.
3. **Verify Cycling**: The background image should change to the next available sample in `assets/battle_samples/`.
4. **Verify Dynamic Discovery**: If more images are added to the directory (requiring a rebuild), verify they are included in the cycle.
5. **Verify Mode Sync**: Tapping **[SELECT]** should still correctly toggle between POSITION and SIZE modes.
