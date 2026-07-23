package com.example.overdex.model.observation

/**
 * Tracks the physical and technical deployment lifecycle of the ODX-FI instrument.
 * 
 * This state is managed by the coordinator (ViewModel) and reflects the status
 * of background services, permissions, and hardware-level readiness.
 */
enum class InstrumentDeploymentState {
    /** The instrument is docked and inactive. */
    IDLE,

    /** The system is waiting for the user to grant required runtime permissions. */
    REQUESTING_PERMISSIONS,

    /** Permissions granted; the instrument is primed for deployment. */
    READY,

    /** The background service is initializing and hardware is being engaged. */
    DEPLOYING,

    /** The instrument is in the field and actively processing frames. */
    OBSERVING,

    /** The session is ending; hardware is being released and the instrument is returning to dock. */
    RETURNING
}
