package com.example.overdex.battle.audio

import kotlin.math.abs

internal data class CryAudioWindowSnapshot(
    val audio: Pcm16Audio,
    val endNanos: Long,
    val endWallMillis: Long,
    val captureSource: String,
    val sequence: Long,
    val generation: Long,
) {
    val durationNanos: Long get() = audio.samples.size.toLong() * 1_000_000_000L / audio.sampleRateHz / audio.channelCount
    val startNanos: Long get() = endNanos - durationNanos
}

/**
 * Four seconds of original interleaved PCM, scanned at most four times per second.
 * Resampling belongs to the analysis worker; per-chunk rounding must not shift cry onset.
 * Capture gaps and format changes clear history, never joining unrelated audio into a cry.
 */
internal class CryAudioWindow {
    private var buffer = ShortArray(0)
    private var next = 0
    private var size = 0
    private var sinceScan = 0
    private var last: LivePcmChunk? = null
    private var generation: Long? = null
    private var sequence = 0L

    val ready: Boolean get() = buffer.isNotEmpty() && size == buffer.size

    fun reset() { buffer=ShortArray(0); next=0; size=0; sinceScan=0; last=null; generation=null }

    fun ingest(chunk: LivePcmChunk, currentGeneration: Long): CryAudioWindowSnapshot? {
        val audio=chunk.audio
        if (audio.sampleRateHz !in 8_000..48_000 || audio.channelCount !in 1..2 ||
            audio.samples.isEmpty() || audio.samples.size % audio.channelCount != 0) { reset();return null }
        val duration=audio.samples.size.toLong()*1_000_000_000L/audio.sampleRateHz/audio.channelCount
        val previous=last
        if (generation != currentGeneration || previous != null && (
                chunk.sequence != previous.sequence+1 || chunk.captureSource != previous.captureSource ||
                audio.sampleRateHz != previous.audio.sampleRateHz || audio.channelCount != previous.audio.channelCount ||
                chunk.endMonotonicNanos <= previous.endMonotonicNanos ||
                abs(chunk.endMonotonicNanos-previous.endMonotonicNanos-duration)>100_000_000L)) reset()
        generation=currentGeneration
        if (buffer.isEmpty()) buffer=ShortArray(audio.sampleRateHz*audio.channelCount*4)
        // Oversized input keeps only its recent four seconds; stored memory is always bounded.
        val from=(audio.samples.size-buffer.size).coerceAtLeast(0)
        for(i in from until audio.samples.size) {
            buffer[next]=audio.samples[i];next=(next+1)%buffer.size
        }
        size=minOf(size+audio.samples.size,buffer.size)
        sinceScan+=minOf(audio.samples.size,buffer.size)
        last=chunk
        if(size<buffer.size || sinceScan<audio.sampleRateHz*audio.channelCount/4)return null
        sinceScan=0
        return CryAudioWindowSnapshot(Pcm16Audio(audio.sampleRateHz,audio.channelCount,
            ShortArray(buffer.size) { buffer[(next+it)%buffer.size] }),chunk.endMonotonicNanos,
            chunk.endWallTimeMillis,chunk.captureSource,sequence++,currentGeneration)
    }
}
