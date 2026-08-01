# Git #261 — Match Countdown Implementation Plan

Introduce the first production battle fact: `MatchCountdown`. This brick establishes the pattern of an observer witnessing a real battle fact and submitting it to an active `Match`.

## User Review Required

> [!IMPORTANT]
> The observer registration will be integrated into `PokedexViewModel.deployInstrument`, which is the current "production" entry point for starting a battle.

## Proposed Changes

### Battle Component

#### [MODIFY] [Match.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/Match.kt)
- Add a list to store `MatchCountdown` instances.
- Add a `submit(countdown: MatchCountdown)` method.

#### [NEW] [MatchCountdown.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/MatchCountdown.kt)
- Define `MatchCountdown` data class (timestamp: Long, value: CountdownValue).
- Define `CountdownValue` enum (THREE, TWO, ONE, GO, UNKNOWN).

#### [NEW] [MatchCountdownRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/MatchCountdownRecognizer.kt)
- Implement `recognize(bitmap: Bitmap): CountdownValue` using ML Kit Text Recognition.

#### [NEW] [MatchCountdownObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/MatchCountdownObserver.kt)
- Implement `Observer` interface.
- Crop the configured countdown region from incoming frames.
- Use `MatchCountdownRecognizer` to identify the countdown value.
- Submit `MatchCountdown` to the active `Match`.

---

### Data Component

#### [MODIFY] [BattleCalibration.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/BattleCalibration.kt)
- Add `countdownRegion: AnchorRegion` to the `BattleCalibration` data class.

---

### UI / Pipeline Component

#### [MODIFY] [PokedexViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt)
- Wire `MatchCountdownObserver` into the match deployment flow.
- Ensure the observer is started when the match begins and stopped when it ends.

## Verification Plan

### Automated Tests
- Create unit tests for `MatchCountdownRecognizer` with sample images of "3", "2", "1", and "GO".
- Create unit tests for `MatchCountdownObserver` to verify it correctly crops and submits facts.

### Manual Verification
1. Deploy the instrument in a live Pokémon GO battle.
2. Observe the logs to verify that `MatchCountdown` facts are being submitted to the `Match` during the pre-battle countdown.
3. Verify that unknown or noisy frames do not produce fabricated countdown values.
4. Verify that no `BattleEvent`s are created and no other battle behavior (timers, replay, etc.) is triggered.
