package com.example.overdex.battle.audio

import kotlin.math.abs

data class ResultMusicWindowSnapshot(
    val audio: Pcm16Audio, val endNanos: Long, val endWallMillis: Long,
    val captureSource: String, val continuity: Long,
) {
    val durationNanos: Long get() = audio.samples.size * 1_000_000_000L / audio.sampleRateHz
}

/** Four seconds of volatile PCM; recognition gets at most one snapshot per second.
 * Sequence gaps, source/format changes and clock discontinuities discard continuity.
 */
class ResultMusicWindow {
    private val samples = ShortArray(ResultMusicMatcher.RATE * 4)
    private var next = 0
    private var size = 0
    private var sinceScan = 0
    private var last: LivePcmChunk? = null
    private var continuity = 0L
    val ready: Boolean get() = size >= ResultMusicMatcher.QUERY_SAMPLES

    fun reset() { next = 0; size = 0; sinceScan = 0; last = null; continuity++ }

    fun ingest(chunk: LivePcmChunk): ResultMusicWindowSnapshot? {
        if (chunk.audio.sampleRateHz <= 0 || chunk.audio.channelCount <= 0) { reset(); return null }
        val duration = chunk.audio.samples.size * 1_000_000_000L / chunk.audio.sampleRateHz / chunk.audio.channelCount
        val previous = last
        if (previous != null && (chunk.sequence != previous.sequence + 1 ||
                chunk.captureSource != previous.captureSource ||
                chunk.audio.sampleRateHz != previous.audio.sampleRateHz ||
                chunk.audio.channelCount != previous.audio.channelCount ||
                abs(chunk.endMonotonicNanos - previous.endMonotonicNanos - duration) > 100_000_000L)) reset()
        last = chunk
        val mono = StatChangeSoundMatcher.mono8k(chunk.audio)
        for (sample in mono) {
            samples[next] = sample; next = (next + 1) % samples.size
            size = minOf(size + 1, samples.size)
        }
        sinceScan += mono.size
        if (size < ResultMusicMatcher.QUERY_SAMPLES || sinceScan < ResultMusicMatcher.RATE) return null
        sinceScan = 0
        val first = if (size < samples.size) 0 else next
        return ResultMusicWindowSnapshot(Pcm16Audio(ResultMusicMatcher.RATE, 1,
            ShortArray(size) { samples[(first + it) % samples.size] }),
            chunk.endMonotonicNanos, chunk.endWallTimeMillis, chunk.captureSource, continuity)
    }
}

/** Two consecutive accepted windows must follow the same track at the same speed. */
class ResultMusicConfirmation {
    private var previous: Pair<ResultMusicWindowSnapshot, ResultMusicMatch>? = null
    fun reset() { previous = null }
    fun accept(window: ResultMusicWindowSnapshot, found: ResultMusicMatch?): Boolean {
        if (found == null || !ResultMusicMatcher.accepts(found)) { reset(); return false }
        val last = previous
        previous = window to found
        if (last == null || last.first.continuity != window.continuity ||
            last.first.captureSource != window.captureSource || last.second.theme != found.theme ||
            last.second.referenceSha256 != found.referenceSha256) return false
        val elapsed = window.endNanos - last.first.endNanos
        return elapsed in 900_000_000L..1_100_000_000L &&
            abs(found.referenceOffsetNanos - last.second.referenceOffsetNanos - elapsed) <= 80_000_000L
    }
}
