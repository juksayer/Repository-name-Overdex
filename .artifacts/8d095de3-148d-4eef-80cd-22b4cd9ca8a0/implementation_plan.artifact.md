# Implementation Plan — Wire Production Observation Dispatcher

Wire the `ObservationDispatcher` into `PokedexViewModel` to manage the lifecycle of production battle observers (`SpeciesObserver` and `CountdownObserver`) during a `Match`.

## User Review Required

> [!IMPORTANT]
> This plan establishes the production lifecycle for battle sensing. It assumes that `DroidballService.frames` is the primary source of visual data, mediated through `DroidballObservationInput`.

## Proposed Changes

### ViewModels

#### [MODIFY] [PokedexViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt)
- **Add Property**: `private val observationDispatcher = ObservationDispatcher()`
- **Update `deployInstrument()`**:
    - Instantiate `DroidballObservationInput`.
    - Load the current `BattleCalibration`.
    - Register `SpeciesObserver` and `CountdownObserver` with the `observationDispatcher`.
    - Call `observationDispatcher.startAll(match)` once the match is initialized.
- **Update `stopObservation()`**:
    - Call `observationDispatcher.stopAll()` to cleanly release observer resources.

## Definition of Done

- `ObservationDispatcher` is owned by `PokedexViewModel`.
- `SpeciesObserver` and `CountdownObserver` are registered exactly once per deployment.
- Dispatcher starts only after `Match` initialization completes.
- Dispatcher stops cleanly during `stopObservation()`.
- No observer behavior is modified.
- No recognition logic is modified.
- Build succeeds.
- Application launches successfully.
- 0RBSLOP.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify compilation and wiring.

### Manual Verification
1. **Deploy ODX-FI**: Launch the instrument from the Pokedex.
2. **Verify Logs**: Check Logcat for "Observer started" or similar production logs from `SpeciesObserver` or `CountdownObserver`.
3. **Stop Observation**: Stop the instrument and verify that background processing (and associated logs) ceases.
