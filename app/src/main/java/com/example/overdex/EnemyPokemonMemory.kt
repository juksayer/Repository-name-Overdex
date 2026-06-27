package com.example.overdex

data class EnemyPokemonMemory(
    val species: String,
    var alive: Boolean = true,
    var estimatedEnergy: Int = 0,
    var timesSeen: Int = 0,
    val fastMoves: MutableSet<String> = mutableSetOf(),
    val chargedMoves: MutableSet<String> = mutableSetOf(),
    var isActive: Boolean = false
)