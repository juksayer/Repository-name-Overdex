package com.example.overdex.battle.observation

/** Uses existing charge-button OCR as independent shield-decision evidence.
 * A fragment alone is never a cue; paired fragments must belong to different
 * apertures within two seconds of source time. No additional OCR is scheduled.
 */
internal class ShieldDecisionTextEvidence {
    data class Reading(val slot: Int, val text: String, val at: Long, val wall: Long, val id: String)
    data class Signal(val at: Long, val wall: Long, val ids: List<String>)
    private val readings = mutableMapOf<Int, Reading>()
    private var lastEmitted: Long? = null

    fun reset() { readings.clear(); lastEmitted = null }

    fun accept(slot: Int, text: String, basis: String, at: Long, wall: Long, id: String): Signal? {
        if (slot !in 1..2 || basis != "OCR") return null
        if (at <= (readings[slot]?.at ?: Long.MIN_VALUE)) return null
        val normalized = text.trim().uppercase().replace(Regex("\\s+"), " ")
        val reading = Reading(slot, normalized, at, wall, id)
        readings[slot] = reading
        val evidence = if (normalized == "NOT NOW") listOf(reading) else {
            val other = readings[3 - slot] ?: return null
            if (kotlin.math.abs(at - other.at) > 2_000_000_000L ||
                setOf(normalized, other.text) != setOf("NOT", "NOW")) return null
            listOf(reading, other)
        }
        val newest = evidence.maxOf { it.at }
        if (lastEmitted?.let { newest - it < 15_000_000_000L } == true) return null
        lastEmitted = newest
        val first = evidence.minBy { it.at }
        return Signal(first.at, first.wall, evidence.map { it.id }.distinct())
    }
}
