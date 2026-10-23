package com.example.overdex.battle.audio

import kotlin.math.abs

internal data class FastMoveAudioWindowSnapshot(
    val samples: ShortArray,
    val endNanos: Long,
    val endWallMillis: Long,
    val captureSource: String,
    val sequence: Long,
    val generation: Long,
) {
    val durationNanos: Long get() = samples.size * 1_000_000_000L / FastMoveAudioWindow.RATE
    val startNanos: Long get() = endNanos - durationNanos
}

/** A 1.2-second mono analysis projection of the shared recorder, with 200 ms overlap steps.
 * No disk writes, full-match buffer, species requirement, or visual impact trigger.
 */
internal class FastMoveAudioWindow {
    private val samples = ShortArray(WINDOW_SAMPLES)
    private var next = 0
    private var size = 0
    private var sinceScan = 0
    private var last: LivePcmChunk? = null
    private var sequence = 0L
    val ready: Boolean get() = size == WINDOW_SAMPLES

    fun reset() { next = 0; size = 0; sinceScan = 0; last = null }

    fun ingest(chunk: LivePcmChunk, generation: Long = 0): FastMoveAudioWindowSnapshot? {
        val audio = chunk.audio
        if (audio.sampleRateHz <= 0 || audio.channelCount !in 1..2 ||
            audio.samples.size % audio.channelCount != 0) { reset(); return null }
        val duration = audio.samples.size * 1_000_000_000L / audio.sampleRateHz / audio.channelCount
        val previous = last
        if (previous != null && (chunk.sequence != previous.sequence + 1 ||
                chunk.captureSource != previous.captureSource ||
                audio.sampleRateHz != previous.audio.sampleRateHz || audio.channelCount != previous.audio.channelCount ||
                abs(chunk.endMonotonicNanos - previous.endMonotonicNanos - duration) > 100_000_000L)) reset()
        last = chunk
        val mono = StatChangeSoundMatcher.mono8k(audio)
        for (sample in mono) {
            samples[next] = sample; next = (next + 1) % samples.size
            size = minOf(size + 1, samples.size)
        }
        sinceScan += mono.size
        if (!ready || sinceScan < STEP_SAMPLES) return null
        sinceScan = 0
        return FastMoveAudioWindowSnapshot(ShortArray(size) { samples[(next + it) % samples.size] },
            chunk.endMonotonicNanos, chunk.endWallTimeMillis, chunk.captureSource, sequence++, generation)
    }

    companion object {
        const val RATE = 8_000
        const val WINDOW_SAMPLES = 9_600
        const val STEP_SAMPLES = 1_600
    }
}
