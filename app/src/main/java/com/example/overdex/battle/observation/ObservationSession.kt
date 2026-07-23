package com.example.overdex.battle.observation

/**
 * Represents an active observation session for a single live battle.
 * 
 * The session manages the lifecycle of observations and owns an [ObservationWorkspace]
 * where evidence is collected. Once the session is complete, its observations are
 * typically reconciled into the Battle Timeline.
 * 
 * @property sessionId A unique identifier for the battle session.
 * @property state The current lifecycle phase of the session.
 * @property workspace The mutable storage area where incoming observations are collected.
 */
class ObservationSession(
    @Suppress("unused") val sessionId: String,
    var state: ObservationSessionState = ObservationSessionState.CREATED,
    val workspace: ObservationWorkspace = ObservationWorkspace(),
) {
    /** The total number of frames processed during this session. */
    var frameCount: Long = 0
        private set

    /**
     * Submits a transient observation to the session workspace.
     */
    fun submit(observation: Observation) {
        workspace.add(observation)
    }

    /**
     * Increments the frame count.
     */
    fun incrementFrameCount() {
        frameCount++
    }
}
