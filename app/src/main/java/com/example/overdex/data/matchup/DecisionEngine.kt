package com.example.overdex.data.matchup

import com.example.overdex.model.*

object DecisionEngine {

    fun analyze(matchup: MatchupAnalysis): DecisionAnalysis {
        
        val isEnemyThreatening = matchup.enemyThreatLevel == AdvantageLevel.HIGH || 
                                 matchup.enemyThreatLevel == AdvantageLevel.MEDIUM
                                 
        val isPlayerThreatening = matchup.playerAdvantage == AdvantageLevel.HIGH || 
                                  matchup.playerAdvantage == AdvantageLevel.MEDIUM

        // Basic Heuristics for prototype
        val recommendedAction: RecommendedAction
        val priority: StrategicPriority
        val reasoning: String
        
        when {
            matchup.enemyThreatLevel == AdvantageLevel.HIGH && !isPlayerThreatening -> {
                recommendedAction = RecommendedAction.SWITCH_NOW
                priority = StrategicPriority.CRITICAL
                reasoning = "Enemy has major type advantage or super-effective moves."
            }
            isEnemyThreatening && isPlayerThreatening -> {
                recommendedAction = RecommendedAction.STAY_AND_FIGHT
                priority = StrategicPriority.MEDIUM
                reasoning = "Neutral/Dual-threat matchup. Play carefully."
            }
            !isEnemyThreatening && isPlayerThreatening -> {
                recommendedAction = RecommendedAction.FARM_ENERGY
                priority = StrategicPriority.HIGH
                reasoning = "Player has dominance. Ideal for energy farming."
            }
            else -> {
                recommendedAction = RecommendedAction.STAY_AND_FIGHT
                priority = StrategicPriority.LOW
                reasoning = "No immediate threats or major opportunities identified."
            }
        }

        val shieldRecommended = matchup.enemyChargedMoveMatchups.any { it.effectiveness == Effectiveness.SUPER_EFFECTIVE }

        return DecisionAnalysis(
            recommendedAction = recommendedAction,
            actionPriority = priority,
            isEnemyThreatening = isEnemyThreatening,
            isPlayerThreatening = isPlayerThreatening,
            shieldRecommended = shieldRecommended,
            farmOpportunityAvailable = (!isEnemyThreatening && isPlayerThreatening),
            confidenceInDecision = if (matchup.unknownRelationships.isEmpty()) 0.9f else 0.6f,
            reasoning = reasoning
        )
    }
}
