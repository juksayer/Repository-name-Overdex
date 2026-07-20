package com.example.overdex.model

/**
 * Actions recommended by the Intelligence layer during battle.
 */
enum class RecommendedAction {
    /** Optimal strategy is to maintain the current matchup. */
    STAY_AND_FIGHT,
    /** High-priority recommendation to switch Pokémon immediately. */
    SWITCH_NOW,
    /** Opportunity to gain energy without taking significant damage. */
    FARM_ENERGY,
    /** Incoming move is likely lethal; shield usage is recommended. */
    SHIELD_LIKELY_REQUIRED,
    /** Insufficient data to make a recommendation. */
    UNKNOWN
}

/**
 * Priority levels for recommended tactical actions.
 */
enum class StrategicPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    NONE
}

/**
 * A tactical assessment of the current battle state.
 */
data class DecisionAnalysis(
    val recommendedAction: RecommendedAction,
    val actionPriority: StrategicPriority,
    
    val isEnemyThreatening: Boolean,
    val isPlayerThreatening: Boolean,
    
    val shieldRecommended: Boolean,
    val farmOpportunityAvailable: Boolean,
    
    val confidenceInDecision: Float,
    val reasoning: String
)
