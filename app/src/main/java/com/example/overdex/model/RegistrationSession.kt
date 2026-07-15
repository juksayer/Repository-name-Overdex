package com.example.overdex.model

import com.example.overdex.model.observation.Observation
import com.example.overdex.model.observation.CombatPowerObservation
import com.example.overdex.model.observation.ShadowStatusObservation
import com.example.overdex.model.observation.FastMoveObservation
import com.example.overdex.model.observation.ChargedMoveObservation
import com.example.overdex.model.observation.EvolutionFamilyObservation
import java.util.UUID

/**
 * A Registration Session groups one or more observations into a single Specimen
 * before it is committed to the Roster.
 */
data class RegistrationSession(
    val id: String = UUID.randomUUID().toString(),
    val observations: List<Observation> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false
) {
    /**
     * Adds a new observation to the session.
     */
    fun addObservation(observation: Observation): RegistrationSession {
        return copy(
            observations = observations + observation,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Builds a Specimen from the current observations when sufficient
     * information is available.
     *
     * Phase 1 establishes the registration architecture.
     * Future phases will merge observations from screenshots,
     * live observation, manual entry, and other registration sources.
     */
    fun buildSpecimen(speciesId: Int): OwnedPokemon {
        var cp: Int? = null
        var isShadow = false
        var fastMove: String? = null
        var chargedMove1: String? = null
        var chargedMove2: String? = null
        var evolutionFamily: String? = null

        // Group by type and pick highest confidence for each attribute
        observations.forEach { obs ->
            when (obs) {
                is CombatPowerObservation -> {
                    if (cp == null || obs.confidence.score > 0.5f) {
                        cp = obs.cp
                    }
                }
                is ShadowStatusObservation -> {
                    if (obs.isShadow) isShadow = true
                }
                is FastMoveObservation -> {
                    fastMove = obs.moveName
                }
                is ChargedMoveObservation -> {
                    if (chargedMove1 == null) {
                        chargedMove1 = obs.moveName
                    } else if (chargedMove1 != obs.moveName) {
                        chargedMove2 = obs.moveName
                    }
                }
                is EvolutionFamilyObservation -> {
                    evolutionFamily = obs.familySpecies
                }
                else -> {}
            }
        }

        return OwnedPokemon(
            speciesId = speciesId,
            cp = cp,
            isShadow = isShadow,
            fastMove = fastMove,
            chargedMove1 = chargedMove1,
            chargedMove2 = chargedMove2,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
