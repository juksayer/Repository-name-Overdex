package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.CapturedVisualFrame
import kotlin.math.abs

/**
 * One geometry pass shared by the two HP witnesses. Search stays inside the
 * calibrated vertical battlefield band, but includes both horizontal end caps
 * when the camera shifts. Neither tracking nor recovery writes calibration.
 */
internal class LiveBattlefieldHpTracker(private val calibration: BattleCalibration) {
    private val tracker = BattlefieldHpTracker()
    private val species = mutableMapOf<ActivePokemonSide, Pair<String, Long>>()
    private var lastWideScanAt: Long? = null
    private var cachedAt: Long? = null
    private var cachedSize: Pair<Int, Int>? = null
    private var cached = emptyMap<ActivePokemonSide, ActiveHpBarMeasurement>()

    @Synchronized fun reset(side: ActivePokemonSide) {
        tracker.reset(side)
        species.remove(side)
        cachedAt = null
        lastWideScanAt = null
    }

    @Synchronized fun observeSpecies(side: ActivePokemonSide, name: String, at: Long) {
        val previous = species[side]
        if (previous != null && at < previous.second) return
        if (previous?.first != name) {
            tracker.reset(side)
            cachedAt = null
            lastWideScanAt = null
        }
        species[side] = name to at
    }

    fun measure(frame: CapturedVisualFrame, side: ActivePokemonSide): ActiveHpBarMeasurement? = measureFrame(frame)[side]

    @Synchronized fun measureFrame(frame: CapturedVisualFrame): Map<ActivePokemonSide, ActiveHpBarMeasurement> {
        val bitmap = frame.bitmap
        val size = bitmap.width to bitmap.height
        if (cachedSize != null && cachedSize != size) {
            ActivePokemonSide.entries.forEach(tracker::reset)
            cachedAt = null
            lastWideScanAt = null
        }
        if (cachedAt != frame.capturedAtMonotonicTimeNanos) {
            // Older subscribers may arrive late. Do not rewind either tracker.
            if (cachedAt?.let { frame.capturedAtMonotonicTimeNanos < it } == true) return emptyMap()
            val regions = mapOf(
                ActivePokemonSide.PLAYER to BattleCropContracts.playerHpEvidence,
                ActivePokemonSide.OPPONENT to BattleCropContracts.opponentHpEvidence,
            ).mapNotNull { (owner, contract) ->
                contract.resolveRect(calibration, bitmap.width, bitmap.height)?.let {
                    owner to BattleCropBounds(it.left, it.top, it.right, it.bottom)
                }
            }.toMap()
            if (regions.size != 2) return emptyMap()
            fun scan(bounds: BattleCropBounds): List<ActiveHpBarMeasurement> {
                val w = bounds.right - bounds.left
                val h = bounds.bottom - bounds.top
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, bounds.left, bounds.top, w, h)
                return ActiveHpBarMeasurer.measureAll(w, h, bitmap.width * 2 / 7, pixels).map {
                    it.copy(left = it.left + bounds.left, right = it.right + bounds.left,
                        top = it.top + bounds.top, bottom = it.bottom + bounds.top)
                }.filter { frame.canObserve(BattleCropBounds(it.left, it.top, it.right, it.bottom)) }
            }
            // Track locally at frame rate; a full-width search on every frame
            // consumes too much CPU on the physical phone. Recovery is bounded
            // and returns to the calibrated battlefield, including the home lock.
            val at = frame.capturedAtMonotonicTimeNanos
            val local = tracker.trackingWindows(bitmap.width, regions).flatMap(::scan)
            cached = tracker.assign(bitmap.width, at, regions, local)
            if (cached.size < 2 && lastWideScanAt?.let { at - it < 250_000_000L } != true) {
                val bounds = BattleCropBounds(0, regions.values.minOf { it.top }, bitmap.width, regions.values.maxOf { it.bottom })
                cached = tracker.assign(bitmap.width, at, regions, scan(bounds))
                lastWideScanAt = at
            }
            cachedAt = frame.capturedAtMonotonicTimeNanos
            cachedSize = size
        }
        return cached
    }
}

/** Pixel-only core, so recorded source frames exercise exactly the live locator. */
internal class BattlefieldHpTracker {
    private data class Track(val home: ActiveHpBarMeasurement, val latest: ActiveHpBarMeasurement, val at: Long)
    private val tracks = mutableMapOf<ActivePokemonSide, Track>()

    fun reset(side: ActivePokemonSide) { tracks.remove(side) }

    fun trackingWindows(width: Int, regions: Map<ActivePokemonSide, BattleCropBounds>): List<BattleCropBounds> =
        tracks.mapNotNull { (side, track) ->
            val region = regions[side] ?: return@mapNotNull null
            val bar = track.latest
            val margin = (bar.width * .30f).toInt().coerceAtLeast(12)
            BattleCropBounds(
                (bar.left - margin).coerceAtLeast(0),
                (bar.top - margin).coerceAtLeast(region.top),
                (bar.right + margin).coerceAtMost(width),
                (bar.bottom + margin).coerceAtMost(region.bottom),
            ).takeIf { it.bottom - it.top >= 24 && it.right - it.left >= 32 }
        }

    fun measure(
        width: Int,
        height: Int,
        at: Long,
        regions: Map<ActivePokemonSide, BattleCropBounds>,
        pixelAt: (Int, Int) -> Int,
    ): Map<ActivePokemonSide, ActiveHpBarMeasurement> {
        if (regions.size != 2 || width <= 0 || height <= 0) return emptyMap()
        val top = regions.values.minOf { it.top }.coerceIn(0, height)
        val bottom = regions.values.maxOf { it.bottom }.coerceIn(top, height)
        val pixels = IntArray(width * (bottom - top))
        for (y in top until bottom) for (x in 0 until width) pixels[(y - top) * width + x] = pixelAt(x, y)
        return measurePixels(width, height, at, regions, pixels)
    }

    internal fun measurePixels(
        width: Int,
        height: Int,
        at: Long,
        regions: Map<ActivePokemonSide, BattleCropBounds>,
        pixels: IntArray,
    ): Map<ActivePokemonSide, ActiveHpBarMeasurement> {
        val top = regions.values.minOf { it.top }.coerceIn(0, height)
        val bottom = regions.values.maxOf { it.bottom }.coerceIn(top, height)
        // GO's active bars are about 2/7 of the source width. This bound excludes
        // the much smaller benched HP bars and does not depend on crop width.
        val candidates = ActiveHpBarMeasurer.measureAll(width, bottom - top, width * 2 / 7, pixels).map { it.copy(top = it.top + top, bottom = it.bottom + top) }
        return assign(width, at, regions, candidates)
    }

    internal fun assign(
        width: Int,
        at: Long,
        regions: Map<ActivePokemonSide, BattleCropBounds>,
        measurements: List<ActiveHpBarMeasurement>,
    ): Map<ActivePokemonSide, ActiveHpBarMeasurement> {
        // A thick outline, or a white sprite behind it, can generate several
        // nested row pairs for one bar. Keep the strongest rectangle when the
        // candidates share an edge and most of their vertical interior. Merely
        // comparing their top rows left tall duplicates behind, making two
        // visible bars look like three and suppressing BOTH combatants.
        val bars = mutableListOf<ActiveHpBarMeasurement>()
        measurements.sortedByDescending { it.confidence }.forEach { candidate ->
            if (bars.none { sameOutline(it, candidate) || weakerOverlappingArtwork(it, candidate) }) bars += candidate
        }
        fun inBand(side: ActivePokemonSide, bar: ActiveHpBarMeasurement): Boolean {
            val region = regions[side] ?: return false
            return bar.top >= region.top && bar.bottom <= region.bottom
        }
        fun sameSize(side: ActivePokemonSide, bar: ActiveHpBarMeasurement): Boolean = tracks[side]?.home?.let {
            ActiveHpBarGeometry.matchesSize(bar.width, bar.bottom - bar.top, it.width, it.bottom - it.top)
        } ?: true

        val selected = mutableMapOf<ActivePokemonSide, ActiveHpBarMeasurement>()
        if (bars.size == 2) {
            val ordered = bars.sortedBy { it.centerX }
            // Two visibly separate bars retain left/player and right/opponent
            // order even while both move toward the middle during a charge.
            if (ordered[1].centerX - ordered[0].centerX > width * .12f) {
                listOf(ActivePokemonSide.PLAYER, ActivePokemonSide.OPPONENT).forEachIndexed { i, side ->
                    if (inBand(side, ordered[i]) && sameSize(side, ordered[i])) selected[side] = ordered[i]
                }
            }
        } else if (bars.size == 1) {
            val bar = bars.single()
            val obviousSide = when {
                bar.centerX < width * .40f -> ActivePokemonSide.PLAYER
                bar.centerX > width * .60f -> ActivePokemonSide.OPPONENT
                else -> null
            }
            val possible = ActivePokemonSide.entries.filter { side ->
                if (!inBand(side, bar) || !sameSize(side, bar)) return@filter false
                val track = tracks[side]
                if (obviousSide != null) return@filter side == obviousSide
                // One centered bar could be the attacker OR the recipient. Only
                // continuous motion from a uniquely owned lock can carry a side
                // into this area. An announcement alone cannot assign the camera.
                track != null && at - track.at in 0..CONTINUITY_NANOS &&
                    abs(bar.centerX - track.latest.centerX) <= track.home.width * .25f
            }
            possible.singleOrNull()?.let { selected[it] = bar }
        }
        selected.forEach { (side, bar) -> tracks[side] = Track(tracks[side]?.home ?: bar, bar, at) }
        return selected
    }

    private fun sameOutline(a: ActiveHpBarMeasurement, b: ActiveHpBarMeasurement): Boolean {
        if (abs(a.left - b.left) >= 5 || abs(a.right - b.right) >= 5) return false
        if (abs(a.top - b.top) >= 5 && abs(a.bottom - b.bottom) >= 5) return false
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val smallerHeight = minOf(a.bottom - a.top, b.bottom - b.top)
        return smallerHeight > 0 && overlap >= smallerHeight * .75f
    }

    private fun weakerOverlappingArtwork(stronger: ActiveHpBarMeasurement, weaker: ActiveHpBarMeasurement): Boolean {
        // Orange Pokémon art can extend one apparent pulsing outline sideways,
        // while the real white rectangle is still complete. Unlike nested edge
        // duplicates, these candidates do not have matching end caps. Suppress
        // only a substantially weaker rectangle occupying the SAME bar area;
        // a separate shape or an equally plausible alternative stays ambiguous.
        if (stronger.confidence < .85f || stronger.confidence - weaker.confidence < .15f) return false
        val overlapWidth = minOf(stronger.right, weaker.right) - maxOf(stronger.left, weaker.left)
        val overlapHeight = minOf(stronger.bottom, weaker.bottom) - maxOf(stronger.top, weaker.top)
        val smallerHeight = minOf(stronger.bottom - stronger.top, weaker.bottom - weaker.top)
        return overlapWidth >= maxOf(stronger.width, weaker.width) * .80f &&
            smallerHeight > 0 && overlapHeight >= smallerHeight * .75f
    }

    private companion object { const val CONTINUITY_NANOS = 500_000_000L }
}
