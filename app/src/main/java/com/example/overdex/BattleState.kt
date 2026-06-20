package com.example.overdex

data class BattleState(
    val enemyPokemon: String?,
    val enemyFastMove: String?,
    val enemyChargedMoves: List<String>,
    val enemyEnergy: Int,
    val enemyRemainingPokemon: Int
)val observedEnemyPokemon: MutableList<String> = mutableListOf()