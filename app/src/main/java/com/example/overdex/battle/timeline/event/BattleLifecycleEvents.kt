package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Marks the beginning of a battle session.
 */
data class BattleStarted(
    override val timestamp: Long,
    override val observerId: ObserverId
) : TimelineEvent

/**
 * Marks the conclusion of a battle session.
 */
data class BattleEnded(
    override val timestamp: Long,
    override val observerId: ObserverId
) : TimelineEvent
