package com.example.overdex.model

enum class Effectiveness {
    SUPER_EFFECTIVE,   // 1.6x or more
    NEUTRAL,           // 1.0x
    NOT_VERY_EFFECTIVE, // 0.625x or less
    IMMUNE,            // 0.39x or less
    UNKNOWN            // Missing data
}

enum class AdvantageLevel {
    HIGH,
    MEDIUM,
    LOW,
    NEUTRAL
}

data class MoveMatchup(
    val moveName: String,
    val type: PokemonType,
    val effectiveness: Effectiveness
)

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
