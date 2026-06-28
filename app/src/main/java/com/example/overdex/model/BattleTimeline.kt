package com.example.overdex.model

import androidx.compose.runtime.mutableStateListOf

/**
 * A chronological record of battle events.
 * It is intentionally "dumb" and only responsible for storing facts in order.
 */
class BattleTimeline {
    private val _events = mutableStateListOf<BattleEvent>()
    val events: List<BattleEvent> get() = _events

    fun record(event: BattleEvent) {
        _events.add(event)
        // Instant console verification for development
        android.util.Log.d("BATTLE_TIMELINE", "[${event.actor}] ${event.type} (#${event.pokemonId ?: "N/A"})")
    }

    fun clear() {
        _events.clear()
    }
}
