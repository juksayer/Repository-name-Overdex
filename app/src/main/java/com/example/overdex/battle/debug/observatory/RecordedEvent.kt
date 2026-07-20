package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * A generic container for any piece of evidence captured during an observation session.
 * 
 * [RecordedEvent]s preserve the exact state and timing of an observation, allowing
 * for deterministic playback and analysis of the observation pipeline.
 * 
 * @property sessionId The ID of the session this event belongs to.
 * @property sequenceNumber A monotonic counter for events within the session.
 * @property timestamp The absolute system time when the event occurred.
 * @property relativeTimestamp The time in ms since the session started.
 * @property sourceType The category of evidence (e.g., VISION, DECISION).
 * @property payload The specific data associated with the event.
 */
@Serializable
data class RecordedEvent(
    val sessionId: String,
    val sequenceNumber: Long,
    val timestamp: Long,
    val relativeTimestamp: Long,
    val sourceType: EvidenceSourceType,
    val payload: RecordedPayload
)
