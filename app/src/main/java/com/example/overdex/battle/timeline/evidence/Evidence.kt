package com.example.overdex.battle.timeline.evidence

/**
 * Represents the underlying evidence supporting an event in the [BattleTimeline].
 * 
 * Evidence provides the "why" behind an event, allowing the system to trace
 * a reconciled belief back to its source (e.g., a specific OCR string or audio clip).
 */
interface Evidence {
    /** The identifier for the specific source of this evidence. */
    val sourceId: String
}
