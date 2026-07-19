package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * An immutable representation of a single Observation Session recording.
 */
@Serializable
data class ObservationRecording(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val metadata: SessionMetadata?,
    val events: List<RecordedEvent>
)
