package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * An immutable representation of a complete observation session recording.
 * 
 * This data class contains the entire chronological history of events captured
 * by the [ObservationRecorder], along with device and session metadata.
 * 
 * @property sessionId The unique identifier for the recording session.
 * @property startTime The system time when recording began.
 * @property endTime The system time when recording ended.
 * @property metadata Information about the device and environment where the recording took place.
 * @property events The ordered list of [RecordedEvent]s captured during the session.
 */
@Serializable
data class ObservationRecording(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val metadata: SessionMetadata?,
    val events: List<RecordedEvent>
)
