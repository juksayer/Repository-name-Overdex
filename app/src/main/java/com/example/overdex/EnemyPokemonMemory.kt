package com.example.overdex

import com.example.overdex.model.Confidence
import com.example.overdex.model.ConfidenceLevel

data class EnemyPokemonMemory(
    val species: String,
    var alive: Boolean = true,
    var estimatedEnergy: Int = 0,
    var timesSeen: Int = 0,
    val fastMoves: MutableSet<String> = mutableSetOf(),
    val chargedMoves: MutableSet<String> = mutableSetOf(),
    var isActive: Boolean = false,
    val speciesConfidence: Confidence = Confidence(ConfidenceLevel.OBSERVED),
    var energyConfidence: Confidence = Confidence(ConfidenceLevel.INFERRED)
)
