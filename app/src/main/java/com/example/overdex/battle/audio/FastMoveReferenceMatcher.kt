package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.FastMoveSoundCandidate
import kotlin.math.*

data class FastMoveSoundReference(val moveName: String, val sha256: String, val audio: Pcm16Audio)

/**
 * Compares a 400 ms spectral sequence against the entire local catalogue. Both the original
 * spectrum and its changing component must agree. A loud clip or a legal move alone cannot win.
 * Offsets describe the matched portion; reference length is NOT the PvP turn duration.
 */
class FastMoveReferenceMatcher(references: List<FastMoveSoundReference>) {
    private data class Features(val original: List<FloatArray>, val changing: List<FloatArray>)
    private data class Prepared(val reference: FastMoveSoundReference, val features: Features)
    private data class Fit(val score: Float, val queryStart: Int, val referenceStart: Int, val frames: Int)
    private val prepared = references.map { Prepared(it, features(StatChangeSoundMatcher.mono8k(it.audio))) }

    fun measure(audio: Pcm16Audio): List<FastMoveSoundCandidate> = measureMono8k(StatChangeSoundMatcher.mono8k(audio))

    internal fun measureMono8k(mono: ShortArray): List<FastMoveSoundCandidate> {
        if (mono.size < SIZE + (MATCH_FRAMES - 1) * HOP || mono.maxOf { abs(it.toInt()) } < 120) return emptyList()
        val query = features(mono)
        return prepared.mapNotNull { ref ->
            val original = fit(query.original, ref.features.original) ?: return@mapNotNull null
            val changing = fit(query.changing, ref.features.changing) ?: return@mapNotNull null
            FastMoveSoundCandidate(ref.reference.moveName, ref.reference.sha256,
                original.score, changing.score,
                changing.queryStart * HOP_NANOS, changing.referenceStart * HOP_NANOS,
                ((changing.frames - 1) * HOP + SIZE) * 1_000_000_000L / RATE)
        }.groupBy { it.moveName }.map { (_, variants) -> variants.maxBy { it.similarity } }
            .sortedByDescending { it.similarity }
    }

    private fun fit(query: List<FloatArray>, reference: List<FloatArray>): Fit? {
        if (min(query.size, reference.size) < MATCH_FRAMES) return null
        var best = Fit(-1f, 0, 0, MATCH_FRAMES)
        // Sliding diagonal sums avoid rescoring the same 400 ms segment at every offset.
        val ring = FloatArray(MATCH_FRAMES)
        for (offset in -query.size + MATCH_FRAMES..reference.size - MATCH_FRAMES) {
            val q0 = max(0, -offset)
            val r0 = max(0, offset)
            val length = min(query.size - q0, reference.size - r0)
            var sum = 0f
            for (i in 0 until length) {
                val q = query[q0 + i]; val r = reference[r0 + i]
                var dot = 0f
                var bin = 0
                // Fixed 64-bin vectors. Unrolling avoids ART's per-bin loop overhead while
                // preserving the full catalogue and both independent score thresholds.
                while (bin < 64) {
                    dot += q[bin] * r[bin] + q[bin + 1] * r[bin + 1] +
                        q[bin + 2] * r[bin + 2] + q[bin + 3] * r[bin + 3] +
                        q[bin + 4] * r[bin + 4] + q[bin + 5] * r[bin + 5] +
                        q[bin + 6] * r[bin + 6] + q[bin + 7] * r[bin + 7]
                    bin += 8
                }
                val slot = i % MATCH_FRAMES
                if (i >= MATCH_FRAMES) sum -= ring[slot]
                ring[slot] = dot
                sum += dot
                if (i >= MATCH_FRAMES - 1 && sum / MATCH_FRAMES > best.score) {
                    best = Fit((sum / MATCH_FRAMES).coerceIn(0f, 1f),
                        q0 + i - MATCH_FRAMES + 1, r0 + i - MATCH_FRAMES + 1, MATCH_FRAMES)
                }
            }
        }
        return best
    }

    private fun features(samples: ShortArray): Features {
        if (samples.size < SIZE) return Features(emptyList(), emptyList())
        val spectra = BattleSoundFeatures.spectra(samples)
        val floor = FloatArray(64) { bin ->
            val values = spectra.map { it[bin] }.sorted()
            val at = (values.size - 1) * .2f
            val left = at.toInt()
            values[left] + (values[min(left + 1, values.lastIndex)] - values[left]) * (at - left)
        }
        fun normalized(values: FloatArray): FloatArray {
            val norm = sqrt(values.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-9f)
            for (bin in values.indices) values[bin] /= norm
            return values
        }
        return Features(spectra.map { normalized(it.copyOf()) },
            spectra.map { spectrum -> normalized(FloatArray(spectrum.size) { max(0f, spectrum[it] - floor[it]) }) })
    }

    companion object {
        const val VERSION = "spectral-sequence-v1"
        private const val RATE = 8_000
        private const val SIZE = 256
        private const val HOP = 128
        private const val HOP_NANOS = 16_000_000L
        private const val MATCH_FRAMES = 25

        /** Retention/presentation floor only, never a confirmed match or a probability. */
        fun plausibleCandidate(candidates: List<FastMoveSoundCandidate>): FastMoveSoundCandidate? {
            if (candidates.size < 2) return null
            return candidates.maxByOrNull { it.similarity }?.takeIf {
                it.similarity.isFinite() && it.changingSimilarity.isFinite() &&
                    it.similarity >= .85f && it.changingSimilarity >= .65f
            }
        }

        /** Scores are similarities, not probabilities. Unknown and mixed sounds can be rejected. */
        fun accepts(candidates: List<FastMoveSoundCandidate>): Boolean {
            val ranked = candidates.sortedByDescending { it.similarity }
            val best = ranked.firstOrNull() ?: return false
            val second = ranked.getOrNull(1) ?: return false // Never force a one-reference choice.
            val otherChanging = ranked.drop(1).maxOf { it.changingSimilarity }
            return best.similarity >= .93f && best.similarity - second.similarity >= .008f &&
                best.changingSimilarity >= .80f && best.changingSimilarity - otherChanging >= .04f
        }
    }
}
