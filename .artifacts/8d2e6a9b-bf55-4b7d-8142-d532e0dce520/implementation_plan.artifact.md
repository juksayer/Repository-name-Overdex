# Implementation Plan - Manual Observation Source

This plan introduces developer-only tools to inject manual observations into the pipeline, validating the end-to-end architectural flow with simulated runtime data.

## Proposed Changes

### `com.example.overdex.battle.observation.debug`

#### [NEW] [ManualObservationSource.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/debug/ManualObservationSource.kt)
A developer utility to emit observations into a session.
- `emit(observation: Observation, session: ObservationSession)`: Forwards the observation to the session's workspace via `session.submit()`.

#### [NEW] [ObservationFactory.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/debug/ObservationFactory.kt)
Convenience helpers for creating test observations.
- `createDebugObservation()`: Generic observation with minimal metadata.
- `createVisualObservation(sourceId: String, frameUri: String)`: Observation with `VisualEvidence`.
- `createAudioObservation(sourceId: String, audioUri: String)`: Observation with `AudioEvidence`.
- `createStateObservation(sourceId: String, key: String, value: String)`: Observation with `StateEvidence`.

### `com.example.overdex.battle.debug`

#### [NEW] [ObservationPipelineDemo.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/debug/ObservationPipelineDemo.kt)
A developer-only demonstration of the end-to-end pipeline traversal.
- Logs the state of the workspace, reconciler output, and final timeline.

## Architectural Principles
- **Producer Isolation**: Producers like `ManualObservationSource` only create observations and never write directly to the timeline.
- **Unidirectional Flow**: The pipeline remains the single path into the `BattleTimeline`.
- **Zero Pokémon GO Logic**: The debug tools are domain-agnostic regarding game mechanics.

## Guardrails
- **Test Producer Only**: `ManualObservationSource` is a test producer for validating the pipeline, not a mock implementation of future sensors.
- **No Implementation Logic**: Reconciler implementations used for validation will be trivial.
- **No Gameplay Logic**: No implementation of OCR, ML, or game-specific detection.
- **No Android UI**: This is a developer-facing validation tool only.

## Verification Plan

### Manual Verification
- I will run the `ObservationPipelineDemo` (e.g., via a scratch script or logcat check) to verify:
    1. Observation emission works.
    2. `ObservationSession` accepts the input.
    3. `ObservationWorkspace` accumulates the data.
    4. `ObservationReconciler` processes the workspace.
    5. `BattleTimelineBuilder` assembles the final `BattleTimeline`.
- Build the project to ensure no compilation errors: `./gradlew :app:assembleDebug`.
