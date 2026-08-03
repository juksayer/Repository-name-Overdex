package com.example.overdex.model

import com.example.overdex.model.observation.ChargedMoveObservation
import com.example.overdex.model.observation.CombatPowerObservation
import com.example.overdex.model.observation.DefaultObservationResolver
import com.example.overdex.model.observation.FastMoveObservation
import com.example.overdex.model.observation.ObservationResolver
import com.example.overdex.model.observation.ShadowStatusObservation
import com.example.overdex.model.observation.Observation as DomainObservation
import java.util.UUID

/**
 * A grouping of [DomainObservation] facts captured for a specific specimen 
 * before it is committed to the collection.
 * 
 * The session persists throughout an observation attempt, allowing multiple 
 * captures to contribute evidence toward a single resolved specimen identity.
 * 
 * @property id Unique identifier for the session.
 * @property observations Chronological history of facts captured in this session.
 * @property createdAt Timestamp of session initiation.
 * @property isComplete True if the session has reached its objective.
 */
data class RegistrationSession(
    val id: String = UUID.randomUUID().toString(),
    val observations: List<DomainObservation> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false
) {
    /**
     * Adds a new observation to the session.
     */
    fun addObservation(observation: DomainObservation): RegistrationSession {
        return copy(
            observations = observations + observation,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Builds a Specimen from the current observations when sufficient
     * information is available.
     *
     * @param speciesId The canonical species identifier.
     * @param resolver The policy used to resolve competing observations.
     */
    fun buildSpecimen(
        speciesId: Int,
        resolver: ObservationResolver = DefaultObservationResolver()
    ): OwnedPokemon {
        // Resolve best understanding per field
        val grouped = observations.groupBy { it::class.java.simpleName }
        
        val cpObs = observations.filterIsInstance<CombatPowerObservation>().let { resolver.resolve(it) } as? CombatPowerObservation
        val shadowObs = observations.filterIsInstance<ShadowStatusObservation>().let { resolver.resolve(it) } as? ShadowStatusObservation
        val fastMoveObs = observations.filterIsInstance<FastMoveObservation>().let { resolver.resolve(it) } as? FastMoveObservation
        
        val chargedMoves = observations.filterIsInstance<ChargedMoveObservation>()
        // Simple resolution for two charged moves: pick two highest confidence distinct moves
        val resolvedCharged = chargedMoves.groupBy { it.moveName }
            .mapNotNull { (_, obsList) -> resolver.resolve(obsList) as? ChargedMoveObservation }
            .sortedByDescending { it.confidence.score }
            .take(2)

        return OwnedPokemon(
            speciesId = speciesId,
            cp = cpObs?.cp,
            isShadow = shadowObs?.isShadow ?: false,
            fastMove = fastMoveObs?.moveName,
            chargedMove1 = resolvedCharged.getOrNull(0)?.moveName,
            chargedMove2 = resolvedCharged.getOrNull(1)?.moveName,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
