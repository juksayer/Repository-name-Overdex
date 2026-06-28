package com.example.overdex.model

import java.util.UUID

enum class BattleEventType {
    BATTLE_STARTED,
    POKEMON_OBSERVED,
    POKEMON_ACTIVE,
    ENERGY_UPDATED,
    POKEMON_SWITCHED,
    POKEMON_FAINTED,
    CHARGED_MOVE_THROWN,
    SHIELD_USED,
    BATTLE_ENDED
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
    val species: String? = null,
    val value: Int? = null,
    val message: String? = null,
    val confidence: Confidence = Confidence(ConfidenceLevel.OBSERVED)
)
