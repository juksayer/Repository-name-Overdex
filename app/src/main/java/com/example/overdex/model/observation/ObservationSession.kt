package com.example.overdex.model.observation

import java.util.UUID

enum class SessionSource {
    SCREENSHOT,
    SCROLLING_SCREENSHOT,
    LIVE_CAPTURE,
    MANUAL_ENTRY,
    DEBUG
}

/**
 * Represents the various phases of an [ObservationSession].
 */
enum class ObservationSessionState {
    /**
     * The session has been initialized but observation hasn't started yet.
     */
    CREATED,

    /**
     * The session is actively consuming visual evidence.
     */
    ACTIVE,

    /**
     * The session has finalized its observations and results.
     */
    COMPLETED,

    /**
     * The session was aborted before completion.
     */
    CANCELLED
}

/**
 * A passive data model representing everything Overdex has observed about a specimen 
 * during a single observation attempt.
 *
 * An [ObservationSession] is created at the start of an observation attempt and 
 * progresses through its lifecycle until it is completed or cancelled.
 */
data class ObservationSession(
    val sessionId: String = UUID.randomUUID().toString().takeLast(8),
    val source: SessionSource = SessionSource.SCREENSHOT,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val observations: List<CaptureObservation> = emptyList(),
    val recognitionResults: Map<String, List<RecognitionResult<*>>> = emptyMap(),
    val assessment: RegistrationAssessment? = null,
    val state: ObservationSessionState = ObservationSessionState.CREATED
)
