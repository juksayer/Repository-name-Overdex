package com.example.overdex.battle.timeline

import com.example.overdex.battle.timeline.event.TimelineEvent

/**
 * The canonical domain model for a battle's history.
 * Represents an immutable ledger of events captured during a single battle session.
 */
class BattleTimeline(
    val events: List<TimelineEvent>,
)
