# Walkthrough - Brick 4B: Establish the Reality Timeline

Completed the implementation of the `RealityTimeline`, a foundational constitutional ledger for preserving the objective journey of Articles in Overdex.

## Changes Made

### Battle Layer - Reality Component

#### [ArticleId.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/reality/ArticleId.kt)
- Established `ArticleId` as a unique identity representation for records within the Reality Timeline. It is decoupled from external sequence systems (like custody) to allow for independent referencing.

#### [RealityArticle.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/reality/RealityArticle.kt)
- Created the `RealityArticle` data class to serve as the canonical, immutable record.
- Features include:
    - **Dual Timestamps**: `perceivedAt` (source's estimate) and `recordedAt` (timeline entry time).
    - **Neutral Payload**: Carries uninterpreted data (`TestimonyPayload`).
    - **Informational Ancestry**: Supports many-to-one derivation via `predecessorIds`.
    - **Provenance**: Explicitly identifies the producer through `sourceId`.

#### [RealityTimeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/reality/RealityTimeline.kt)
- Defined the `RealityTimeline` interface and its thread-safe `InMemoryRealityTimeline` implementation.
- The timeline is append-only and completely isolated from existing historical timelines, ensuring a clean slate for the Reality domain.

## Verification Results

### Automated Tests
- **[RealityTimelineTest.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/test/java/com/example/overdex/battle/reality/RealityTimelineTest.kt)**:
    - **Objective Receipt**: Confirmed that `perceivedAt` and `recordedAt` are preserved as distinct values.
    - **Many-to-One Derivation**: Verified that a record can correctly reference multiple predecessors, preserving the evidence chain.
    - **Immutability**: Demonstrated that reasoning results (new articles) do not alter their predecessors and that the internal store is protected from external mutation.
    - **Identity Independence**: Verified that `ArticleId` remains unique and independent of external ordering systems.

```text
:app:testDebugUnitTest
37 passed, 0 skipped, 0 failed
```

## Summary of Answers

1.  **What is a Timeline event?** A `RealityArticle`—an immutable record of a perception or reasoning outcome concerning an Article.
2.  **How is it uniquely identified?** Via `ArticleId` (independent of custody sequence).
3.  **What does an originating event say?** "Source S perceived Payload P at time T."
4.  **How does an Article own its originating submission?** The Article is the subject of the record; the record preserves the originating state.
5.  **How is time represented?** Separated into `perceivedAt` (reality time) and `recordedAt` (system time).
6.  **How is provenance preserved?** Through `sourceId` and `id`.
7.  **How does a later event reference an earlier one?** Via the `predecessorIds` list.
8.  **Minimum append operation?** `RealityTimeline.append( RealityArticle )`.
9.  **Where does it live?** It is a Match-scoped component (in-memory implementation for now).
10. **Proof of no overwrite?** Verified through tests that new derivations are separate records and the timeline is append-only with immutable articles.

> [!NOTE]
> This implementation is completely isolated from the four existing "Timeline" implementations identified in previous research.
