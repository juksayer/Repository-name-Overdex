# Implementation Plan — Git #276: Countdown Presentation

This plan establishes the **presentation boundary** for Battle countdowns. It allows the Battle Overlay to subscribe to published `CountdownWitnessed` facts and display them to the trainer without introducing any downstream reasoning or persistence.

## Objective

Establish the **presentation boundary** for Battle countdowns.

## User Review Required

> [!NOTE]
> This commit completes the first end-to-end Battle pipeline:
>
> ```
> CountdownRecognizer
>         ↓
> CountdownWitness
>         ↓
> DroidballFact.CountdownWitnessed
>         ↓
> BattleOverlay
> ```
>
> The countdown is presented to the trainer exactly as published.

## Proposed Changes

### Presentation Layer

#### [MODIFY] [BattleOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattleOverlay.kt)
- Add a `countdownValue` state using `remember { mutableStateOf<String?>(null) }`.
- Update the `LaunchedEffect` to handle `DroidballFact.CountdownWitnessed` and update `countdownValue`.
- Update the UI to display the `countdownValue` prominently if it is not null.

## Not Included

- Match integration
- Timeline integration
- Memory
- Intelligence
- Recommendations
- Articles
- Persistence
- Anchor acquisition
- Additional recognizers

## Verification Plan

### Automated
- `app:assembleDebug`

### Manual
1. Deploy the instrument.
2. Observe the following sequence in the Battle Overlay:
   - `3`
   - `2`
   - `1`
   - `GO`
3. Verify synchronization with the in-game countdown.
4. Verify `Logcat` continues to show `CountdownWitness(value=...)` alongside the UI updates.
