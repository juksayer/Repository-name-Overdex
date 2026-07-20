package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * The root container for the static game data loaded from external sources.
 * 
 * This represents the "Raw Reference Knowledge" as defined by external game data
 * before it is normalized into the Overdex domain model.
 */
@Serializable
data class GameMaster(
    val pokemon: List<GameMasterPokemon>,
    val moves: List<GameMasterMove>
)

/**
 * Raw base stats for a Pokémon as defined in the game files.
 */
@Serializable
data class BaseStats(
    val atk: Int,
    val def: Int,
    val hp: Int
)

/**
 * Raw species data entry in the GameMaster.
 */
@Serializable
data class GameMasterPokemon(
    val dex: Int,
    val speciesId: String,
    val speciesName: String,
    val types: List<String> = emptyList(),
    val fastMoves: List<String> = emptyList(),
    val chargedMoves: List<String> = emptyList(),
    val baseStats: BaseStats? = null
)

/**
 * Raw move data entry in the GameMaster.
 */
@Serializable
data class GameMasterMove(
    val moveId: String,
    val name: String,
    val type: String,
    val power: Int,
    val energy: Int,
    val energyGain: Int,
    val turns: Int = 1
)
