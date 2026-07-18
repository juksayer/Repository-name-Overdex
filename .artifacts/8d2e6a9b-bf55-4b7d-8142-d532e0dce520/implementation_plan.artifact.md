# Implementation Plan - Battle Timeline Architecture (Final Refinement)

This plan establishes the canonical domain architecture for the Battle Timeline. It is a first-class domain concept, independent of UI, observation, or tournaments.

Favor mechanical believability over animation complexity. If implementation reveals a small improvement that better communicates the hardware without changing the design philosophy, use your engineering judgment.

## User Review Required

> [!IMPORTANT]
> **Architecture Only**: This task is limited to establishing the package structure and key interface/class definitions. No business logic or UI behavior will be implemented.

> [!NOTE]
> **Immutability Pattern**: The architecture introduces `BattleTimelineBuilder` to reserve space for an immutable ledger pattern, separating the active observation session from the final battle record.

## Proposed Changes

I will create a new top-level `battle` package structure. I will avoid creating placeholder subclasses and focus on core contracts.

### `com.example.overdex.battle.timeline`

#### [NEW] [BattleTimeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimeline.kt)
The primary domain model and canonical ledger.

#### [NEW] [BattleTimelineBuilder.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/BattleTimelineBuilder.kt)
Reserved for the mutation of timelines during an active session before finalizing as an immutable record.

### `com.example.overdex.battle.timeline.event`

#### [NEW] [TimelineEvent.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/TimelineEvent.kt)
#### [NEW] [ObservationEvent.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/ObservationEvent.kt)

> [!CAUTION]
> Do not create placeholder event subclasses "just to fill out the architecture." Only create the abstract `TimelineEvent` contract and a single `ObservationEvent` implementation.

### `com.example.overdex.battle.timeline.confidence`

#### [NEW] [ConfidenceScore.kt](file:///home/sean/AndroidStudioProjects/Overdex/battle/timeline/confidence/ConfidenceScore.kt)
#### [NEW] [ConfidenceLevel.kt](file:///home/sean/AndroidStudioProjects/Overdex/battle/timeline/confidence/ConfidenceLevel.kt)
#### [NEW] [ConfidenceMath.kt](file:///home/sean/AndroidStudioProjects/Overdex/battle/timeline/confidence/ConfidenceMath.kt)

### `com.example.overdex.battle.timeline.evidence`

#### [NEW] [Evidence.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/evidence/Evidence.kt)
Renamed from `TimelineEvidence` for better internal naming within the `evidence` package.

### `com.example.overdex.battle.timeline.observer`

#### [NEW] [ObservationSource.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/observer/ObservationSource.kt)
#### [NEW] [ObserverId.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/observer/ObserverId.kt)

### `com.example.overdex.battle.timeline.serialization`

#### [NEW] [BattleTimelineSerializer.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/serialization/BattleTimelineSerializer.kt)
Interface definition only. No serialization format (JSON, etc.) will be baked into the domain at this stage.

## Verification Plan

### Manual Verification
- The user will perform the verification process personally.
- I will verify the physical directory structure and package declarations match the refined hierarchy.
- I will ensure no existing "battle log" or "history" implementations were extended or modified.
