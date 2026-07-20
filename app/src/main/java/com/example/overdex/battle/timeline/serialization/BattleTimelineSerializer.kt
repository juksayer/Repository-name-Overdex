package com.example.overdex.battle.timeline.serialization

import com.example.overdex.battle.timeline.BattleTimeline

/**
 * Contract for persisting and transmitting a [BattleTimeline].
 * 
 * Implementations are responsible for converting the complex, hierarchical
 * timeline into a format suitable for storage or network transport (e.g., JSON, Protobuf).
 */
interface BattleTimelineSerializer {
    /** Converts a timeline into a byte array. */
    fun serialize(timeline: BattleTimeline): ByteArray
    /** Reconstructs a timeline from a byte array. */
    fun deserialize(data: ByteArray): BattleTimeline
}
