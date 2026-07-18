package com.example.overdex.battle.observation

/**
 * Represents an active observation workspace for a single battle.
 * Owns an [ObservationWorkspace]. The resulting observations are eventually
 * reconciled into a BattleTimeline through the observation pipeline.
 */
class ObservationSession(
    val sessionId: String,
    val state: ObservationSessionState = ObservationSessionState.CREATED,
    val workspace: ObservationWorkspace = ObservationWorkspace()
) {
    /**
     * Submits a transient observation to the session workspace.
     */
    fun submit(observation: Observation) {
        workspace.add(observation)
    }
}
