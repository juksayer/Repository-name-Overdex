package com.example.overdex.model.observation

/**
 * Defines the goal of an observation attempt and the requirements needed to satisfy it.
 */
sealed class ObservationObjective(val label: String) {
    abstract val requiredFields: Set<String>

    /**
     * Objective: Full data collection for registering a new specimen.
     */
    object RegisterSpecimen : ObservationObjective("Register Specimen") {
        override val requiredFields = setOf(
            "SpeciesName",
            "CombatPower",
            "FastMoveRow",
            "ChargedMoveRowA"
        )
    }

    /**
     * Objective: Quick identification of a specimen.
     */
    object IdentifySpecimen : ObservationObjective("Identify Specimen") {
        override val requiredFields = setOf(
            "SpeciesName",
            "CombatPower"
        )
    }
}
