package com.example.overdex.model.observation

enum class RegistrationAction {
    REGISTER,
    SELECT_SPECIES,
    CAPTURE_SECOND_SCREEN,
    VERIFY_CP,
    NONE
}

enum class ObservationStatus {
    OBSERVED,   // Raw text extracted but not validated
    RECOGNIZED, // Mapped to a known Species/Move
    CONFIRMED,  // Manually selected or extremely high confidence
    CONFLICT,   // Contradictory evidence (e.g. name vs family)
    MISSING     // Not found in the current capture
}

data class CandidateSpecies(
    val id: Int,
    val name: String,
    val confidence: Float,
    val reasoning: String
)

data class RegistrationAssessment(
    val confidence: Float,
    val candidates: List<CandidateSpecies> = emptyList(),
    val missingObservations: List<String> = emptyList(),
    val conflictingObservations: List<String> = emptyList(),
    val recommendedAction: RegistrationAction = RegistrationAction.NONE
)
