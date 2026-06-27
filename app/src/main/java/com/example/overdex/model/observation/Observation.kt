package com.example.overdex.model.observation

import com.example.overdex.model.Confidence

sealed class Observation {
    abstract val timestamp: Long
    abstract val source: ObservationSource
    abstract val confidence: Confidence
}

data class PokemonNameObservation(
    val species: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val confidence: Confidence
) : Observation()

data class FastMoveObservation(
    val species: String,
    val moveName: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val confidence: Confidence
) : Observation()

data class ChargedMoveObservation(
    val species: String,
    val moveName: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val confidence: Confidence
) : Observation()
