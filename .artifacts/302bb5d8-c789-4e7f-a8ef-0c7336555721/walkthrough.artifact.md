# Walkthrough - Default Parameters for Anchor Detectors

This task established a robust "factory default" system for the Overdex observation instrument. By providing sensible fallback coordinates for battle UI elements, the instrument now becomes functional immediately upon deployment, even before user-specific calibration.

## Changes

### Data & Calibration
- **[CalibrationManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/CalibrationManager.kt)**: Updated the `load()` method to use normalized fallback coordinates derived from real-world `battle_samples`. This ensures that `BattleCalibration` is populated with "usable" regions even when no preferences exist.
- **[BattleCalibration.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/BattleCalibration.kt)**: Refined the `isCalibrated()` check to rely on the presence of a valid `enemyNameRegion` width.

### Anchor Detection
- **[AnchorDetectorConfig.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/AnchorDetectorConfig.kt)**: Introduced a formal configuration model for anchor detection, moving away from hardcoded pixel/ratio assumptions.
- **[AnchorDetector.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/AnchorDetector.kt)**: Updated the interface to support configurable detection parameters.
- **[SimpleAnchorDetector.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/SimpleAnchorDetector.kt)**: Refactored to use `AnchorDetectorConfig`, making the detector's behavior tunable and consistent.

## Verification Results

### Automated Verification
- **Gradle Build**: Successfully compiled the project, ensuring all interface changes and usages in `ObservationRecognizer.kt` and `GuidedObservationPipeline.kt` were correctly handled via default parameters.

### Manual Review of Fallbacks
The coordinates were verified against the provided `battle_samples` (e.g., `celluloid-shot0008.jpg`) to ensure high-probability hits for the Enemy Name, HP Bar, and Countdown regions on standard 20:9 displays.
