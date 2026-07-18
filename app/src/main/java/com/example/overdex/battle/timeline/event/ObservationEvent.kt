package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.evidence.Evidence
import com.example.overdex.battle.timeline.confidence.ConfidenceScore

/**
 * Represents an observation captured during battle, supported by [Evidence]
 * and qualified by a [ConfidenceScore].
 */
data class ObservationEvent(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val evidence: Evidence,
    val confidence: ConfidenceScore
) : TimelineEvent
