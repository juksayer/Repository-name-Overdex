package com.example.overdex.battle.timeline

import com.example.overdex.battle.timeline.event.TimelineEvent

/**
 * Legacy immutable event-list representation of a battle's history.
 * 
 * A [BattleTimeline] represents an immutable, ordered ledger of events captured 
 * during a single Match. The canonical Article ledger is
 * [com.example.overdex.battle.reality.RealityTimeline]. Neither ledger
 * interprets the events it receives.
 * 
 * @property events The complete, chronological list of events recorded in this battle.
 */
class BattleTimeline(
    val events: List<TimelineEvent>,
)
