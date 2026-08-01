package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * A [TimelineEvent] marking the beginning of a Match.
 */
data class BattleStarted(
    override val timestamp: Long,
    override val observerId: ObserverId
) : TimelineEvent

/**
 * A [TimelineEvent] marking the conclusion of a Match.
 */
data class BattleEnded(
    override val timestamp: Long,
    override val observerId: ObserverId
) : TimelineEvent
