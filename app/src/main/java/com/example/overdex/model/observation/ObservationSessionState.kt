package com.example.overdex.model.observation

/**
 * Represents the unified Instrument Operating State of the Overdex.
 * Physical hardware components respond to this state.
 */
enum class ObservationSessionState {
    /**
     * No active observation or service.
     */
    IDLE,

    /**
     * Actively capturing/inspecting evidence (e.g., in Capture Verification).
     */
    OBSERVING,

    /**
     * Calibration mode is active.
     */
    CALIBRATING,

    /**
     * DroidBall background service is active.
     */
    SERVICE_ACTIVE
}
