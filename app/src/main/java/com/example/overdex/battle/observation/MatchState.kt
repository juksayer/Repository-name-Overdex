package com.example.overdex.battle.observation

/**
 * Represents the lifecycle stages of a Match.
 * 
 * These states describe the actual Pokémon GO battle, rather than the
 * surrounding Droidball deployment session.
 */
enum class MatchState {
    /** Droidball may be armed and observing, but GO has not started a battle. */
    CREATED,
    /** GO has been accepted and the battle clock is running. */
    ACTIVE,
    /** Match observation is temporarily suspended. */
    PAUSED,
    /** The battle has ended and the match has finalized its data. */
    COMPLETED,
    /** The match was terminated before the battle concluded. */
    CANCELLED
}
