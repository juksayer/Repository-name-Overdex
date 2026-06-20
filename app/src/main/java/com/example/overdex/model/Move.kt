package com.example.overdex.model

import kotlinx.serialization.Serializable

@Serializable
data class Move(
    val name: String,
    val type: PokemonType,
    val damage: Int,
    val energy: Int, // For Fast: Energy Gain. For Charged: Energy Cost.
    val isFast: Boolean,
    val turns: Int? = null, // PvP turns (1 turn = 0.5s). Only for Fast moves.
) {
    val dpe: Double? // Damage Per Energy
        get() = if (!isFast && energy > 0) damage.toDouble() / energy else null
}
