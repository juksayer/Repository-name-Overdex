package com.example.overdex.model

/**
 * Qualitative categories for move effectiveness.
 */
enum class Effectiveness {
    /** 1.6x or more damage. */
    SUPER_EFFECTIVE,
    /** 1.0x damage. */
    NEUTRAL,
    /** 0.625x or less damage. */
    NOT_VERY_EFFECTIVE,
    /** 0.39x or less damage. */
    IMMUNE,
    /** Missing data to perform calculation. */
    UNKNOWN
}

/**
 * High-level assessment of a combat advantage.
 */
enum class AdvantageLevel {
    HIGH,
    MEDIUM,
    LOW,
    NEUTRAL
}

/**
 * Assessment of a single move's performance in a specific matchup.
 */
data class MoveMatchup(
    val moveName: String,
    val type: PokemonType,
    val effectiveness: Effectiveness
)

/**
 * Comprehensive analysis of a 1v1 matchup between two species.
 */
data class MatchupAnalysis(
    val playerSpecies: String,
    val enemySpecies: String,
    
    val enemyThreatLevel: AdvantageLevel,
    val playerAdvantage: AdvantageLevel,
    
    val enemyFastMoveMatchup: MoveMatchup?,
    val enemyChargedMoveMatchups: List<MoveMatchup>,
    
    val playerFastMoveMatchups: List<MoveMatchup>,
    val playerChargedMoveMatchups: List<MoveMatchup>,
    
    val unknownRelationships: List<String>
)
