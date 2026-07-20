package com.example.overdex.battle.observation

/**
 * Represents the lifecycle stages of a battle observation session.
 * 
 * These states describe the technical observation process (e.g., active vs paused),
 * rather than the state of the Pokémon GO battle itself.
 */
enum class ObservationSessionState {
    /** The session has been created but has not yet started collecting evidence. */
    CREATED,
    /** Observers are actively monitoring and submitting evidence. */
    ACTIVE,
    /** Observation is temporarily suspended. */
    PAUSED,
    /** The battle has ended and the session has finalized its data. */
    COMPLETED,
    /** The session was terminated before the battle concluded. */
    CANCELLED
}
