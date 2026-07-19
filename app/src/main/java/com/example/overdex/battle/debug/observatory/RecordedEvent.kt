package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * A generic container for any piece of evidence captured during an Observation Session.
 * Preserves the exact state and timing of the observation.
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
