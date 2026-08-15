# Walkthrough - Brick 3: Attack Incoming Collector

Completed the implementation and verification of the first real Collector in Overdex. The `AttackIncomingCollector` gathering evidence of the "Attack Incoming!" phenomenon and delivers it as neutral testimony to the custody ledger.

## Changes Made

### Battle Layer - Collector Component

#### [AttackIncomingCollector.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/collector/AttackIncomingCollector.kt)
- **Occupation**: Monitors visual input for the "Attack Incoming!" announcement.
- **Identity**: `SourceId("ATTACK_INCOMING_COLLECTOR")`.
- **Local Evidence Collection**: Performs internal cropping of the incoming `Bitmap` using the canonical Announcement Region (0, 710, 1080, 160).
- **Phenomenon Detection**: Uses ML Kit `TextRecognition` to identify the announcement string. It is resilient to OCR artifacts (e.g., missing spaces).
- **Neutral Testimony**: Submits `RawTestimony("ATTACK_INCOMING")` to custody without any strategic inference.
- **State Reporting**: Correctly signals its online/offline status and input availability to the "Bagman."

## Verification Results

### Automated Tests
- **[AttackIncomingCollectorTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/battle/collector/AttackIncomingCollectorTest.kt)** (JVM):
    - Verified the architectural contract (availability, input reporting) using fakes.
- **[AttackIncomingCollectorAndroidTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/androidTest/java/com/example/overdex/battle/collector/AttackIncomingCollectorAndroidTest.kt)** (Android):
    - **Deployment Testing**: Executed on Moondrop MIAD01.
    - **Real Visual Path**: Verified that the Collector identifies "ATTACK INCOMING!" from a real `Bitmap` buffer using real ML Kit OCR.
    - **Architectural Silence**: Confirmed that the Collector remains silent when the text is not present, with silence preserved in the custody log.

```text
:app:connectedDebugAndroidTest
MDPH00124112500414: 2 PASSED

:app:testDebugUnitTest
32 passed, 0 skipped, 0 failed
```

## Summary of Findings

- **What the Collector receives**: Full-screen `Bitmap` frames from the `ObservationInput` stream.
- **How it obtains evidence**: Internal cropping using absolute coordinates (0, 710) to (1080, 870) for a 1080x2460 portrait display.
- **How it detects the phenomenon**: ML Kit `TextRecognizer` is applied to the local crop.
- **Resilience**: The detection logic now removes spaces before comparison to handle OCR producing "ATTACKINCOMING!".
- **Testimony reaches custody**: Verified that `TestimonyRecord` arrives unchanged with custody-assigned sequence numbers.
- **No downstream interpretation**: Confirmed that the Collector only identifies the *phenomenon* of the announcement, not the move name or strategy.

> [!NOTE]
> Fixed an unrelated non-exhaustive `when` expression in `ObservationEngineValidator.kt` to enable the instrumented build.
