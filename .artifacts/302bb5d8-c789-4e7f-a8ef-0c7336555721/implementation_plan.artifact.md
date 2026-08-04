# Implementation Plan - Git #274: Countdown Witness Proof of Concept

This plan focuses strictly on proving that the `CountdownRecognizer` can produce a module-specific "Witness" within the Battle domain. It avoids side effects like Match submission or UI updates to ensure a clean, isolated validation of the sensing logic.

## User Review Required

> [!NOTE]
> This is a transitional step. The "Witness" created here is internal to the `CountdownObserver` and will be logged to verify the ML Kit recognition performance before it is integrated into the Battle Timeline.

## Proposed Changes

### Battle Observation Layer

#### [MODIFY] [CountdownObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/CountdownObserver.kt)
- Define a local `data class CountdownWitness(val value: String, val timestamp: Long)`.
- Update the `supply` block to:
    - Capture the recognition result from `CountdownRecognizer`.
    - If the result is "3", "2", "1", or "GO", instantiate a `CountdownWitness`.
    - Log the witness exactly as requested: `CountdownWitness(value=...)`.
- Remove the existing `RecognitionObservationMapper.map` call to avoid using retired/scrapped logic.
- Ensure no calls to `match.submit()` or `DroidballService`.

## Verification Plan

### Automated Tests
- Run `app:assembleDebug` to ensure no syntax errors.

### Manual Verification
- Deploy the instrument and monitor `Logcat`.
- Verify that the logs show:
  - `CountdownWitness(value=3)`
  - `CountdownWitness(value=2)`
  - `CountdownWitness(value=1)`
  - `CountdownWitness(value=GO)`
