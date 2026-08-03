# Implementation Plan — Wire Observation Dispatcher

Wire the `ObservationDispatcher` in `PokedexViewModel` to manage the lifecycle of production battle observers (`SpeciesObserver`, `CountdownObserver`) during a `Match`.

## User Review Required

> [!IMPORTANT]
> This plan introduces the first real-world wiring of the `ObservationDispatcher` into the production deployment flow. It assumes that `DroidballService.frames` is the source of visual evidence for these observers.

## Proposed Changes

### Core Infrastructure
#### [MODIFY] [CalibrationManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/CalibrationManager.kt)
- Add loading and saving logic for the `countdownRegion`.

### Battle Observation
#### [MODIFY] [SpeciesObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/SpeciesObserver.kt)
- Replace the debug log with a call to `match.submit(observation)`.
- Convert the OCR result into a `battle.observation.Observation` using `ObservationFactory` or direct instantiation.

#### [MODIFY] [CountdownObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/CountdownObserver.kt)
- Replace the debug log with a call to `match.submit(observation)`.
- Convert the OCR result into a `battle.observation.Observation`.

### ViewModel Integration
#### [MODIFY] [PokedexViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt)
- Add a private `observationDispatcher` property.
- In `deployInstrument()`:
    - Instantiate `DroidballObservationInput`.
    - Load the latest `BattleCalibration` from `CalibrationManager`.
    - Register `SpeciesObserver` and `CountdownObserver` with the dispatcher.
    - Call `observationDispatcher.startAll(match)`.
- In `stopObservation()`:
    - Call `observationDispatcher.stopAll()`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify the build.

### Manual Verification
- Deploy the instrument in a live battle.
- Verify through logs that `SpeciesObserver` and `CountdownObserver` are started.
- Verify that `Match.frameCount` increments and that observations are submitted to the `Match.workspace`.
