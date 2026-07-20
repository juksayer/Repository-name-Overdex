package com.example.overdex.model.observation

/**
 * Recommended next step for the trainer to resolve a registration session.
 */
enum class RegistrationAction {
    /** The assessment is complete and the specimen can be registered. */
    REGISTER,
    /** The identified species is ambiguous; user must select from a list. */
    SELECT_SPECIES,
    /** Essential fields are missing; capture a different part of the screen. */
    CAPTURE_SECOND_SCREEN,
    /** CP recognition is low confidence; manual verification needed. */
    VERIFY_CP,
    /** No recommendation can be made yet. */
    NONE
}

/**
 * The status of a specific field within an [ObservationSession].
 */
enum class ObservationStatus {
    /** Raw text was extracted but has not been validated against the GameMaster. */
    OBSERVED,
    /** The observation has been successfully mapped to a known entity (e.g., a specific Move). */
    RECOGNIZED,
    /** The value has been confirmed by the user or has extremely high confidence. */
    CONFIRMED,
    /** Multiple observations provide contradictory information. */
    CONFLICT,
    /** No evidence for this field was found in the session. */
    MISSING
}

/**
 * A species candidate identified by the registration engine.
 */
data class CandidateSpecies(
    val id: Int,
    val name: String,
    val confidence: Float,
    val reasoning: String
)

/**
 * The final outcome of a registration evaluation.
 * 
 * @property confidence Overall certainty of the assessment from 0.0 to 1.0.
 * @property candidates A list of potential species matches.
 * @property missingObservations List of required fields that lack evidence.
 * @property conflictingObservations List of fields with contradictory evidence.
 * @property recommendedAction The next step suggested by the engine.
 */
data class RegistrationAssessment(
    val confidence: Float,
    val candidates: List<CandidateSpecies> = emptyList(),
    val missingObservations: List<String> = emptyList(),
    val conflictingObservations: List<String> = emptyList(),
    val recommendedAction: RegistrationAction = RegistrationAction.NONE
)
