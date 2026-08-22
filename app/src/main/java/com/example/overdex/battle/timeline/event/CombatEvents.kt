package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.evidence.Evidence
import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * A [TimelineEvent] recorded when a fast move is identified during battle.
 * 
 * @property moveId The unique identifier of the fast move being used.
 */
data class FastMovePerformed(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val moveId: String,
    override val evidence: Evidence? = null
) : TimelineEvent

/**
 * A [TimelineEvent] recorded when the charged move animation sequence begins.
 * 
 * @property moveId The identifier of the charged move being prepared.
 */
data class ChargedMoveStarted(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val moveId: String,
    override val evidence: Evidence? = null
) : TimelineEvent

/**
 * A [TimelineEvent] recorded when the charged move damage or effect is applied.
 * 
 * @property moveId The identifier of the charged move being resolved.
 */
data class ChargedMoveResolved(
    override val timestamp: Long,
    override val observerId: ObserverId,
    val moveId: String,
    override val evidence: Evidence? = null
) : TimelineEvent

/**
 * A [TimelineEvent] recorded when a shield is deployed by either trainer.
 */
data class ShieldUsed(
    override val timestamp: Long,
    override val observerId: ObserverId,
    override val evidence: Evidence? = null
) : TimelineEvent
