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
 * Represents the various phases of an [ObservationSession] lifecycle.
 */
enum class SessionPhase {
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
 * Describes the health and consistency of an [ObservationSession]'s current understanding.
 */
enum class IntegrityStatus {
    /**
     * All required fields for the objective are resolved and consistent.
     */
    COMPLETE,

    /**
     * Some required fields are missing.
     */
    PARTIAL,

    /**
     * Contradictory evidence exists within the session history.
     */
    CONFLICTING,

    /**
     * Sparse evidence that doesn't meet the minimum requirements for the objective.
     */
    INSUFFICIENT
}

/**
 * A summary of the [ObservationSession]'s integrity.
 */
data class ObservationIntegrity(
    val status: IntegrityStatus,
    val resolvedFields: Set<String>,
    val missingFields: Set<String>,
    val conflictingFields: Set<String>
)

/**
 * Tracks the progress of an [ObservationSession] toward its [ObservationObjective].
 */
data class ObservationProgress(
    val completedRequiredFields: Int,
    val totalRequiredFields: Int,
    val percentComplete: Float,
    val missingFields: Set<String>,
    val isComplete: Boolean
)

/**
 * Describes the current recommendation for the next observation.
 */
enum class GuidanceStatus {
    /**
     * Additional evidence is needed to fulfill the objective.
     */
    CONTINUE_OBSERVING,

    /**
     * All required fields are resolved and consistent.
     */
    OBJECTIVE_COMPLETE,

    /**
     * No evidence has been collected yet.
     */
    INSUFFICIENT_EVIDENCE,

    /**
     * Contradictory evidence requires manual or additional confirmation.
     */
    CONFLICT_REQUIRES_CONFIRMATION
}

/**
 * A recommendation for the next step in an [ObservationSession].
 */
data class ObservationGuidance(
    val status: GuidanceStatus,
    val targetFields: Set<String> = emptySet(),
    val reason: String? = null
)

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
    val state: SessionPhase = SessionPhase.CREATED,
    val objective: ObservationObjective = ObservationObjective.RegisterSpecimen
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

    /**
     * Evaluates the integrity of the session's current understanding relative to its [objective].
     */
    fun evaluateIntegrity(): ObservationIntegrity {
        val resolved = resolveResults()
        val required = objective.requiredFields
        
        val resolvedFields = resolved.keys.filter { field -> 
            resolved[field]?.any { it.value != null } == true 
        }.toSet()
        
        val missingFields = required - resolvedFields
        
        // Detect conflicts: A single recognizer reporting different non-null values in history
        val conflictingFields = recognitionResults.filter { (field, history) ->
            history.groupBy { it.recognizer }.any { (_, recognizerHistory) ->
                val uniqueValues = recognizerHistory.mapNotNull { it.value }.distinct()
                uniqueValues.size > 1
            }
        }.keys.toSet()

        val status = when {
            conflictingFields.intersect(required).isNotEmpty() -> IntegrityStatus.CONFLICTING
            missingFields.isEmpty() -> IntegrityStatus.COMPLETE
            resolvedFields.isEmpty() -> IntegrityStatus.INSUFFICIENT
            else -> IntegrityStatus.PARTIAL
        }

        return ObservationIntegrity(
            status = status,
            resolvedFields = resolvedFields,
            missingFields = missingFields,
            conflictingFields = conflictingFields
        )
    }

    /**
     * Evaluates the progress of the session relative to its [objective].
     * Progress is calculated based only on [ObservationObjective.requiredFields].
     */
    fun evaluateProgress(): ObservationProgress {
        val resolved = resolveResults()
        val required = objective.requiredFields
        
        val completedRequiredFields = required.count { field ->
            resolved[field]?.any { it.value != null } == true
        }
        
        val totalRequiredFields = required.size
        val percentComplete = if (totalRequiredFields > 0) {
            completedRequiredFields.toFloat() / totalRequiredFields
        } else {
            1.0f
        }
        
        val resolvedFields = resolved.keys.filter { field -> 
            resolved[field]?.any { it.value != null } == true 
        }.toSet()
        val missingFields = required - resolvedFields

        return ObservationProgress(
            completedRequiredFields = completedRequiredFields,
            totalRequiredFields = totalRequiredFields,
            percentComplete = percentComplete,
            missingFields = missingFields,
            isComplete = completedRequiredFields == totalRequiredFields
        )
    }

    /**
     * Recommends the next best observation to make progress toward the [objective].
     */
    fun nextObservation(): ObservationGuidance {
        val integrity = evaluateIntegrity()
        if (integrity.status == IntegrityStatus.CONFLICTING) {
            val relevantConflicts = integrity.conflictingFields.intersect(objective.requiredFields)
            if (relevantConflicts.isNotEmpty()) {
                return ObservationGuidance(
                    status = GuidanceStatus.CONFLICT_REQUIRES_CONFIRMATION,
                    targetFields = relevantConflicts,
                    reason = "Conflicting evidence in required fields"
                )
            }
        }

        val progress = evaluateProgress()
        if (progress.isComplete) {
            return ObservationGuidance(GuidanceStatus.OBJECTIVE_COMPLETE)
        }

        if (progress.completedRequiredFields == 0) {
            return ObservationGuidance(
                status = GuidanceStatus.INSUFFICIENT_EVIDENCE,
                targetFields = objective.requiredFields,
                reason = "No required fields resolved"
            )
        }

        return ObservationGuidance(
            status = GuidanceStatus.CONTINUE_OBSERVING,
            targetFields = progress.missingFields,
            reason = "Awaiting required fields"
        )
    }
}
