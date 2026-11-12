package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.TerminalFlashFeature
import com.example.overdex.battle.custody.TerminalParticleSample
import kotlin.math.hypot

internal data class TerminalEffectFrame(val articleId: String, val sample: TerminalParticleSample)
internal interface TerminalFinishSequence {
    val startedAt: Long
    val clearedAt: Long
    val evidenceReferences: List<String>
}
internal data class TerminalCollapseSequence(
    override val startedAt: Long, val peakAt: Long, val contractedAt: Long, override val clearedAt: Long,
    override val evidenceReferences: List<String>,
) : TerminalFinishSequence

/** Pixel order only. QTE can satisfy this sequence; no side, damage or KO is implied. */
internal object TerminalCollapseSequences {
    private data class Seed(val frame: TerminalEffectFrame, val x: Double, val y: Double,
        var peak: TerminalFlashFeature? = null, var peakAt: Long = 0,
        var contractedAt: Long? = null, var clearFrames: Int = 0,
        val refs: MutableSet<String> = linkedSetOf())

    fun find(frames: List<TerminalEffectFrame>): List<TerminalCollapseSequence> {
        val seeds = mutableListOf<Seed>()
        val result = mutableListOf<TerminalCollapseSequence>()
        var previous: TerminalParticleSample? = null
        var cooldown = Long.MIN_VALUE
        // A source instant with conflicting readings is an unavailable barrier.
        for ((_, copies) in frames.groupBy { it.sample.sourceAtNanos }.toSortedMap()) {
            val readings = copies.map { it.sample }.distinct()
            if (readings.size != 1) { seeds.clear(); previous = null; continue }
            val frame = copies.minBy { it.articleId }; val s = frame.sample; val at = s.sourceAtNanos
            val old = previous
            val contiguous = old != null && at - old.sourceAtNanos in 1..250_000_000L &&
                s.previousSourceAtNanos == old.sourceAtNanos && s.sourceWidth == old.sourceWidth &&
                s.sourceHeight == old.sourceHeight && s.bounds == old.bounds &&
                s.publicationStream == old.publicationStream
            if (!contiguous || s.status != "SAMPLED") seeds.clear()
            previous = s
            if (!contiguous || s.status != "SAMPLED" || s.sourceWidth <= 0 || s.sourceHeight <= 0 || s.bounds?.size != 4) continue
            val b = s.bounds
            if (b[0] < 0 || b[1] < 0 || b[2] > s.sourceWidth || b[3] > s.sourceHeight ||
                b[0] >= b[2] || b[1] >= b[3] ||
                (s.publicationSequence != null && old?.publicationSequence != null &&
                    s.publicationSequence <= old.publicationSequence)) { seeds.clear(); continue }
            fun inside(x: Double, y: Double) = x.isFinite() && y.isFinite() &&
                x >= b[0] && x < b[2] && y >= b[1] && y < b[3]
            if (s.flashes.size > 16 || s.points.size > 128 || s.flashes.any {
                    !inside(it.x,it.y) || it.area <= 0 || it.density !in .4..1.0
                } || s.points.any { !inside(it.x,it.y) }) { seeds.clear(); continue }
            seeds.removeAll { at - it.frame.sample.sourceAtNanos > 1_500_000_000L }
            fun near(seed: Seed) = s.flashes.filter {
                hypot(it.x - seed.x, it.y - seed.y) <= .15 * s.sourceWidth
            }.maxByOrNull { it.area }
            var completed: TerminalCollapseSequence? = null
            for (seed in seeds) {
                seed.refs += frame.articleId
                val core = near(seed)
                if (core != null) {
                    seed.clearFrames = 0
                    if (seed.peak == null || core.area > seed.peak!!.area) {
                        seed.peak = core; seed.peakAt = at; seed.contractedAt = null
                    } else if (core.area <= seed.peak!!.area * .5 && at - seed.peakAt <= 800_000_000L) {
                        seed.contractedAt = at
                    }
                } else if (seed.contractedAt?.let { at - it <= 500_000_000L } == true) {
                    if (++seed.clearFrames >= 2) {
                        completed = TerminalCollapseSequence(seed.frame.sample.sourceAtNanos, seed.peakAt,
                            seed.contractedAt!!, at, seed.refs.toList()); break
                    }
                }
            }
            if (completed != null) {
                result += completed; seeds.clear(); cooldown = at + 500_000_000L
            }
            if (s.points.size >= 4 && at >= cooldown) {
                fun median(v: List<Double>): Double = v.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
                val seed = Seed(frame, median(s.points.map { it.x }), median(s.points.map { it.y }))
                seed.refs += frame.articleId
                near(seed)?.let { seed.peak = it; seed.peakAt = at }
                seeds += seed
                // A storm is unavailable, not a truncated positive.
                if (seeds.size > 32) { seeds.clear(); cooldown = at + 1_500_000_000L }
            }
        }
        return result
    }
}
