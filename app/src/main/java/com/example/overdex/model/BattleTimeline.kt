package com.example.overdex.model

import androidx.compose.runtime.mutableStateListOf
import com.example.overdex.battle.reality.RealityArticle

/**
 * A chronological record of battle events used for real-time UI updates.
 * 
 * Unlike the canonical domain model, this implementation uses [mutableStateListOf]
 * to provide reactive updates to Compose observers during a live session.
 */
class BattleTimeline {
    private val _records = mutableStateListOf<TimelineRecord>()
    /** The current list of semantic events (compatibility view). */
    val events: List<BattleEvent> get() = _records.filterIsInstance<BattleEvent>()
    
    /** The complete list of records (articles and events). */
    val records: List<TimelineRecord> get() = _records

    /** Records a new factual record in the timeline. */
    fun record(record: TimelineRecord) {
        _records.add(record)
        
        when (record) {
            is BattleEvent -> {
                // Instant console verification for development
                android.util.Log.d("BATTLE_TIMELINE", "[${record.actor}] ${record.type} (#${record.pokemonId ?: "N/A"})")
            }
            is RealityArticle -> {
                android.util.Log.d("BATTLE_TIMELINE", "[OBSERVATION] ${record.sourceId.id}")
            }
        }
    }

    fun clear() {
        _records.clear()
    }
}
