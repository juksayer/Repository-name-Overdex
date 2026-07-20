package com.example.overdex.model.observation

/**
 * Defines the goal of an observation attempt and the requirements needed to satisfy it.
 * 
 * Objectives are used by the observation engine to provide guidance to the trainer 
 * and determine when a session has reached sufficient completion.
 * 
 * @property label A user-friendly name for the objective.
 */
sealed class ObservationObjective(val label: String) {
    /** The set of field identifiers that must have evidence for the objective to be "Complete". */
    abstract val requiredFields: Set<String>
    /** The set of field identifiers that may be captured but are not strictly necessary. */
    abstract val optionalFields: Set<String>

    /**
     * Objective for full data collection when registering a new specimen in the collection.
     */
    object RegisterSpecimen : ObservationObjective("Register Specimen") {
        override val requiredFields = setOf(
            "SpeciesName",
            "CombatPower",
            "FastMoveRow",
            "ChargedMoveRowA"
        )
        override val optionalFields = setOf(
            "CandyPanel",
            "ChargedMoveRowB"
        )
    }

    /**
     * Objective for quick identification of a specimen, focusing on essential combat stats.
     */
    object IdentifySpecimen : ObservationObjective("Identify Specimen") {
        override val requiredFields = setOf(
            "SpeciesName",
            "CombatPower"
        )
        override val optionalFields = setOf(
            "CandyPanel",
            "FastMoveRow",
            "SummaryFastMove"
        )
    }
}
