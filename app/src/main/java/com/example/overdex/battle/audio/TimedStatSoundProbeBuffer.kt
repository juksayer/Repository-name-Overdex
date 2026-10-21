package com.example.overdex.battle.audio

import com.example.overdex.battle.inference.StatChangeTiming

/** Bounded volatile PCM accommodates both delayed OCR and separated visual/audio cues. */
internal class TimedStatSoundProbeBuffer {
    private val samples = ShortArray(20 * StatChangeSoundMatcher.RATE)
    private var next = 0
    private var size = 0
    private var endNanos: Long? = null
    private var endWallMillis = 0L

    sealed interface Read {
        data object Pending : Read
        data object Unavailable : Read
        data class Ready(val audio: Pcm16Audio, val endNanos: Long, val endWallMillis: Long) : Read
    }

    fun reset() { next = 0; size = 0; endNanos = null }

    fun ingest(chunk: LivePcmChunk) {
        for (sample in StatChangeSoundMatcher.mono8k(chunk.audio)) {
            samples[next] = sample
            next = (next + 1) % samples.size
            size = minOf(size + 1, samples.size)
        }
        endNanos = chunk.endMonotonicNanos
        endWallMillis = chunk.endWallTimeMillis
    }

    fun read(announcementAtNanos: Long): Read {
        val latest = endNanos ?: return Read.Pending
        val first = latest - size * SAMPLE_NANOS
        val requestedStart = announcementAtNanos - StatChangeTiming.MAX_CUE_GAP_NANOS
        val requestedEnd = announcementAtNanos + StatChangeTiming.MAX_CUE_GAP_NANOS + 1_000_000_000L
        if (requestedStart < first) return Read.Unavailable
        if (requestedEnd > latest) return Read.Pending
        val from = ((requestedStart - first) / SAMPLE_NANOS).toInt()
        val length = ((requestedEnd - requestedStart) / SAMPLE_NANOS).toInt()
        val oldest = if (size < samples.size) 0 else next
        val audio = Pcm16Audio(StatChangeSoundMatcher.RATE, 1,
            ShortArray(length) { samples[(oldest + from + it) % samples.size] })
        val actualEnd = first + (from + length) * SAMPLE_NANOS
        return Read.Ready(audio, actualEnd, endWallMillis - (latest - actualEnd) / 1_000_000L)
    }

    private companion object { const val SAMPLE_NANOS = 1_000_000_000L / StatChangeSoundMatcher.RATE }
}
