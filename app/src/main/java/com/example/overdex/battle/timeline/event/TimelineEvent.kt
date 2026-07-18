package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * The abstract contract for all events occurring within a [BattleTimeline].
 */
interface TimelineEvent {
    val timestamp: Long
    val observerId: ObserverId
}
