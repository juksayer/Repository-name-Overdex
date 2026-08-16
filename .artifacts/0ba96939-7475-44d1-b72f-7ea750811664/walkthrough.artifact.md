# Walkthrough - Brick 4: Raw Timeline Submission (Match Handoff)

Completed the implementation of the coordinated handoff from accepted testimony in `TestimonyCustody` to the `RealityTimeline`. The `Match` now acts as the authoritative coordinator for publishing originating `RealityArticle` records.

## Changes Made

### Battle Layer - Custody Component

#### [TestimonyCustody.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/custody/TestimonyCustody.kt)
- Added `testimonyFlow: Flow<TestimonyRecord>` to the `TestimonyCustody` interface to allow decoupled notification of accepted testimony.
- Implemented the flow in `InMemoryTestimonyCustody` using a `MutableSharedFlow` with an emission point inside `submitTestimony`.
- This ensures that only **accepted** testimony (with assigned sequence numbers) is exposed to the rest of the system.

### Battle Layer - Observation Component

#### [Match.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/Match.kt)
- Updated the `Match` class to own a `TestimonyCustody` and a `RealityTimeline`.
- Implemented an internal `CoroutineScope` and a subscription to `custody.testimonyFlow` in the `init` block.
- For every accepted testimony, the `Match`:
    - Generates a unique `ArticleId`.
    - Maps the data to an originating `RealityArticle`.
    - Preserves `perceivedAt` from the testimony timestamp.
    - Establishes `recordedAt` at the moment of timeline append.
    - Passes the `sourceId` and `payload` through unchanged.
- Added a `release()` method to properly cancel the subscription when the match is concluded.

## Verification Results

### Automated Tests
- **[MatchRealityHandoffTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/battle/observation/MatchRealityHandoffTest.kt)**:
    - **Automatic Append**: Proved that submitting testimony to custody automatically results in a new article in the `RealityTimeline`.
    - **Data Integrity**: Confirmed that `perceivedAt`, `sourceId`, and the neutral `payload` are preserved exactly.
    - **Ordering**: Verified that multiple independent submissions produce independent articles in the correct chronological order.
- **[TestimonyCustodyTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/battle/custody/TestimonyCustodyTest.kt)** (Updated):
    - Verified that the new `testimonyFlow` correctly emits only accepted testimony records.
- **[AttackIncomingCollectorAndroidTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/androidTest/java/com/example/overdex/battle/collector/AttackIncomingCollectorAndroidTest.kt)** (Updated):
    - Ensured compatibility with the updated `TestimonyCustody` interface.

```text
:app:testDebugUnitTest
39 passed, 0 skipped, 0 failed

:app:connectedDebugAndroidTest (MIAD01)
2 PASSED
```

## Summary of Handoff Chain

```text
AttackIncomingCollector (Producer)
   │
   │ testimony
   ▼
InMemoryTestimonyCustody (Bagman)
   │
   │ [Assign Sequence #]
   │ [Preserve in Ledger]
   │ [Emit TestimonyRecord]
   ▼
Match (Coordinator)
   │
   │ [Subscribe to testimonyFlow]
   │ [Create RealityArticle]
   ▼
InMemoryRealityTimeline (Reality Ledger)
```

> [!NOTE]
> This implementation preserves the constitutional boundary: Custody remains independent of the Reality domain, and the `Match` owns the responsibility of translating evidence into history.
