package com.example.overdex.battle.observation

import com.example.overdex.battle.timeline.confidence.ConfidenceScore
import com.example.overdex.battle.timeline.evidence.Evidence
import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Represents a single transient observation before it becomes an immutable TimelineEvent.
 * It qualifies "who saw what" during an active session.
 */
data class Observation(
    val timestamp: Long,
    val observerId: ObserverId,
    val evidence: List<Evidence>,
    val confidence: ConfidenceScore
)
