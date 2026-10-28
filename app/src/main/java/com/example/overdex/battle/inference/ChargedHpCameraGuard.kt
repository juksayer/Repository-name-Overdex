package com.example.overdex.battle.inference

import java.util.TreeMap
import java.util.TreeSet
import kotlin.math.abs
import kotlin.math.max

/**
 * Associates a measured relocation of an existing HP bar with an independently
 * announced charge. It does not identify a move, infer damage, or reject the HP
 * reading: only the relocated interval is unavailable as Fast Move evidence.
 *
 * A relocation without a charge is held briefly, then released. Source-time
 * ordering is rebuilt so slower announcement OCR can corroborate an earlier
 * captured frame. All retained state is numeric and bounded.
 */
internal class ChargedHpCameraGuard {
    private data class Frame(
        val id: String, val at: Long, val side: String,
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val sourceW: Int, val sourceH: Int,
    ) {
        val width get() = right - left
        val height get() = bottom - top
    }

    private data class Envelope(val first: Frame, val minTop: Int, val maxTop: Int, val width: Int) {
        fun compatible(frame: Frame): Boolean {
            val tolerance = max(3.0, width * HORIZONTAL_TOLERANCE)
            return frame.sourceW == first.sourceW && frame.sourceH == first.sourceH &&
                abs(frame.left - first.left) <= tolerance && abs(frame.right - first.right) <= tolerance &&
                abs(frame.width - width) <= tolerance * 2
        }

        fun returned(frame: Frame): Boolean =
            frame.top in (minTop - max(2, width / 12))..(maxTop + max(2, width / 12))
    }

    private data class Episode(
        val side: String, val start: Long, var endExclusive: Long,
        val envelope: Envelope,
    ) {
        val key get() = side to start
        fun contains(at: Long) = at >= start && at < endExclusive
    }

    /** Rolling extrema and width counts avoid allocating/sorting a baseline per frame. */
    private class Baseline {
        val samples = ArrayDeque<Frame>()
        private val low = ArrayDeque<Frame>()
        private val high = ArrayDeque<Frame>()
        private val widths = TreeMap<Int, Int>()
        val size get() = samples.size
        val minTop get() = low.first().top
        val maxTop get() = high.first().top
        val medianWidth: Int get() {
            var remaining = size / 2
            for ((width, count) in widths) {
                if (remaining < count) return width
                remaining -= count
            }
            return samples.first().width
        }
        fun expire(at: Long) {
            while (samples.isNotEmpty() && at - samples.first().at > BASELINE_NANOS) removeFirst()
        }
        fun compatible(frame: Frame, width: Int): Boolean {
            val first = samples.first()
            val tolerance = max(3.0, width * HORIZONTAL_TOLERANCE)
            return frame.sourceW == first.sourceW && frame.sourceH == first.sourceH &&
                abs(frame.left - first.left) <= tolerance && abs(frame.right - first.right) <= tolerance &&
                abs(frame.width - width) <= tolerance * 2
        }
        fun add(frame: Frame) {
            samples += frame
            while (low.isNotEmpty() && low.last().top > frame.top) low.removeLast()
            while (high.isNotEmpty() && high.last().top < frame.top) high.removeLast()
            low += frame
            high += frame
            widths[frame.width] = (widths[frame.width] ?: 0) + 1
            while (size > MAX_BASELINE_SAMPLES) removeFirst()
        }
        fun snapshot(width: Int) = Envelope(samples.first(), minTop, maxTop, width)
        fun clear() {
            samples.clear()
            low.clear()
            high.clear()
            widths.clear()
        }
        private fun removeFirst() {
            val frame = samples.removeFirst()
            if (low.first().at == frame.at) low.removeFirst()
            if (high.first().at == frame.at) high.removeFirst()
            val count = widths.getValue(frame.width)
            if (count == 1) widths.remove(frame.width) else widths[frame.width] = count - 1
        }
    }

    private class SideState(
        val baseline: Baseline = Baseline(), var active: Episode? = null,
        var boundaryAt: Long? = null,
    )

    private val frames = mutableMapOf<String, TreeMap<Long, Frame>>()
    private val ids = LinkedHashSet<String>()
    private val charges = TreeSet<Long>()
    private val boundaries = TreeSet<Long>()
    private val inclusiveBoundaries = TreeSet<Long>()
    private val frozenWindows = LinkedHashMap<Pair<String, Long>, LongRange>()
    private val sideStates = mutableMapOf<String, SideState>()
    private var watermark = Long.MIN_VALUE
    private var dirty = false
    private val episodes = mutableListOf<Episode>()

    /** Non-HP testimony must also advance the expiry of an uncorroborated hold. */
    fun advanceTo(at: Long) {
        if (at < 0 || at <= watermark) return
        watermark = at
        prune()
    }

    fun observeHp(
        id: String, at: Long, side: String, left: Int, top: Int, right: Int, bottom: Int,
        sourceW: Int?, sourceH: Int?,
    ) {
        if (at < 0 || id.isBlank() || side !in SIDES || sourceW == null || sourceH == null ||
            sourceW <= 0 || sourceH <= 0 || left < 0 || top < 0 || right <= left || bottom <= top ||
            right > sourceW || bottom > sourceH ||
            (right - left).toDouble() / (bottom - top) !in 6.0..30.0 ||
            watermark != Long.MIN_VALUE && at < watermark - HISTORY_NANOS || id in ids) return
        val byTime = frames.getOrPut(side) { TreeMap() }
        // Duplicate publications of a source frame do not create baseline samples.
        if (at in byTime) return
        val append = byTime.isEmpty() || at > byTime.lastKey()
        val frame = Frame(id, at, side, left, top, right, bottom, sourceW, sourceH)
        ids += id
        byTime[at] = frame
        if (append && !dirty) process(frame) else dirty = true
        advanceTo(at)
        while (byTime.size > MAX_FRAMES_PER_SIDE) {
            freezeBefore(byTime.firstKey() + 1)
            ids.remove(requireNotNull(byTime.pollFirstEntry()).value.id)
        }
    }

    fun observeCharge(at: Long) {
        if (at < 0 || watermark != Long.MIN_VALUE && at < watermark - HISTORY_NANOS) return
        charges.add(at)
        advanceTo(at)
    }

    /** Only a genuine appearance/faint boundary belongs here, not repeated species OCR. */
    fun observeBoundary(at: Long, includeImpactAtBoundary: Boolean = false) {
        if (at < 0 || watermark != Long.MIN_VALUE && at < watermark - HISTORY_NANOS) return
        val added = boundaries.add(at)
        val inclusionAdded = includeImpactAtBoundary && inclusiveBoundaries.add(at)
        if (!added && !inclusionAdded) return
        dirty = true
        advanceTo(at)
    }

    fun isChargeImpact(at: Long): Boolean {
        rebuild()
        return frozenWindows.values.any { at in it } || episodes.any { it.contains(at) && confirmed(it) }
    }

    fun isAwaitingCharge(at: Long): Boolean {
        rebuild()
        return episodes.any { it.contains(at) && !confirmed(it) &&
            watermark <= add(it.start, CAMERA_MAX_NANOS) }
    }

    /** Confirmed windows retain their measured endpoints after old frames expire. */
    fun windows(): List<LongRange> {
        rebuild()
        return (frozenWindows.values + episodes.filter(::confirmed).map { it.start until it.endExclusive })
            .sortedBy { it.first }.fold(mutableListOf()) { merged, next ->
                val previous = merged.lastOrNull()
                if (previous != null && next.first <= add(previous.last, 1))
                    merged[merged.lastIndex] = previous.first..maxOf(previous.last, next.last)
                else merged += next
                merged
            }
    }

    fun reset() {
        frames.clear()
        ids.clear()
        charges.clear()
        boundaries.clear()
        inclusiveBoundaries.clear()
        frozenWindows.clear()
        sideStates.clear()
        episodes.clear()
        watermark = Long.MIN_VALUE
        dirty = false
    }

    private fun confirmed(episode: Episode): Boolean {
        val from = maxOf(episode.start - CHARGE_LOOKBACK_NANOS,
            boundaries.floor(episode.start)?.let { add(it, 1) } ?: Long.MIN_VALUE)
        val until = minOf(add(episode.start, CHARGE_LOOKAHEAD_NANOS),
            boundaries.higher(episode.start)?.let { boundaryEnd(it) - 1 } ?: Long.MAX_VALUE)
        return charges.ceiling(from)?.let { it <= until } == true
    }

    private fun rebuild() {
        if (!dirty) return
        // A newly accepted late frame cannot change an episode that already
        // ended before the accepted-history boundary. Preserve those endpoints
        // before rebuilding a possibly partial old prefix.
        if (watermark >= HISTORY_NANOS) freezeCompletedBefore(watermark - HISTORY_NANOS)
        episodes.clear()
        sideStates.clear()
        for (samples in frames.values) for (frame in samples.values) process(frame)
        // Older completed episodes were frozen while their full baseline was
        // present. A partial prefix at the retention edge must not replace them.
        if (watermark >= HISTORY_NANOS) episodes.removeAll {
            it.endExclusive < watermark - HISTORY_NANOS
        }
        dirty = false
    }

    private fun process(frame: Frame) {
        val state = sideStates.getOrPut(frame.side) { SideState() }
        val baseline = state.baseline
        val boundary = boundaries.floor(frame.at)
        if (boundary != state.boundaryAt) {
            state.active?.let { it.endExclusive = minOf(it.endExclusive, boundaryEnd(requireNotNull(boundary))) }
            baseline.clear()
            state.active = null
            state.boundaryAt = boundary
        }
        val current = state.active
        if (current != null) {
            when {
                frame.at >= current.endExclusive -> {
                    // A sustained new layout becomes a new baseline; it cannot
                    // keep the entire battle suppressed.
                    baseline.clear()
                    state.active = null
                }
                !current.envelope.compatible(frame) -> {
                    current.endExclusive = frame.at
                    baseline.clear()
                    state.active = null
                }
                current.envelope.returned(frame) -> {
                    current.endExclusive = frame.at
                    state.active = null
                }
                else -> return
            }
        }
        baseline.expire(frame.at)
        val width = if (baseline.size > 0) baseline.medianWidth else frame.width
        // A change of resolution or horizontal aperture starts a new baseline.
        if (baseline.size > 0 && !baseline.compatible(frame, width)) baseline.clear()
        if (baseline.size >= MIN_BASELINE_SAMPLES &&
            (frame.top < baseline.minTop - width * RELOCATION_WIDTH_FRACTION ||
                frame.top > baseline.maxTop + width * RELOCATION_WIDTH_FRACTION)) {
            val end = minOf(add(frame.at, CAMERA_MAX_NANOS),
                boundaries.higher(frame.at)?.let(::boundaryEnd) ?: Long.MAX_VALUE)
            val episode = Episode(frame.side, frame.at, end, baseline.snapshot(width))
            episodes += episode
            state.active = episode
            return
        }
        baseline.add(frame)
    }

    private fun freezeBefore(cutoff: Long) {
        rebuild()
        freezeCompletedBefore(cutoff)
    }

    private fun freezeCompletedBefore(cutoff: Long) {
        episodes.filter { it.endExclusive < cutoff && confirmed(it) }.forEach {
            frozenWindows[it.key] = it.start until it.endExclusive
        }
        episodes.removeAll { it.endExclusive < cutoff }
        while (frozenWindows.size > MAX_WINDOWS) frozenWindows.remove(frozenWindows.keys.first())
    }

    private fun prune() {
        if (watermark < RETENTION_NANOS) return
        val cutoff = watermark - RETENTION_NANOS
        if (frames.values.none { it.isNotEmpty() && it.firstKey() < cutoff }) return
        freezeBefore(watermark - HISTORY_NANOS)
        frames.values.forEach { samples ->
            while (samples.isNotEmpty() && samples.firstKey() < cutoff)
                ids.remove(requireNotNull(samples.pollFirstEntry()).value.id)
        }
        charges.removeIf { it < cutoff }
        // Retain the preceding boundary as the context for the first retained
        // frame; dropping it would look like a new appearance on the hot path.
        boundaries.floor(cutoff)?.let {
            boundaries.headSet(it, false).clear()
            inclusiveBoundaries.headSet(it, false).clear()
        }
    }

    private fun boundaryEnd(at: Long) = if (at in inclusiveBoundaries) add(at, 1) else at

    private fun add(at: Long, duration: Long) = if (at > Long.MAX_VALUE - duration) Long.MAX_VALUE else at + duration

    private companion object {
        val SIDES = setOf("PLAYER", "OPPONENT")
        const val BASELINE_NANOS = 5_000_000_000L
        const val MIN_BASELINE_SAMPLES = 3
        const val MAX_BASELINE_SAMPLES = 96
        const val RELOCATION_WIDTH_FRACTION = .55
        const val HORIZONTAL_TOLERANCE = .04
        const val CHARGE_LOOKBACK_NANOS = 4_000_000_000L
        const val CHARGE_LOOKAHEAD_NANOS = 1_500_000_000L
        const val CAMERA_MAX_NANOS = 5_000_000_000L
        const val HISTORY_NANOS = 20_000_000_000L
        // Extra baseline + camera history makes late rebuilds near the accepted
        // history edge equivalent to the incremental path.
        const val RETENTION_NANOS = HISTORY_NANOS + BASELINE_NANOS + CAMERA_MAX_NANOS
        const val MAX_FRAMES_PER_SIDE = 2_048
        const val MAX_WINDOWS = 256
    }
}
