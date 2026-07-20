package com.example.overdex.model

import androidx.compose.runtime.mutableStateListOf

/**
 * A chronological record of battle events used for real-time UI updates.
 * 
 * Unlike the canonical domain model, this implementation uses [mutableStateListOf]
 * to provide reactive updates to Compose observers during a live session.
 */
class BattleTimeline {
    private val _events = mutableStateListOf<BattleEvent>()
    /** The current list of events. */
    val events: List<BattleEvent> get() = _events

    /** Records a new factual event in the timeline. */
    fun record(event: BattleEvent) {
        _events.add(event)
        // Instant console verification for development
        android.util.Log.d("BATTLE_TIMELINE", "[${event.actor}] ${event.type} (#${event.pokemonId ?: "N/A"})")
    }

    fun clear() {
        _events.clear()
    }
}
