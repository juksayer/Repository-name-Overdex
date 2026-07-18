package com.example.overdex.battle.timeline.serialization

import com.example.overdex.battle.timeline.BattleTimeline

/**
 * Contract for persisting and transmitting the canonical [BattleTimeline].
 * Intentionally generic to avoid baking a specific format into the domain.
 */
interface BattleTimelineSerializer {
    fun serialize(timeline: BattleTimeline): ByteArray
    fun deserialize(data: ByteArray): BattleTimeline
}
