package com.example.overdex.battle.observation

/**
 * Represents the lifecycle stages of a Match.
 * 
 * These states describe the technical Match process (e.g., active vs paused),
 * rather than the state of the Pokémon GO battle itself.
 */
enum class MatchState {
    /** The match has been created but has not yet started collecting evidence. */
    CREATED,
    /** Observers are actively monitoring and submitting evidence to the match. */
    ACTIVE,
    /** Match observation is temporarily suspended. */
    PAUSED,
    /** The battle has ended and the match has finalized its data. */
    COMPLETED,
    /** The match was terminated before the battle concluded. */
    CANCELLED
}
