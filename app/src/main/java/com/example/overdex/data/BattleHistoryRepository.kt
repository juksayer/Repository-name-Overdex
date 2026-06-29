package com.example.overdex.data

import com.example.overdex.model.*
import java.util.UUID

/**
 * Placeholder repository for battle history.
 * Serves several ArchivedBattle objects with believable timelines.
 */
class BattleHistoryRepository {
    
    private val placeholderArchives = listOf(
        // Victory against Swampert Lead
        ArchivedBattle(
            id = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis() - 3600000, // 1 hour ago
            endTime = System.currentTimeMillis() - 3600000 + 120000,
            timeline = listOf(
                BattleEvent(type = BattleEventType.BATTLE_STARTED, actor = BattleActor.SYSTEM),
                BattleEvent(type = BattleEventType.POKEMON_IDENTIFIED, actor = BattleActor.ENEMY, pokemonId = 260),
                BattleEvent(type = BattleEventType.ENERGY_UPDATED, actor = BattleActor.ENEMY, pokemonId = 260, value = 45),
                BattleEvent(type = BattleEventType.CHARGED_MOVE_THROWN, actor = BattleActor.ENEMY, pokemonId = 260),
                BattleEvent(type = BattleEventType.POKEMON_FAINTED, actor = BattleActor.ENEMY, pokemonId = 260),
                BattleEvent(type = BattleEventType.BATTLE_ENDED, actor = BattleActor.SYSTEM)
            )
        ),
        // Defeat against Azumarill Lead
        ArchivedBattle(
            id = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis() - 86400000, // Yesterday
            endTime = System.currentTimeMillis() - 86400000 + 150000,
            timeline = listOf(
                BattleEvent(type = BattleEventType.BATTLE_STARTED, actor = BattleActor.SYSTEM),
                BattleEvent(type = BattleEventType.POKEMON_IDENTIFIED, actor = BattleActor.ENEMY, pokemonId = 184),
                BattleEvent(type = BattleEventType.POKEMON_SWITCHED, actor = BattleActor.ENEMY, pokemonId = 663),
                BattleEvent(type = BattleEventType.CHARGED_MOVE_THROWN, actor = BattleActor.ENEMY, pokemonId = 663),
                BattleEvent(type = BattleEventType.BATTLE_ENDED, actor = BattleActor.SYSTEM)
            )
        ),
        // Victory against Talonflame Lead
        ArchivedBattle(
            id = UUID.randomUUID().toString(),
            startTime = System.currentTimeMillis() - 172800000, // 2 days ago
            endTime = System.currentTimeMillis() - 172800000 + 90000,
            timeline = listOf(
                BattleEvent(type = BattleEventType.BATTLE_STARTED, actor = BattleActor.SYSTEM),
                BattleEvent(type = BattleEventType.POKEMON_IDENTIFIED, actor = BattleActor.ENEMY, pokemonId = 663),
                BattleEvent(type = BattleEventType.CHARGED_MOVE_THROWN, actor = BattleActor.ENEMY, pokemonId = 663),
                BattleEvent(type = BattleEventType.POKEMON_FAINTED, actor = BattleActor.ENEMY, pokemonId = 663),
                BattleEvent(type = BattleEventType.BATTLE_ENDED, actor = BattleActor.SYSTEM)
            )
        )
    )

    fun getHistory(): List<ArchivedBattle> = placeholderArchives.sortedByDescending { it.startTime }
}
