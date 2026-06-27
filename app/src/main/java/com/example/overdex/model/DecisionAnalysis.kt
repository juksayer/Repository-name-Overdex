package com.example.overdex.model

enum class RecommendedAction {
    STAY_AND_FIGHT,
    SWITCH_NOW,
    FARM_ENERGY,
    SHIELD_LIKELY_REQUIRED,
    UNKNOWN
}

enum class StrategicPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    NONE
}

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
