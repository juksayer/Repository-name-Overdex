# Implementation Plan - Brick 3: Attack Incoming Collector

Implement the `AttackIncomingCollector` to gather evidence of the "Attack Incoming!" phenomenon and deliver it to custody.

## User Review Required

> [!IMPORTANT]
> The Collector will perform **local cropping** using the provided absolute coordinates (X: 0-1080, Y: 710-870) for a 1080x2460 portrait display. No shared region or calibration infrastructure will be modified.

> [!NOTE]
> The Collector identifies itself as `SourceId("ATTACK_INCOMING_COLLECTOR")`. It uses the neutral `RawTestimony` payload approved in Brick 1.

## Proposed Changes

### Battle Layer - Collector

#### [NEW] [AttackIncomingCollector.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/collector/AttackIncomingCollector.kt)
- **Occupation**: Monitor visual input for the "Attack Incoming!" announcement.
- **Identity**: `SourceId("ATTACK_INCOMING_COLLECTOR")`.
- **Dependencies**: `ObservationInput` (for frames), `TestimonyCustody` (for preservation), and a `CoroutineScope`.
- **Operating State**:
    - `start()`: Submit `SourceAvailabilityRecord(true)`.
    - `stop()`: Submit `SourceAvailabilityRecord(false)`.
- **Collection**:
    - Use `input.supply { bitmap -> ... }`.
    - For each frame:
        - Submit `SourceInputRecord(true)`.
        - Perform local crop: `Rect(0, 710, 1080, 870)`.
        - Use ML Kit `TextRecognition` to detect text in the crop.
        - If "ATTACK INCOMING!" is detected: Submit `TestimonyRecord` with `RawTestimony("ATTACK_INCOMING")`.
        - If not detected: Produce no testimony. Failure to detect is not testimony of absence.
- **Neutrality**: No interpretation of Charge Moves, energy, or strategy.

## Verification Plan

### Automated Tests
- **[NEW] [AttackIncomingCollectorTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/battle/collector/AttackIncomingCollectorTest.kt)**:
    - Verify that the Collector reports its availability on `start()`/`stop()`.
    - Verify that it reports input availability when a bitmap is supplied.
    - Mock `TextRecognition` (or provide a bitmap with the text) to verify that testimony is produced and reaches `TestimonyCustody` with the correct ID and neutral payload.
    - Confirm that no downstream conclusions are present in the produced testimony.

### Manual Verification
- None required for this brick.
