# Walkthrough - Match Calibration Shell Integration (LCD-Driven)

I have implemented a touch-sensitive "Trackpad Mode" for the Match Calibration workspace. This implementation respects the concept that the CRT is protected by a **GlassShield** and should not accept direct operator input.

## Changes Made

### Interaction Model Refinement

#### [PokedexComponents.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/PokedexComponents.kt)
- **LCD Touch Support**: Added `onDrag` and `onTap` callbacks to the `InstrumentLCD` component.
- **Gesture Detection**: Implemented `detectDragGestures` and `detectTapGestures` on the LCD surface to capture operator intent without touching the CRT.

#### [ODXFiShell.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt)
- **Input Pipeline**: Updated the shell to propagate touch events from the physical LCD area down to the active application workspace.
- **Long-Press Support**: Added `onSelectLong` to the **SELECT** button on the physical shell.

#### [MatchCalibrationScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt)
- **Trackpad Mapping**: Bound LCD drag deltas to the internal `move` and `resize` functions.
- **Cycle-via-Tap**: Tapping the LCD now cycles through active calibration regions, complementing the [A] button.
- **Reference Image Cycling**: Implemented dynamic asset discovery in `assets/battle_samples/`. Long-pressing the **[SELECT]** button now cycles through all available reference images.
- **Discrete vs. Fluid Input**: Preserved the physical D-pad for pixel-perfect adjustments while adding the LCD trackpad for fluid coordinate movement.

#### [MainActivity.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt)
- **Control Orchestration**: Updated the `match_calibration` route to bridge the shell's touch events and long-press actions to the calibration screen's input handlers.

## Verification Results

### Interaction Verification
- **GlassShield Invariant**: Verified that touch events on the CRT area are still consumed and ignored, preserving the physical barrier concept.
- **Trackpad Responsiveness**: Dragging on the LCD provides smooth, relative movement of the selected region on the CRT.
- **Input Concurrency**: Verified that D-pad, physical buttons, and LCD touch can be used simultaneously for calibration.
- **Asset Discovery**: Verified that all images in the `battle_samples` directory are accessible via the new cycling control.

> [!NOTE]
> The LCD now serves as the primary touch-interface for all protected-CRT workspaces, establishing a consistent interaction pattern for the ODX-Fi.

render_diffs(file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/PokedexComponents.kt)
render_diffs(file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt)
render_diffs(file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt)
render_diffs(file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt)
