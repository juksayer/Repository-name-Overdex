package com.example.overdex.model.observation

import java.util.UUID

enum class SessionSource {
    SCREENSHOT,
    SCROLLING_SCREENSHOT,
    LIVE_CAPTURE,
    MANUAL_ENTRY,
    DEBUG
}

enum class SessionCompletionState {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

/**
 * A passive data model representing everything Overdex has observed about a specimen 
 * during a single observation attempt.
 */
data class ObservationSession(
    val sessionId: String = UUID.randomUUID().toString().takeLast(8),
    val source: SessionSource = SessionSource.SCREENSHOT,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val observations: List<CaptureObservation> = emptyList(),
    val recognitionResults: Map<String, List<RecognitionResult<*>>> = emptyMap(),
    val assessment: RegistrationAssessment? = null,
    val completionState: SessionCompletionState = SessionCompletionState.ACTIVE
)
