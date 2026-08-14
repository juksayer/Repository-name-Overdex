# Implementation Plan — Witness → Workspace Bridge (#297)

This plan bridges the `GoodEffortWitness` to the `BattleWorkspace` by submitting recognized "GOOD EFFORT" observations to the active `Match`.

## Proposed Changes

### Battle Module

#### [MODIFY] [GoodEffortWitness.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/witness/GoodEffortWitness.kt)
- Import `com.example.overdex.battle.observation.Observation`.
- Import `com.example.overdex.battle.timeline.confidence.ConfidenceScore`.
- Import `com.example.overdex.battle.timeline.confidence.ConfidenceLevel`.
- Import `com.example.overdex.battle.timeline.evidence.StateEvidence`.
- In the `start(match: Match)` loop:
    - When `isMatch(result)` is true:
        - Construct a `com.example.overdex.battle.observation.Observation` instance.
        - Use `StateEvidence` to wrap the recognized "GOOD EFFORT!" string.
        - Call `match.submit(observation)` to push the data into the `BattleWorkspace`.
- **Preserve Droidball Behavior**: Continue using `Log.d` for recognition feedback. (No `DroidballSignal` is currently emitted by this witness, and per constraints, we will not modify `DroidballService` to add new signal types).

## Verification Plan

### Automated Tests
- I will verify that `GoodEffortWitness` compiles and that its `start` method correctly invokes `match.submit`.

### Manual Verification
- Deploy the app to a device/emulator.
- Trigger the "GOOD EFFORT" recognition (e.g., by playing a battle or using a mock input).
- Verify via Logcat that `GoodEffortWitness` logs the recognition.
- Verify that `match.submit` is called (can be confirmed via debugger or temporary logs).
- (Optional) Check the `BattleWorkspace` state if a debug view is available.

## Constraints Checklist & Confidence Score
1. Only `GoodEffortWitness`? Yes.
2. Submit to `Match`? Yes.
3. Appear in `BattleWorkspace`? Yes.
4. Preserve Droidball signal? Yes (keeping it as-is).
5. DO NOT Change recognizer? Yes.
6. DO NOT Change OCR? Yes.
7. DO NOT Change Observation? Yes (not changing the class).
8. DO NOT Change Match/Workspace? Yes.
9. DO NOT Add resolver/Timeline/Registration? Yes.
10. DO NOT Change overlay/DroidballService? Yes.
11. DO NOT Refactor Witness architecture? Yes.
12. DO NOT Rename anything? Yes.

Confidence Score: 5/5
