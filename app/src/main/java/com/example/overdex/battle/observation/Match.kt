package com.example.overdex.battle.observation

/**
 * Represents one live Pokémon GO battle.
 * 
 * The Match manages the lifecycle of observations and owns a [BattleWorkspace]
 * where evidence is collected. Once the match is complete, its observations are
 * typically reconciled into the Battle Timeline.
 * 
 * @property matchId A unique identifier for the battle.
 * @property state The current lifecycle phase of the match.
 * @property workspace The mutable storage area where incoming observations are collected.
 */
class Match(
    @Suppress("unused") val matchId: String,
    var state: MatchState = MatchState.CREATED,
    val workspace: BattleWorkspace = BattleWorkspace(),
) {
    /** The total number of frames processed during this Match. */
    var frameCount: Long = 0
        private set

    /**
     * Submits a transient observation to the match workspace.
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
