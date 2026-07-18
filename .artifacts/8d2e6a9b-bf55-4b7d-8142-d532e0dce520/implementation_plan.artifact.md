# Implementation Plan - Battle Timeline Expansion (Refined)

This plan expands the Battle Timeline architecture with concrete event types, evidence implementations, and a refined observer model. This establishes the domain vocabulary for Pokémon GO battles without introducing behavior, game mechanics, or UI.

## User Review Required

> [!IMPORTANT]
> **Observation-First Events**: All new event types are restricted to observable actions (e.g., `ShieldUsed`) rather than inferred state changes (e.g., `ShieldBroken` or `EnergyGenerated`).

> [!NOTE]
> **Evidence Refinement**: `DerivedEvidence` has been removed as it represents reasoning rather than primary source evidence.

## Proposed Changes

### Event Taxonomy
I will create concrete implementations of `TimelineEvent` in the `com.example.overdex.battle.timeline.event` package.

#### [NEW] [BattleLifecycleEvents.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/BattleLifecycleEvents.kt)
- `BattleStarted`: Marks the beginning of a battle session.
- `BattleEnded`: Marks the conclusion of a battle session.

#### [NEW] [CombatEvents.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/CombatEvents.kt)
- `FastMovePerformed`: Recorded when a fast move is identified.
- `ChargedMoveStarted`: Recorded when the charged move sequence begins.
- `ChargedMoveResolved`: Recorded when the charged move damage/effect is applied.
- `ShieldUsed`: Recorded when a shield is deployed.

#### [NEW] [StatusEvents.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/event/StatusEvents.kt)
- `PokemonSwitched`: Recorded when a trainer switches their active Pokémon.
- `PokemonFainted`: Recorded when a Pokémon's HP reaches zero.

### Evidence Implementations
I will create concrete implementations of the `Evidence` interface in the `com.example.overdex.battle.timeline.evidence` package.

#### [NEW] [EvidenceTypes.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/evidence/EvidenceTypes.kt)
- `VisualEvidence`: Supporting data from screen/video frames.
- `AudioEvidence`: Supporting data from audio captures.
- `StateEvidence`: Supporting data from internal application or system state.

### Observer Model Expansion
I will update the `ObservationSource` enum to reflect the specific inputs of the Overdex instrument.

#### [MODIFY] [ObservationSource.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/timeline/observer/ObservationSource.kt)
- Expand the enum to include: `SCREEN_CAPTURE`, `AUDIO_CAPTURE`, `DROIDBALL`, `REMOTE_PARTNER`, `SYSTEM_INFERENCE`.

### Serializer Definition
The `BattleTimelineSerializer` interface remains a pure contract without backing implementation for any specific storage format.

## Guardrails

- **No Inferred Mechanics**: Do not include Pokémon GO-specific game mechanics beyond event names. Keep events as close to observations as possible (e.g., no `EnergyGenerated`).
- **Builder Placeholder**: Do not add behavior to `BattleTimelineBuilder`. It remains a placeholder for future observation reconciliation.
- **No Implementation**: Do not wire up the events or evidence yet. Establish the vocabulary only.

## Verification Plan

### Manual Verification
- Verify that all new files are in the correct packages (`event`, `evidence`, `observer`).
- Confirm that the `TimelineEvent` contract is correctly implemented by all new event types.
- Ensure no behavior was added to `BattleTimelineBuilder`.
