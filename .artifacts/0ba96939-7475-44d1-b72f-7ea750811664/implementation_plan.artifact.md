# Implementation Plan - Canonical Announcement Region

Implement the "Announcement Region" using the provided measured geometry (2460 x 1080 screen, 710-870 Y-range, 0-1080 X-range) in the existing region-definition mechanism.

## User Review Required

> [!IMPORTANT]
> The region-definition mechanism identified is `ObservationRegions` within `ObservationRegion.kt`. This object serves as the "permanent architectural descriptor" registry for the framework.

> [!NOTE]
> The "Announcement Region" overlaps physically with the `countdownRegion` defined in `BattleCalibration`. However, per instructions, no other regions will be modified.

## Proposed Changes

### Observation Layer

#### [MODIFY] [ObservationRegion.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/ObservationRegion.kt)

- Add `Announcement` to the `ObservationRegions` object.
- Use normalized coordinates derived from 2460 x 1080 geometry:
    - **X**: 0.0f
    - **Y**: 0.2886f (710 / 2460)
    - **Width**: 1.0f (1080 / 1080)
    - **Height**: 0.0650f (160 / 2460)
- Update `stateRegistry` to include the new `Announcement` region.

## Verification Plan

### Automated Tests
- Execute `./gradlew :app:compileDebugKotlin` to ensure the project still builds with the new region definition.

### Manual Verification
- Report the exact normalized values and the file location as requested.
- Document the conflict with `countdownRegion`.
