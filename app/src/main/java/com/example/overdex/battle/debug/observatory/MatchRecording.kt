package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * An immutable representation of a complete Match recording.
 */
@Serializable
data class MatchRecording(
    val matchId: String,
    val startTime: Long,
    val endTime: Long,
    val metadata: MatchMetadata?,
    val events: List<RecordedEvent>
)
