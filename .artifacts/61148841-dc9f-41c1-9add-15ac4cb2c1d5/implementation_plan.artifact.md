# Implementation Plan - Spatial Extraction Boundary

Establish `ObservationExtractor` as the single authoritative spatial extraction boundary (the "Sally Port") for the visual recognition pipeline.

## Goal
Consolidate generic bitmap-cropping and coordinate-conversion responsibility into `ObservationExtractor`. This establishes a single point where raw pixels are transformed into institutional `CaptureObservation` artifacts.

## Proposed Changes

### [Component: Spatial Boundary]

#### [MODIFY] [ObservationExtractor.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/ObservationExtractor.kt)
- Introduce a generic `extractRegion` method:
    ```kotlin
    fun extractRegion(
        source: Bitmap,
        regionId: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): CaptureObservation
    ```
- Implement the defensive cropping logic:
    - Convert normalized coordinates to pixel coordinates.
    - Perform bounds checking against the source bitmap.
    - Execute `Bitmap.createBitmap`.
    - Provide fallback behavior (e.g., returning a 1x1 placeholder) to ensure a valid `CaptureObservation` is always returned.
- Preserve existing unused methods and models (`model.Observation`, `ObservationRegionState`).

### [Component: Guided Pipeline]

#### [MODIFY] [GuidedObservationPipeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/observation/GuidedObservationPipeline.kt)
- Update the internal `crop` method to delegate the actual pixel extraction to `ObservationExtractor.extractRegion`.
- **Logic Split**:
    - `GuidedObservationPipeline` continues to own **Anchor Refinement** (calculating the adjusted coordinates for Moves).
    - `ObservationExtractor` owns the **Spatial Extraction** (turning those coordinates into a bitmap).
- This proves the boundary while respecting that anchor refinement is phenomenon-specific knowledge.

### [Component: Witnesses]

#### [MODIFY] [GoodEffortWitness.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/witness/GoodEffortWitness.kt)
- Replace internal cropping logic with a call to `ObservationExtractor.extractRegion`.
- Use the `calibration.goodEffortRegion` parameters.

---

## Verification Plan

### Automated Tests
- **Run `ObservationEngineValidator`**: This is the primary verification tool. It passes real bitmaps through the `GuidedObservationPipeline`. Success proves that the extraction boundary is functional and equivalent to the previous internal implementation.

### Manual Verification
- **Logcat Trace**: Verify that `ObservationExtractor` is called and that `CaptureObservation` objects are successfully passed to `ObservationRecognizer`.
- **Recognition Integrity**: Confirm that `GoodEffortRecognizer` continues to produce high-confidence results during live matches.
