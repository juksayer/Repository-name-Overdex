package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide

/** Two small, source-timed tracks. Missing pixels and optional fading never imply a faint. */
internal class CompleteHpBarTrack {
    data class Request(val side: ActivePokemonSide, val anchor: ActiveHpBarMeasurement, val generation: Long)
    data class Change(val sample: CompleteHpBarSample, val status: String, val candidateSince: Long?)
    private data class Track(var sample: CompleteHpBarSample, var at: Long,
        var firstOrange: Long? = null, var orangeCount: Int = 0, var status: String? = null)
    private val tracks = mutableMapOf<ActivePokemonSide, Track>()
    private val boundaries = mutableMapOf<ActivePokemonSide, Long>()
    private val generations = mutableMapOf<ActivePokemonSide, Long>()
    private var dimensions: Pair<Int, Int>? = null

    @Synchronized fun generation(side: ActivePokemonSide) = generations[side] ?: 0L
    @Synchronized fun reset(side: ActivePokemonSide, at: Long? = null) {
        tracks.remove(side)
        generations[side] = generation(side) + 1L
        if (at != null) boundaries[side] = maxOf(boundaries[side] ?: Long.MIN_VALUE, at)
    }
    @Synchronized fun resetAll(at: Long? = null) { ActivePokemonSide.entries.forEach { reset(it, at) } }
    @Synchronized fun frameSize(width: Int, height: Int) {
        if (dimensions != null && dimensions != (width to height)) resetAll()
        dimensions = width to height
    }
    @Synchronized fun requests(at: Long): List<Request> = tracks.mapNotNull { (side, track) ->
        if (at - track.at !in 1..MAX_GAP) null else Request(side, track.sample.anchor(), generation(side))
    }
    @Synchronized fun accept(side: ActivePokemonSide, sample: CompleteHpBarSample, at: Long, generation: Long): Change? {
        if (generation != generation(side) || at <= (boundaries[side] ?: Long.MIN_VALUE)) return null
        val old = tracks[side]
        if (old != null && at <= old.at) return null
        val continuous = old != null && at - old.at <= MAX_GAP &&
            kotlin.math.abs(sample.bounds.left - old.sample.bounds.left) <= 6 &&
            kotlin.math.abs(sample.bounds.top - old.sample.bounds.top) <= 20 &&
            kotlin.math.abs(sample.interiorColumns - old.sample.interiorColumns) <= 6
        val track = if (continuous) old!! else Track(sample, at).also { tracks[side] = it }
        if (sample.orangeOnly) {
            if (track.firstOrange == null || at - track.at > MAX_COLOR_GAP) {
                track.firstOrange = at; track.orangeCount = 0
            }
            track.orangeCount++
        } else { track.firstOrange = null; track.orangeCount = 0 }
        val status = when {
            sample.orangeOnly && track.orangeCount >= 3 && at - requireNotNull(track.firstOrange) >= 100_000_000L -> "SUSTAINED_DAMAGE_COLOR"
            sample.orangeOnly -> "DAMAGE_COLOR"
            sample.liveColumns > 0 -> "LIVE_COLOR"
            sample.hpSample?.fillColor == HpBarFillColor.EMPTY -> "VISIBLE_EMPTY"
            else -> "UNRESOLVED_COLOR"
        }
        val changed = track.status != status
        track.sample = sample; track.at = at; track.status = status
        return if (changed) Change(sample, status, track.firstOrange) else null
    }
    companion object {
        const val MAX_GAP = 500_000_000L
        const val MAX_COLOR_GAP = 150_000_000L
    }
}
