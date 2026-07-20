package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * Represents a Pokémon move and its competitive statistics.
 * 
 * @property name The human-readable name of the move (e.g., "Thunderbolt").
 * @property type The elemental type of the move.
 * @property damage The raw power/damage of the move in battle.
 * @property energy For Fast moves: energy generated per use. For Charged moves: energy cost to fire.
 * @property isFast True if this is a fast move, false if it is a charged move.
 * @property turns The number of PvP turns (0.5s units) required for a fast move.
 */
@Serializable
data class Move(
    val name: String,
    val type: PokemonType,
    val damage: Int,
    val energy: Int, // For Fast: Energy Gain. For Charged: Energy Cost.
    val isFast: Boolean,
    val turns: Int? = null, // PvP turns (1 turn = 0.5s). Only for Fast moves.
) {
    /**
     * Damage Per Energy (DPE) ratio. Only applicable for charged moves.
     */
    val dpe: Double? // Damage Per Energy
        get() = if (!isFast && energy > 0) damage.toDouble() / energy else null
}
