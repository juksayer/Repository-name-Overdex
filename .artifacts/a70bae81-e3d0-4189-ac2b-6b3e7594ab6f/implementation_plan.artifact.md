# Implementation Plan - AttackIncomingWitness

Create the production `AttackIncomingWitness` implementation for the "Attack Incoming!" phenomenon.

## Proposed Changes

### Battle Layer

#### [NEW] [AttackIncomingWitness.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/witness/AttackIncomingWitness.kt)
- Create a new Witness class that implements the `Observer` interface.
- **Identity**:
    - `observerId`: `ObserverId("ATTACK_INCOMING_WITNESS", ObservationSource.SCREEN_CAPTURE)`
    - `name`: "Attack Incoming Witness"
- **Aperture**: `calibration.moveBannerRegion`.
- **Recognition Logic**:
    - Use ML Kit `TextRecognition` to extract text from the cropped region.
    - Match behavior: Normalize (uppercase, trim, remove spaces) and check if it contains "ATTACKINCOMING".
- **Handoff**:
    - Call `match.custody.submitAvailability` on `start`.
    - Call `match.custody.submitInputAvailability` for each frame.
    - If recognized, call `match.custody.submitTestimony` with `RawTestimony` containing the **original recognized text**.
- **Structure**: Follow `GoodEffortWitness` precedent, using constructor lambdas for `recognize` and `crop`.

## DO NOT
- Modify any existing files.
- Interpret the phenomenon or mutate Match state.
- Create new regions, payloads, or collectors.
- Build, test, or manually verify the changes.

## Completion
- Report implementation details as requested and stop.
