package com.example.overdex.model.observation

/**
 * Defines the unified Instrument Operating State of Overdex.
 * 
 * Physical hardware components and UI overlays respond to this state to adjust their
 * behavior (e.g., Droidball movement, overlay visibility).
 */
enum class ObservationSessionState {
    /** No active observation or background service. */
    IDLE,

    /** Actively capturing or inspecting evidence in the foreground. */
    OBSERVING,

    /** User is currently calibrating observation regions. */
    CALIBRATING,

    /** DroidBall background service is active, listening for battle triggers. */
    SERVICE_ACTIVE
}
