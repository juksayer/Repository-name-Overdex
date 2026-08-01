package com.example.overdex.battle.timeline

import com.example.overdex.battle.timeline.event.TimelineEvent

/**
 * The canonical domain model for a battle's history.
 * 
 * A [BattleTimeline] represents an immutable, ordered ledger of events captured 
 * during a single Match. It serves as the primary data source for 
 * post-battle analysis, replays, and long-term archiving.
 * 
 * @property events The complete, chronological list of events recorded in this battle.
 */
class BattleTimeline(
    val events: List<TimelineEvent>,
)
