package com.example.overdex.battle.observation

/**
 * The mutable storage area for a Match.
 * 
 * The workspace accumulates raw [Observation]s from multiple sources and provides
 * the shared data pool used by the [ObservationReconciler] to commit events to history.
 */
class BattleWorkspace {
    private val _observations = mutableListOf<Observation>()
    /** The complete list of observations collected during this Match. */
    val observations: List<Observation> get() = _observations

    /** Adds a new observation to the workspace. */
    fun add(observation: Observation) {
        _observations.add(observation)
    }

    /** Clears all observations from the workspace. */
    fun clear() {
        _observations.clear()
    }
}
