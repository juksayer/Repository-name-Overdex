# Implementation Plan - Brick 4B: Establish the Reality Timeline (Revised)

Establish the `RealityTimeline` to preserve immutable records concerning Articles. This timeline operates independently of existing interpretive timelines and focuses on the objective history of what was perceived and what was reasoned.

## User Review Required

> [!IMPORTANT]
> A **`RealityArticle`** is a record *concerning* an independent Article. It is not synonymous with the phenomenon itself.

> [!NOTE]
> We distinguish between **`perceivedAt`** (when the source says it happened) and **`recordedAt`** (when the record was committed to the timeline).

> [!TIP]
> Derivations are many-to-one. A single `RealityArticle` can reference multiple preceding articles via **`predecessorIds`**.

## Proposed Changes

### Battle Layer - Reality Component

#### [NEW] [ArticleId.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/reality/ArticleId.kt)
- `data class ArticleId(val value: String)`: Unique identifier for records in this timeline. Independent of custody sequence numbers.

#### [NEW] [RealityArticle.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/reality/RealityArticle.kt)
- `data class RealityArticle`:
    - `id: ArticleId`: The unique identity of this record.
    - `perceivedAt: Long`: Temporal position provided by the source. For derived articles, this represents the source's authoritative estimate of when the conclusion was true (typically the `perceivedAt` of the latest predecessor).
    - `recordedAt: Long`: Temporal position when committed to this timeline.
    - `sourceId: SourceId`: Identifies the producer of **this current record** (the office performing the derivation or collection).
    - `payload: TestimonyPayload`: The uninterpreted data or reasoning outcome.
    - `predecessorIds: List<ArticleId> = emptyList()`: Identifies the **informational ancestry** (the specific articles used as input for this record).

#### [NEW] [RealityTimeline.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/reality/RealityTimeline.kt)
- `interface RealityTimeline`:
    - `fun append(article: RealityArticle)`
    - `fun getArticles(): List<RealityArticle>`
- `class InMemoryRealityTimeline`: Thread-safe, append-only implementation. Completely isolated from existing historical timelines.

## Verification Plan

### Automated Tests
- **[NEW] [RealityTimelineTest.kt]**:
    1.  **Objective Receipt**: Verify that an originating `RealityArticle` preserves `perceivedAt` and `recordedAt` as distinct values.
    2.  **Immutability**: Verify that once appended, a record cannot be modified.
    3.  **Provenance**: Verify `sourceId` and `id` remain intact.
    4.  **Many-to-One Derivation**:
        - Append Article A and Article B.
        - Create Article C with `predecessorIds = listOf(A.id, B.id)`.
        - Verify Article C correctly references its predecessors.
    5.  **Independence**: Ensure `ArticleId` is generated independently of any external sequence or ID system.
