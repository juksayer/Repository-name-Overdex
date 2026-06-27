package com.example.overdex.data.matchup

import com.example.overdex.EnemyPokemonMemory
import com.example.overdex.model.*

object MatchupEngine {

    fun analyze(
        player: Pokemon,
        enemy: Pokemon,
        enemyMemory: EnemyPokemonMemory
    ): MatchupAnalysis {
        
        // 1. Analyze Player Moves vs Enemy Types
        val playerFastMoves = player.fastMoves.map { move ->
            MoveMatchup(move.name, move.type, TypeEffectivenessEngine.getEffectiveness(move.type, enemy.types))
        }
        val playerChargedMoves = player.chargedMoves.map { move ->
            MoveMatchup(move.name, move.type, TypeEffectivenessEngine.getEffectiveness(move.type, enemy.types))
        }

        // 2. Analyze Observed Enemy Moves vs Player Types
        val observedFastMoves = enemyMemory.fastMoves.mapNotNull { moveName ->
            enemy.fastMoves.find { it.name == moveName } ?: enemy.chargedMoves.find { it.name == moveName } // Safety lookup
        }
        val observedChargedMoves = enemyMemory.chargedMoves.mapNotNull { moveName ->
            enemy.chargedMoves.find { it.name == moveName }
        }

        val enemyFastMoveMatchup = observedFastMoves.firstOrNull()?.let { move ->
            MoveMatchup(move.name, move.type, TypeEffectivenessEngine.getEffectiveness(move.type, player.types))
        }
        
        val enemyChargedMoveMatchups = observedChargedMoves.map { move ->
            MoveMatchup(move.name, move.type, TypeEffectivenessEngine.getEffectiveness(move.type, player.types))
        }

        // 3. Determine Unknowns
        val unknowns = mutableListOf<String>()
        if (enemyMemory.fastMoves.isEmpty()) unknowns.add("Enemy Fast Move")
        if (enemyMemory.chargedMoves.isEmpty()) unknowns.add("Enemy Charged Moves")

        // 4. Calculate Advantage Levels (Heuristic)
        val playerAdvantage = when {
            playerChargedMoves.any { it.effectiveness == Effectiveness.SUPER_EFFECTIVE } -> AdvantageLevel.HIGH
            playerFastMoves.any { it.effectiveness == Effectiveness.SUPER_EFFECTIVE } -> AdvantageLevel.MEDIUM
            else -> AdvantageLevel.NEUTRAL
        }

        // Check if ANY enemy move (from DB) is dangerous
        val possibleEnemyMoves = enemy.fastMoves + enemy.chargedMoves
        val anyDangerousMove = possibleEnemyMoves.any { move ->
            TypeEffectivenessEngine.getEffectiveness(move.type, player.types) == Effectiveness.SUPER_EFFECTIVE
        }

        val enemyThreatLevel = when {
            enemyChargedMoveMatchups.any { it.effectiveness == Effectiveness.SUPER_EFFECTIVE } -> AdvantageLevel.HIGH
            anyDangerousMove -> AdvantageLevel.MEDIUM
            else -> AdvantageLevel.LOW
        }

        return MatchupAnalysis(
            playerSpecies = player.name,
            enemySpecies = enemy.name,
            enemyThreatLevel = enemyThreatLevel,
            playerAdvantage = playerAdvantage,
            enemyFastMoveMatchup = enemyFastMoveMatchup,
            enemyChargedMoveMatchups = enemyChargedMoveMatchups,
            playerFastMoveMatchups = playerFastMoves,
            playerChargedMoveMatchups = playerChargedMoves,
            unknownRelationships = unknowns
        )
    }
}
