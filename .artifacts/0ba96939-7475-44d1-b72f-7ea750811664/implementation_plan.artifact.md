# Implementation Plan - Brick 4: Raw Timeline Submission (Match Handoff)

Establish the coordinated handoff from accepted testimony in `TestimonyCustody` to the `RealityTimeline`, owned and managed by the `Match`.

## User Review Required

> [!IMPORTANT]
> The `Match` is the authoritative coordinator for this handoff. It subscribes to a stream of accepted testimony and publishes originating `RealityArticle` records to the timeline.

> [!NOTE]
> `TestimonyCustody` remains independent of the reality domain. It only exposes a neutral `Flow` of accepted testimony records.

## Proposed Changes

### Battle Layer - Custody Component

#### [MODIFY] [TestimonyCustody.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/custody/TestimonyCustody.kt)
- Add `val testimonyFlow: Flow<TestimonyRecord>` to the `TestimonyCustody` interface.
- Update `InMemoryTestimonyCustody` to include a `MutableSharedFlow<TestimonyRecord>`.
- In `submitTestimony()`, emit the accepted `TestimonyRecord` to the flow after it is stored and assigned a sequence number.

### Battle Layer - Observation Component

#### [MODIFY] [Match.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/Match.kt)
- Add `val custody: TestimonyCustody` and `val realityTimeline: RealityTimeline` as properties.
- Introduce a `CoroutineScope` (e.g., `matchScope`) to manage the lifetime of the testimony subscription.
- In `init` (or an explicit `start()` method), launch a collection of `custody.testimonyFlow`.
- For every `TestimonyRecord` received:
    - Generate a new `ArticleId` (UUID).
    - Map to `RealityArticle`:
        - `perceivedAt` = `TestimonyRecord.timestamp`.
        - `recordedAt` = `System.currentTimeMillis()`.
        - `sourceId` = `TestimonyRecord.sourceId`.
        - `payload` = `TestimonyRecord.payload`.
    - Append to `realityTimeline`.
- Ensure the scope is canceled when the Match state transitions to finished or via an explicit `release()` method.

## Verification Plan

### Automated Tests
- **[NEW] [MatchRealityHandoffTest.kt]**:
    - Wire a `Match` instance with real `InMemoryTestimonyCustody` and `InMemoryRealityTimeline`.
    - Submit a `RawTestimony` payload to custody via `submitTestimony()`.
    - Verify that a `RealityArticle` is automatically appended to the `RealityTimeline`.
    - Assert that `perceivedAt` matches the original testimony timestamp.
    - Assert that the `payload` and `sourceId` are preserved exactly.
    - Verify that `recordedAt` is accurately captured at the moment of handoff.
    - Demonstrate that multiple independent testimony submissions produce independent articles in the correct order.
    - Verify that subscription cleanup works when the Match is concluded.
