package com.example.overdex.model

import java.util.UUID

enum class BattleEventType {
    BATTLE_STARTED,
    POKEMON_OBSERVED,
    POKEMON_ACTIVE,
    ENERGY_UPDATED,
    POKEMON_SWITCHED,
    POKEMON_FAINTED,
    BATTLE_ENDED
}

data class BattleEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: BattleEventType,
    val species: String? = null,
    val value: Int? = null,
    val message: String? = null,
    val confidence: Confidence = Confidence(ConfidenceLevel.OBSERVED)
)
