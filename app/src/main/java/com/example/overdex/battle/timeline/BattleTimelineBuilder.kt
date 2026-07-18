package com.example.overdex.battle.timeline

import com.example.overdex.battle.timeline.event.TimelineEvent

/**
 * Responsible for the construction of an immutable [BattleTimeline] from
 * derived events.
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
