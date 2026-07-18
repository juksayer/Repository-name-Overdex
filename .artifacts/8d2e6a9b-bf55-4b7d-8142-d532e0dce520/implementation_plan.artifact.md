# Implementation Plan - Observation Session Foundation (Refined)

This plan establishes the `ObservationSession` domain, which represents the mutable workspace for collecting and reconciling observations during an active battle. This layer sits between evidence collection and the immutable `BattleTimeline`.

## User Review Required

> [!IMPORTANT]
> **Minimal Observers**: I have removed the "observer list" from the initial session model to avoid speculative abstraction. We will track observation sources at the individual `Observation` level for now.

> [!NOTE]
> **Workspace Scope**: `ObservationWorkspace` is designed to be the "source of current truth" for an active battle, prepared to eventually handle hypotheses and conflicts beyond simple observation storage.

## Proposed Changes

I will create a new package `com.example.overdex.battle.observation` to house the observation domain.

### `com.example.overdex.battle.observation`

#### [NEW] [ObservationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationSession.kt)
Represents an active observation workspace.
- Holds session ID and current lifecycle state.
- Owns an `ObservationWorkspace`. The resulting observations are eventually reconciled into a `BattleTimeline` through the observation pipeline.

#### [NEW] [ObservationSessionState.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationSessionState.kt)
Enum for the session lifecycle: `CREATED`, `ACTIVE`, `PAUSED`, `COMPLETED`, `CANCELLED`.

#### [NEW] [Observation.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/Observation.kt)
Represents a single transient observation before reconciliation.
- Properties: timestamp, evidence collection, confidence score, and `ObserverId`.

#### [NEW] [ObservationWorkspace.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationWorkspace.kt)
The mutable "active memory" of the session. Initially a collection of observations, but structured to support future hypothesis and conflict management.

#### [NEW] [ObservationReconciler.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationReconciler.kt)
Interface defining the responsibility of transforming transient observations into immutable `TimelineEvent` objects.

## Architectural Principles
- **Separation of Concerns**: Observation logic is distinct from the immutable ledger (`BattleTimeline`).
- **Responsibility-Driven API**: `ObservationReconciler` defines a clear transition point in the domain logic.
- **Side-by-Side Evolution**: Built alongside existing code to allow for incremental migration.

## Guardrails
- **No Gameplay Logic**: No energy counting, fast move timing, or strategy recommendations.
- **No Abstraction Bloat**: Removed observer lists and placeholder classes that don't have an immediate purpose.
- **No Existing Model Edits**: Do not modify files in `com.example.overdex.model.observation`.

## Verification Plan

### Manual Verification
- Verify the physical directory structure: `battle/observation/`.
- Ensure all package declarations and imports are correct.
- Confirm `BattleTimelineBuilder` and `TimelineEvent` are referenced according to the architectural flow.
- Build the project to ensure no compilation errors: `./gradlew :app:assembleDebug`.
