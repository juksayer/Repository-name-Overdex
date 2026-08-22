package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.evidence.Evidence
import com.example.overdex.battle.timeline.confidence.ConfidenceScore

/**
 * A [TimelineEvent] representing an observation captured during battle.
 * 
 * Observation events are the primary record of evidence-based beliefs in the
 * timeline, supporting the auditability of Overdex's intelligence.
 * 
 * @property evidence The underlying [Evidence] (e.g., image frame) supporting this event.
 * @property confidence The system's certainty regarding the observation at the time of record.
 */
data class ObservationEvent(
    override val timestamp: Long,
    override val observerId: ObserverId,
    override val evidence: Evidence,
    val confidence: ConfidenceScore
) : TimelineEvent
