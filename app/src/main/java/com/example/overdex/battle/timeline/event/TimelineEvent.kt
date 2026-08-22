package com.example.overdex.battle.timeline.event

import com.example.overdex.battle.timeline.evidence.Evidence
import com.example.overdex.battle.timeline.observer.ObserverId

/**
 * The abstract contract for all events occurring within a [BattleTimeline].
 * 
 * A [TimelineEvent] represents a reconciled fact about the battle (e.g., a move
 * being used, a Pokémon switching). Every event is associated with the observer
 * that detected it and the time it occurred.
 */
interface TimelineEvent {
    /** The system time in milliseconds when the event was recorded. */
    val timestamp: Long
    /** The identifier of the observer that contributed the primary evidence for this event. */
    val observerId: ObserverId
    /** The optional evidence reference that supports this reconciled event. */
    val evidence: Evidence?
}
