package com.example.overdex

/**
 * A snapshot of the current visible state of a battle.
 */
data class BattleState(
    val myPokemon: String?,
    val enemyPokemon: String?,

    val myFastMove: String?,
    val enemyFastMove: String?,

    val enemyChargedMoves: List<String>,

    val myEnergy: Int,
    val enemyEnergy: Int,

    val myShields: Int,
    val enemyShields: Int,

    val myRemainingPokemon: Int,
    val enemyRemainingPokemon: Int
)