package com.example.overdex.battle.timeline

import com.example.overdex.battle.timeline.event.TimelineEvent

/**
 * A builder class responsible for constructing a [BattleTimeline].
 * 
 * The builder allows for the incremental addition of events during the 
 * reconciliation process before producing the final immutable ledger.
 */
class BattleTimelineBuilder {
    private val events = mutableListOf<TimelineEvent>()

    /**
     * Adds a derived event to the timeline being built.
     */
    fun addEvent(event: TimelineEvent) {
        events.add(event)
    }

    /**
     * Constructs the final immutable ledger.
     */
    fun build(): BattleTimeline {
        return BattleTimeline(events.toList())
    }
}
