package com.example.overdex.battle.observation

import com.example.overdex.battle.timeline.confidence.ConfidenceScore
import com.example.overdex.battle.timeline.evidence.Evidence
import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Represents a single transient observation during an active Match.
 * 
 * Unlike the immutable events in the Battle Timeline, an [Observation] is a raw,
 * intermediate data point that qualifies "who saw what" before it is reconciled
 * and committed to history.
 * 
 * @property timestamp The time when the observation was recorded.
 * @property observerId The identifier of the [Observer] that produced this record.
 * @property evidence A list of evidence items (e.g., OCR text, images) supporting this observation.
 * @property confidence The system's certainty regarding this observation.
 */
data class Observation(
    val timestamp: Long,
    val observerId: ObserverId,
    val evidence: List<Evidence>,
    val confidence: ConfidenceScore
)
