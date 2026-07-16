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
) {
    /**
     * Resolves the current best understanding of the specimen based on all accumulated evidence.
     * Applies deterministic rules: Higher Confidence Wins, Missing Never Wins, and Equal Confidence
     * preserves the existing observation.
     */
    fun resolveResults(): Map<String, List<RecognitionResult<*>>> {
        return recognitionResults.mapValues { (_, results) ->
            results.groupBy { it.recognizer }.mapNotNull { (_, recognizerResults) ->
                var currentBest: RecognitionResult<*>? = null

                for (result in recognizerResults) {
                    // Rule: Missing Never Wins (A missing/null observation must never replace an existing value)
                    if (result.value == null) continue

                    if (currentBest == null) {
                        // Rule: New Information Wins
                        currentBest = result
                    } else if (result.confidence > currentBest.confidence) {
                        // Rule: Higher Confidence Wins
                        currentBest = result
                    }
                    // Rule: Equal Confidence (If confidence is equal, preserve existing. Do not oscillate.)
                    // This is handled by only updating if confidence is strictly greater.
                }

                currentBest
            }
        }
    }
}
