package com.example.overdex.battle.audio

import kotlin.math.*

enum class ResultMusicTheme(val isResult: Boolean) {
    WIN(true), LOSS(true), BATTLE(false), TEAM_SELECT(false)
}

data class ResultMusicReference(val theme: ResultMusicTheme, val sha256: String, val audio: Pcm16Audio)
data class ResultMusicMatch(
    val theme: ResultMusicTheme, val referenceSha256: String,
    val similarity: Float, val runnerUpSimilarity: Float, val referenceOffsetNanos: Long,
)

/** Matches a sequence of spectra against the actual tracks, including non-result distractors.
 * Gain is normalized; neither loudness nor a quiet input is evidence of a result.
 * Scores are similarities, not probabilities. No battle lifecycle decisions belong here.
 */
class ResultMusicMatcher(references: List<ResultMusicReference>) {
    private data class Prepared(val theme: ResultMusicTheme, val sha: String, val frames: Array<FloatArray>)
    private val prepared = references.map {
        Prepared(it.theme, it.sha256, features(StatChangeSoundMatcher.mono8k(it.audio)))
    }

    fun match(audio: Pcm16Audio): ResultMusicMatch? = bestCandidate(audio)?.takeIf(::accepts)

    fun bestCandidate(audio: Pcm16Audio): ResultMusicMatch? {
        val samples = StatChangeSoundMatcher.mono8k(audio)
        if (samples.size < QUERY_SAMPLES) return null
        val querySamples = samples.copyOfRange(samples.size - QUERY_SAMPLES, samples.size)
        // Reject numerical noise without requiring the user's volume to be loud.
        val rms = sqrt(querySamples.sumOf { it.toDouble() * it } / querySamples.size)
        if (rms < 24) return null
        val query = features(querySamples)
        val ranked = prepared.mapNotNull { reference ->
            if (reference.frames.size < query.size) return@mapNotNull null
            // Locate promising sequence positions cheaply, then evaluate complete windows
            // around those positions. Scanning every bin at every position throughout a
            // two-minute battle track would compete with the live visual witnesses.
            val lastStart = reference.frames.size - query.size
            val candidates = mutableListOf<Pair<Int, Float>>()
            for (start in 0..lastStart step 4) {
                var score = 0f
                repeat(12) { anchor ->
                    val frame = anchor * (query.lastIndex) / 11
                    val actual = query[frame]
                    val expected = reference.frames[start + frame]
                    for (bin in actual.indices) score += actual[bin] * expected[bin]
                }
                if (candidates.size < 16 || score > candidates.last().second) {
                    val position = candidates.indexOfFirst { score > it.second }.let { if (it < 0) candidates.size else it }
                    candidates.add(position, start to score)
                    if (candidates.size > 16) candidates.removeAt(candidates.lastIndex)
                }
            }
            var best = -1f
            var offset = 0
            val starts = candidates.flatMap { (start, _) -> (maxOf(0, start - 3)..minOf(lastStart, start + 3)).toList() }.toSet()
            for (start in starts) {
                var sum = 0f
                var count = 0
                // 32 ms query steps, with 16 ms reference alignment, bound the CPU cost.
                for (frame in query.indices step 2) {
                    val actual = query[frame]
                    val expected = reference.frames[start + frame]
                    for (bin in actual.indices) sum += actual[bin] * expected[bin]
                    count++
                }
                val score = sum / count
                if (score > best) { best = score; offset = start }
            }
            ResultMusicMatch(reference.theme, reference.sha, best, 0f, offset * HOP * 1_000_000_000L / RATE)
        }.groupBy { it.theme }.map { (_, matches) -> matches.maxBy { it.similarity } }
            .sortedByDescending { it.similarity }
        val first = ranked.firstOrNull() ?: return null
        return first.copy(runnerUpSimilarity = ranked.getOrNull(1)?.similarity ?: 0f)
    }

    companion object {
        const val RATE = 8_000
        const val QUERY_SAMPLES = 3 * RATE
        const val MIN_SIMILARITY = .90f
        const val MIN_MARGIN = .065f
        private const val SIZE = 256
        private const val HOP = 128
        private val window = FloatArray(SIZE) { (.5 - .5 * cos(2 * PI * it / (SIZE - 1))).toFloat() }
        private val coefficients = FloatArray(64) { (2 * cos(2 * PI * (it * 2 + 1) / SIZE)).toFloat() }

        fun accepts(found: ResultMusicMatch): Boolean = found.theme.isResult &&
            found.similarity >= MIN_SIMILARITY && found.similarity - found.runnerUpSimilarity >= MIN_MARGIN

        private fun features(samples: ShortArray): Array<FloatArray> {
            if (samples.size < SIZE) return emptyArray()
            return Array((samples.size - SIZE) / HOP + 1) { frame ->
                val start = frame * HOP
                val input = FloatArray(SIZE) { samples[start + it] / 32768f * window[it] }
                FloatArray(coefficients.size) { bin ->
                    val coefficient = coefficients[bin]
                    var previous = 0f
                    var older = 0f
                    for (sample in input) {
                        val next = sample + coefficient * previous - older
                        older = previous; previous = next
                    }
                    sqrt(sqrt(maxOf(0f, previous * previous + older * older - coefficient * previous * older)))
                }.also { spectrum ->
                    // A shared broadband/noise floor must not count as matching notes.
                    val mean = spectrum.average().toFloat()
                    for (bin in spectrum.indices) spectrum[bin] -= mean
                    val norm = sqrt(spectrum.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-9f)
                    for (bin in spectrum.indices) spectrum[bin] /= norm
                }
            }
        }
    }
}
