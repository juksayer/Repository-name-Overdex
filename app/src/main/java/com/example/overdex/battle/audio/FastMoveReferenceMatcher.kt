package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.FastMoveSoundCandidate
import kotlin.math.*

data class FastMoveSoundReference(val moveName: String, val sha256: String, val audio: Pcm16Audio)

/**
 * Compares up to a 416 ms spectral sequence against eligible local references. Both the original
 * spectrum and its changing component describe the same aligned passage. A loud clip or a legal
 * move alone cannot win.
 * Offsets describe the matched portion; reference length is NOT the PvP turn duration.
 */
class FastMoveReferenceMatcher(references: List<FastMoveSoundReference>) {
    private data class Features(val original: List<FloatArray>, val changing: List<FloatArray>)
    private data class Prepared(val reference: FastMoveSoundReference, val key: String, val features: Features)
    private val prepared = references.map { Prepared(it, moveKey(it.moveName), features(StatChangeSoundMatcher.mono8k(it.audio))) }

    /** Full-catalogue comparison retained for offline diagnostics. Live callers supply an allowlist. */
    fun measure(audio: Pcm16Audio): List<FastMoveSoundCandidate> = measureMono8k(StatChangeSoundMatcher.mono8k(audio))

    fun measure(audio: Pcm16Audio, allowedMoveNames: Set<String>): List<FastMoveSoundCandidate> {
        val eligible = allowedReferences(allowedMoveNames)
        if (eligible.isEmpty()) return emptyList()
        return measureReferences(StatChangeSoundMatcher.mono8k(audio), eligible)
    }

    internal fun measureMono8k(mono: ShortArray): List<FastMoveSoundCandidate> = measureReferences(mono, prepared)

    internal fun measureMono8k(mono: ShortArray, allowedMoveNames: Set<String>): List<FastMoveSoundCandidate> =
        measureReferences(mono, allowedReferences(allowedMoveNames))

    private fun allowedReferences(names: Set<String>): List<Prepared> {
        val keys = names.map(::moveKey).filter(String::isNotEmpty).toSet()
        return if (keys.isEmpty()) emptyList() else prepared.filter { it.key in keys }
    }

    private fun measureReferences(mono: ShortArray, eligible: List<Prepared>): List<FastMoveSoundCandidate> {
        // An unknown roster or missing reference does not authorize a full-catalogue fallback.
        // Gate before query extraction and fitting: impossible moves are never scored.
        if (eligible.isEmpty()) return emptyList()
        if (mono.size < SIZE + (MATCH_FRAMES - 1) * HOP || mono.maxOf { abs(it.toInt()) } < 120) return emptyList()
        val query = features(mono)
        return eligible.mapNotNull { ref ->
            val frames = min(MATCH_FRAMES, ref.features.original.size)
            if (frames < MIN_REFERENCE_FRAMES) return@mapNotNull null
            val fit = fitCoherentSequences(query.original, query.changing,
                ref.features.original, ref.features.changing, frames) ?: return@mapNotNull null
            FastMoveSoundCandidate(ref.reference.moveName, ref.reference.sha256,
                fit.originalSimilarity, fit.changingSimilarity,
                fit.queryStart * HOP_NANOS, fit.referenceStart * HOP_NANOS,
                ((fit.frames - 1) * HOP + SIZE) * 1_000_000_000L / RATE)
        }.groupBy { it.moveName }.map { (_, variants) -> variants.maxBy { it.similarity } }
            .sortedByDescending { it.similarity }
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
        const val VERSION = "spectral-sequence-v3"

        /**
         * Historical v1/v2 scores remain readable. Their two spectral maxima may describe
         * different passages, so only v3 can support inference requiring a coherent pair.
         */
        internal fun supportsAnalysisVersion(version: String?): Boolean =
            version == "spectral-sequence-v1" || version == "spectral-sequence-v2" || version == VERSION

        internal data class CoherentFit(
            val originalSimilarity: Float,
            val changingSimilarity: Float,
            val queryStart: Int,
            val referenceStart: Int,
            val frames: Int,
        )

        /** Both scores and offsets refer to one passage, chosen by their mean similarity. */
        internal fun fitCoherentSequences(
            queryOriginal: List<FloatArray>, queryChanging: List<FloatArray>,
            referenceOriginal: List<FloatArray>, referenceChanging: List<FloatArray>, frames: Int,
        ): CoherentFit? {
            if (frames < MIN_REFERENCE_FRAMES || queryOriginal.size < frames ||
                referenceOriginal.size < frames || queryChanging.size != queryOriginal.size ||
                referenceChanging.size != referenceOriginal.size) return null
            var best: CoherentFit? = null
            var bestMean = -1f
            // Sliding diagonal sums keep the two components on exactly the same source frames.
            val originalRing = FloatArray(frames)
            val changingRing = FloatArray(frames)
            for (offset in -queryOriginal.size + frames..referenceOriginal.size - frames) {
                val q0 = max(0, -offset)
                val r0 = max(0, offset)
                val length = min(queryOriginal.size - q0, referenceOriginal.size - r0)
                var originalSum = 0f
                var changingSum = 0f
                for (i in 0 until length) {
                    val q = queryOriginal[q0 + i]; val r = referenceOriginal[r0 + i]
                    val qc = queryChanging[q0 + i]; val rc = referenceChanging[r0 + i]
                    var originalDot = 0f
                    var changingDot = 0f
                    var bin = 0
                    while (bin < 64) {
                        originalDot += q[bin] * r[bin] + q[bin + 1] * r[bin + 1] +
                            q[bin + 2] * r[bin + 2] + q[bin + 3] * r[bin + 3] +
                            q[bin + 4] * r[bin + 4] + q[bin + 5] * r[bin + 5] +
                            q[bin + 6] * r[bin + 6] + q[bin + 7] * r[bin + 7]
                        changingDot += qc[bin] * rc[bin] + qc[bin + 1] * rc[bin + 1] +
                            qc[bin + 2] * rc[bin + 2] + qc[bin + 3] * rc[bin + 3] +
                            qc[bin + 4] * rc[bin + 4] + qc[bin + 5] * rc[bin + 5] +
                            qc[bin + 6] * rc[bin + 6] + qc[bin + 7] * rc[bin + 7]
                        bin += 8
                    }
                    val slot = i % frames
                    if (i >= frames) {
                        originalSum -= originalRing[slot]
                        changingSum -= changingRing[slot]
                    }
                    originalRing[slot] = originalDot
                    changingRing[slot] = changingDot
                    originalSum += originalDot
                    changingSum += changingDot
                    if (i < frames - 1) continue
                    val original = (originalSum / frames).coerceIn(0f, 1f)
                    val changing = (changingSum / frames).coerceIn(0f, 1f)
                    val mean = (original + changing) * .5f
                    if (mean > bestMean) {
                        bestMean = mean
                        best = CoherentFit(original, changing,
                            q0 + i - frames + 1, r0 + i - frames + 1, frames)
                    }
                }
            }
            return best
        }

        private const val RATE = 8_000
        private const val SIZE = 256
        private const val HOP = 128
        private const val HOP_NANOS = 16_000_000L
        private const val MATCH_FRAMES = 25
        // 21 overlapping windows span 352 ms, the shortest complete bundled effect.
        private const val MIN_REFERENCE_FRAMES = 21
        private fun moveKey(name: String) = name.uppercase().filter(Char::isLetterOrDigit)

        /** Retention/presentation floor only, never a confirmed match or a probability. */
        fun plausibleCandidate(candidates: List<FastMoveSoundCandidate>): FastMoveSoundCandidate? {
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
