package com.example.overdex.battle.observation

/**
 * The mutable "active memory" of a battle observation session.
 * Accumulates raw observations and serves as the workspace for reconciliation.
 */
class ObservationWorkspace {
    private val _observations = mutableListOf<Observation>()
    val observations: List<Observation> get() = _observations

    fun add(observation: Observation) {
        _observations.add(observation)
    }

    fun clear() {
        _observations.clear()
    }
}
