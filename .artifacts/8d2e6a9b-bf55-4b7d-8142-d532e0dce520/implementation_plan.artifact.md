# Implementation Plan - Observation Pipeline Skeleton (Refined)

This plan establishes the minimal end-to-end observation pipeline, connecting the `ObservationSession` to the `BattleTimeline` while strictly separating event derivation (Reconciliation) from ledger construction (Builder).

## User Review Required

> [!IMPORTANT]
> **Builder Responsibility**: `BattleTimelineBuilder` is no longer a placeholder. It now owns the responsibility of accepting derived events and constructing the immutable `BattleTimeline`.

> [!NOTE]
> **Reconciler Scope**: `ObservationReconciler` remains focused purely on deriving `TimelineEvent` objects from observations, returning a list of events rather than a finished timeline.

## Proposed Changes

### `com.example.overdex.battle.observation`

#### [MODIFY] [ObservationSession.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationSession.kt)
- Add `submit(observation: Observation)` method to forward observations to the owned `ObservationWorkspace`.

#### [MODIFY] [ObservationReconciler.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/ObservationReconciler.kt)
- Maintain the current contract: `fun reconcile(workspace: ObservationWorkspace): List<TimelineEvent>`.

### `com.example.overdex.battle.timeline`

#### [MODIFY] [BattleTimelineBuilder.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimelineBuilder.kt)
- Implement `addEvent(event: TimelineEvent)` to accumulate derived events.
- Implement `build(): BattleTimeline` to produce the final immutable record.

## Guardrails
- **Responsibility Isolation**: No object in this pipeline should infer more than its responsibility requires (Session stores, Workspace accumulates, Reconciler derives, Builder assembles, Timeline records).
- **No Implementation Logic**: Reconciler implementations used for validation will be trivial.
- **Side-Effect Free**: Submission of an observation does not trigger automatic reconciliation.
- **Strict Boundaries**: The session does not know about the builder; the reconciler does not know about the finished timeline.

## Verification Plan

### Manual Verification
- I will create a temporary verification script in the `scratch/` directory to demonstrate the full pipeline:
  1. Create `Observation`
  2. `session.submit(observation)`
  3. `reconciler.reconcile(session.workspace)` -> `List<TimelineEvent>`
  4. `builder.addEvent(event)`
  5. `builder.build()` -> `BattleTimeline`
- Build the project to ensure no compilation errors: `./gradlew :app:assembleDebug`.
