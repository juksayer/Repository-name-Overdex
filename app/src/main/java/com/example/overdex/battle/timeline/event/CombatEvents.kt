package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * Recorded when a fast move is identified.
 */
data class FastMovePerformed(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val moveId: String
) : TimelineEvent

/**
 * Recorded when the charged move sequence begins.
 */
data class ChargedMoveStarted(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val moveId: String
) : TimelineEvent

/**
 * Recorded when the charged move damage/effect is applied.
 */
data class ChargedMoveResolved(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val moveId: String
) : TimelineEvent

/**
 * Recorded when a shield is deployed.
 */
data class ShieldUsed(
    override val timestamp: Long,
    override val observerId: ObserverId
) : TimelineEvent
