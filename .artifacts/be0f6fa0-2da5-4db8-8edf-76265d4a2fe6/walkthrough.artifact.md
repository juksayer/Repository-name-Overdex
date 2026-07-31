# Walkthrough — Eliminate Registration Dead-End

This brick ensures that trainers are never stranded on the `CaptureVerificationScreen` when an observation finishes without a clear species identification.

## Changes Made

### UI Layer

#### [CaptureVerificationScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/CaptureVerificationScreen.kt)
- Introduced `effectiveAction` derivation logic. This state-derived value determines the final action available to the user based on both the engine's recommendation and the current pipeline status.
- Updated the `onA` controller to use `effectiveAction`, ensuring consistent behavior between the visual prompt and the actual button press.

#### [ServiceConsole.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/ServiceConsole.kt)
- Modified `ServiceConsole` to accept an optional `effectiveAction`.
- The console now prioritizes the `effectiveAction` for its primary prompt, allowing it to display "SELECT SPECIES MANUALLY" even if the engine's internal recommendation is `NONE`.

## Verification Results

### Automated Tests
- Ran `RegistrationObservationFlowTest` and other unit tests via `:app:testDebugUnitTest`.
- **Result:** 23 passed, 0 failed.

### Manual Verification (Logic Check)
- **Case A (Success):** Unchanged. `effectiveAction` inherits `REGISTER` from the engine.
- **Case B (Unknown Species):** When `recommendedAction` is `NONE` and pipeline is `Complete`, `effectiveAction` successfully switches to `SELECT_SPECIES`.
- **Case C (Processing):** While `isProcessing` is true, the dead-end recovery is suppressed, preserving the "AWAITING DATA" / "PROCESSING" state.
- **Case F (Ownership):** Verified that `RegistrationEngine` remains untouched and continues to report its findings purely, while the UI handles the workflow recovery.

render_diffs(file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/CaptureVerificationScreen.kt)
render_diffs(file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/ServiceConsole.kt)
