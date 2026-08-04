package com.example.overdex.model.observation

import com.example.overdex.model.Confidence

/**
 * Represents a factual record of something detected on the screen during a battle or session.
 * 
 * Observations are point-in-time, immutable facts produced by recognizers. They do not
 * contain inference or strategy—they only record what was seen, who saw it, and with what confidence.
 */
sealed class Observation {
    /** The system time in milliseconds when the observation was recorded. */
    abstract val timestamp: Long
    /** The source from which this observation originated (e.g., SCREEN_CAPTURE, REPLAY). */
    abstract val source: ObservationSource
    /** A unique identifier for the component that produced this observation. */
    abstract val observerId: String
    /** The system's certainty about this observation. */
    abstract val confidence: Confidence
}

/**
 * An observation of a specific Pokémon species name on the screen.
 */
data class PokemonNameObservation(
    val species: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val observerId: String,
    override val confidence: Confidence
) : Observation()

/**
 * An observation of a fast move being used or visible in the move set.
 */
data class FastMoveObservation(
    val species: String,
    val moveName: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val observerId: String,
    override val confidence: Confidence
) : Observation()

/**
 * An observation of a charged move being visible or prepared.
 */
data class ChargedMoveObservation(
    val species: String,
    val moveName: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val observerId: String,
    override val confidence: Confidence
) : Observation()

/**
 * An observation of a Pokémon's Combat Power (CP) value.
 */
data class CombatPowerObservation(
    val cp: Int,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val observerId: String,
    override val confidence: Confidence
) : Observation()

/**
 * An observation regarding whether a Pokémon has shadow status (e.g., shadow bonus or purple flames).
 */
data class ShadowStatusObservation(
    val isShadow: Boolean,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val observerId: String,
    override val confidence: Confidence
) : Observation()

/**
 * An observation identifying the evolution family of a Pokémon, typically from the candy panel.
 */
data class EvolutionFamilyObservation(
    val familySpecies: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val observerId: String,
    override val confidence: Confidence
) : Observation()

/**
 * An observation of a countdown timer (e.g., "3", "2", "1", "GO").
 */
data class CountdownObservation(
    val value: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: ObservationSource,
    override val observerId: String,
    override val confidence: Confidence
) : Observation()
