package com.example.overdex.model

import java.util.UUID

enum class BattleEventType {
    BATTLE_STARTED,
    BATTLE_ENDED,
    POKEMON_IDENTIFIED,  // Replaces POKEMON_OBSERVED / ENEMY_IDENTIFIED
    POKEMON_SWITCHED,    // Replaces POKEMON_ACTIVE / PLAYER_SWAPPED
    POKEMON_FAINTED,
    CHARGED_MOVE_THROWN,
    SHIELD_USED,
    ENERGY_UPDATED
}

enum class BattleActor {
    PLAYER,
    ENEMY,
    SYSTEM
}

data class BattleEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: BattleEventType,
    val actor: BattleActor = BattleActor.SYSTEM,
    val pokemonId: Int? = null, // Use Stable IDs (#006) instead of Strings
    val value: Int? = null,
    val message: String? = null,
    val confidence: Confidence = Confidence(ConfidenceLevel.OBSERVED)
)
