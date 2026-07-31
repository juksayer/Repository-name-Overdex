# Brick 1 — Eliminate Registration Dead-End

The goal of this brick is to ensure that the user is never stuck in the `CaptureVerificationScreen` without a valid action when an observation is complete. Currently, if the `RegistrationEngine` has no actionable recommendation (`RegistrationAction.NONE`) after the observation has completed, the A-button does nothing and the UI displays "AWAITING DATA", even if the pipeline has finished processing.

## User Review Required

> [!IMPORTANT]
> This implementation follows a strict "UI-only" approach. We will NOT modify `RegistrationAssessment` or any logic within `RegistrationEngine`. The engine will continue to report `NONE` when it lacks a candidate, and the UI will interpret this state to offer a manual recovery path.

## Proposed Changes

### UI Layer

#### [MODIFY] [CaptureVerificationScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/CaptureVerificationScreen.kt)

- Define "dead-end" logic within the `CaptureVerificationScreen` to detect when:
    1. `recommendedAction == RegistrationAction.NONE`
    2. `pipelineStatus.currentStage == ObservationStage.Complete`
    3. The pipeline is not currently processing (i.e., `panelState.isProcessing` is false).
- When these conditions are met, the UI will present and execute `RegistrationAction.SELECT_SPECIES` as the effective action.
- Update the `onA` click handler and the `ServiceConsole` call to use this "effective" action.

## Scope Guardrail

> [!CAUTION]
> If implementing this requires changes outside `CaptureVerificationScreen` (for example, changes to `RegistrationEngine`, confidence calculations, candidate generation, or observation pipeline behavior), stop implementation immediately and create a follow-up brick instead.

## Verification Plan

### Manual Verification

- **Case A (Success):** Load a clear screenshot of a known Pokémon. Verify that "REGISTER SPECIMEN" appears and clicking A registers the Pokémon. Behavior remains unchanged.
- **Case B (Unknown Species):** Load an image where the engine has no actionable recommendation (e.g., blurry image). Verify that once the pipeline finishes and processing stops, the action changes from "AWAITING DATA" to "SELECT SPECIES MANUALLY".
- **Case C (Incomplete):** Observe the UI during recognition. Verify that while processing is active or before `ObservationStage.Complete`, the action remains "AWAITING DATA".
- **Case D (Auto-registration):** Verify that high-confidence matches still default to "REGISTER SPECIMEN".
- **Case E (Existing Manual Workflow):** Verify that if the user manually triggers species selection (after Case B or via other paths), the registration proceeds normally once a species is chosen.

### Automated Tests
- Run `RegistrationObservationFlowTest` and other relevant model tests to ensure no regressions in core registration logic.
