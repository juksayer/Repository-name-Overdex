package com.example.overdex.model

import androidx.compose.runtime.mutableStateListOf
import com.example.overdex.diagnostics.BattleRecordDiagnostics

/**
 * A chronological record of battle events used for real-time UI updates.
 * 
 * Unlike the canonical domain model, this implementation uses [mutableStateListOf]
 * to provide reactive updates to Compose observers during a live session.
 */
class BattleTimeline {
    private val _records = mutableStateListOf<TimelineRecord>()
    private val _events = mutableStateListOf<BattleEvent>()
    /** The current list of semantic events (compatibility view). */
    val events: List<BattleEvent> get() = _events.toList()

    /** Raw measurements must not invalidate a shell that only displays this count. */
    val eventCount: Int get() = _events.size
    
    /** The complete list of records (articles and events). */
    val records: List<TimelineRecord> get() = _records

    /** Preserves a supplied record; interpretation belongs to its producer. */
    fun record(record: TimelineRecord) {
        _records.add(record)
        if (record is BattleEvent) _events.add(record)
        BattleRecordDiagnostics.log(record)
    }

    fun clear() {
        _records.clear()
        _events.clear()
    }
}
