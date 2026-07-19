# Implementation Plan - Presentation Layer Refinement (Semantic Models)

Refine the Presentation Layer to use strictly semantic models instead of strings, ensuring the state remains renderer-independent and localized-ready.

## User Review Required

> [!IMPORTANT]
> **No Strings in PresentationState**: All descriptive fields (`activityDescription`, `missingFields`, etc.) will be replaced with semantic enums or data models. Renderers will be responsible for localizing or formatting these for the user.
>
> **Enriched Tactical Evidence**: `TacticalPresentation` will now expose a list of `Evidence` objects (semantic models of observed moves, type advantages, etc.) instead of a raw reasoning string.
>
> **Semantic Progress**: `EstimatedCompletion` will be a structured model (e.g., `remainingObservations`) rather than a formatted string.

## Proposed Changes

### [Component] Presentation Layer Models

#### [MODIFY] [PresentationState.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/presentation/PresentationState.kt)
- **`ObservationPresentation`**:
    - Replace `activityDescription: String` with `activity: ObservationActivity` (Enum).
    - Replace `missingFields: List<String>` with `missingRequirements: List<ObservationRequirement>` (Enum).
    - Replace `estimatedCompletion: String` with `completionEstimate: ObservationEstimate` (Data Class).
- **`TacticalPresentation`**:
    - Rename `recommendedAction` to `primaryGuidance`.
    - Rename `priority` to `urgency`.
    - Rename `threatLevel` to `threat`.
    - Rename `playerAdvantage` to `advantage`.
    - Rename `shieldRecommended` to `shieldRequired`.
    - Replace `reasoning: String` with `evidence: List<TacticalEvidence>` (Sealed Interface/Class).

### [Component] Presentation Layer Logic

#### [MODIFY] [PresentationMapper.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/presentation/PresentationMapper.kt)
- Update mapping logic to populate new semantic models.
- Move "Reasoning" generation logic (if any) out of the mapper and into a domain-level service (Intelligence), or simply map the raw tactical evidence.

### [Component] Domain Layer Refinements (Optional/Deferred)
- Ensure `GuidedObservationPipeline` and `Intelligence` modules provide the necessary semantic outputs to feed the mapper.

## Verification Plan

### Automated Tests
- **Mapper Tests**: Verify that `PresentationState` contains the expected semantic enums for various pipeline states.
- **Serialization Test**: Verify that `PresentationState` can be serialized to JSON (preparing for the future Replay Viewer).

### Manual Verification
- Verify that Droidball and HUD continue to render correctly by updating their UI-level mapping from semantic enums to strings/colors.
