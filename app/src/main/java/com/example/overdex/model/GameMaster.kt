package com.example.overdex.model

import kotlinx.serialization.Serializable

@Serializable
data class GameMaster(
    val pokemon: List<GameMasterPokemon>,
    val moves: List<GameMasterMove>
)

@Serializable
data class BaseStats(
    val atk: Int,
    val def: Int,
    val hp: Int
)

@Serializable
data class GameMasterPokemon(
    val dex: Int,
    val speciesId: String,
    val speciesName: String,
    val fastMoves: List<String> = emptyList(),
    val chargedMoves: List<String> = emptyList(),
    val baseStats: BaseStats? = null
)

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