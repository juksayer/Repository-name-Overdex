package com.example.overdex.battle.reality

import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload

import com.example.overdex.model.TimelineRecord

/**
 * The canonical, immutable historical record of an article's journey through Reality.
 * 
 * A RealityArticle can represent an objective perception (originating event) 
 * or a reasoning outcome (derived event).
 * 
 * @property id Unique identity of this record in the timeline.
 * @property perceivedAt Temporal position provided by the source. For derivations, 
 *           this represents the best estimate of when the conclusion was true.
 * @property recordedAt Temporal position when the record was accepted by the timeline.
 * @property sourceId Identifies the producer of this specific record.
 * @property payload The uninterpreted data or reasoning outcome.
 * @property predecessorIds Identifies the specific articles used as input for this record.
 */
data class RealityArticle(
    val id: ArticleId,
    override val perceivedAt: Long,
    val recordedAt: Long,
    val sourceId: SourceId,
    val payload: TestimonyPayload,
    val predecessorIds: List<ArticleId> = emptyList()
) : TimelineRecord
