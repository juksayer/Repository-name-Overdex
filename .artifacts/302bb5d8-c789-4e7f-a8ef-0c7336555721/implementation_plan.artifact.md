# Implementation Plan — Git #279: Expanded Countdown Recognition

This plan expands the visual sensing range of the countdown observer to witness the complete pre-battle sequence, from the "VS" screen through the "GO" signal. It maintains a strict boundary between recognition (witnessing) and interpretation (intelligence).

## Objective
**Expand the existing countdown recognizer to recognize the complete pre-battle sequence.**

## Proposed Changes

### Data & Calibration

#### [MODIFY] [CalibrationManager.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/CalibrationManager.kt)
- Expand the default fallback coordinates for the `countdownRegion`:
    - `x = 0.25f`
    - `y = 0.25f`
    - `width = 0.50f`
    - `height = 0.30f`
- This expansion ensures the "VS" circle (center-screen) and the "Get Ready!" pill are both within the sensing field.

### Battle Observation Layer

#### [MODIFY] [CountdownRecognizer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/CountdownRecognizer.kt)
- Update the set of accepted strings to include:
    - `"VS"`
    - `"GETREADY"`
    - `"3"`, `"2"`, `"1"`, `"GO"`

#### [MODIFY] [CountdownObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/CountdownObserver.kt)
- Update the failure logic in the sensing loop:
    - If the OCR produces a string that does *not* match the accepted set, log the **normalized OCR string**.
    - This provides "Sensing Transparency" to aid in future OCR tuning.
- Continue producing `CountdownWitness` and publishing `CountdownWitnessed` facts for all accepted values.

---

## Not Included
- **Battle Lifecycle State**: Assigning meaning to "VS" (e.g., triggering a match start).
- **Match Timers**: Starting clocks based on "GO".
- **Opponent Identification**: Extracting names from the VS screen.
- **Intelligence**: Any reasoning about the sequence.

---

## Verification Plan

### Automated
- `app:assembleDebug`

### Manual
Deploy the instrument during battle startup.
Verify `Logcat` shows the expected sequence:
1. `CountdownWitness(value=VS)`
2. `CountdownWitness(value=GETREADY)`
3. `CountdownWitness(value=3)`
4. `CountdownWitness(value=2)`
5. `CountdownWitness(value=1)`
6. `CountdownWitness(value=GO)`

If a value is misread (e.g., "V5"), verify that the raw normalized string is logged for diagnostics.
