# Implementation Plan - Witnessing the Countdown

This plan bridges the gap between the raw visual detection of the battle countdown and the system's ability to record and present that event. It resolves the architectural "domain mismatch" and provides visual feedback to the trainer.

## User Review Required

> [!NOTE]
> This change will make the "3, 2, 1, GO" sequence visible in the Droidball overlay during deployment.

## Proposed Changes

### Infrastructure & Presentation

#### [MODIFY] [DroidballService.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/DroidballService.kt)
- Add `data class CountdownWitnessed(val value: String) : DroidballFact()` to the `DroidballFact` sealed class.
- Expose a `fun emitFact(fact: DroidballFact)` in the `companion object` to allow observers to broadcast events to the UI.

#### [MODIFY] [BattleOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattleOverlay.kt)
- Update the `LaunchedEffect` to listen for `DroidballFact.CountdownWitnessed`.
- Display the countdown value prominently in the overlay when detected.

### Observation Layer

#### [MODIFY] [CountdownObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/CountdownObserver.kt)
- Resolve the domain mismatch by creating a `battle.observation.Observation` from the `CountdownRecognizer` result.
- Submit the observation to the active `Match` via `match.submit()`.
- Emit the `CountdownWitnessed` fact via `DroidballService.emitFact()`.
- Clean up unused imports.

## Verification Plan

### Automated Tests
- Run `app:assembleDebug` to ensure all cross-layer communication (Service -> Overlay, Observer -> Match) is syntactically correct.

### Manual Verification
- Deploy the instrument and check if the `BattleOverlay` correctly displays "3", "2", "1", and "GO" as they appear on screen.
- Verify that `Logcat` shows "Observed: CountdownObservation(value=...)" and the corresponding `match.submit()` logs.
