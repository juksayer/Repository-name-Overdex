package com.example.overdex.battle.observation

/**
 * Represents the current lifecycle of an observation session.
 * These describe the observation process, not the battle itself.
 */
enum class ObservationSessionState {
    CREATED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED
}
