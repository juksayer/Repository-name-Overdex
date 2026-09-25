package com.example.overdex.battle.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Pcm16Audio(val sampleRateHz: Int, val channelCount: Int, val samples: ShortArray)

/** Strict reader for the PCM-16 WAV artifacts and references used by Droidball. */
object WavPcm16 {
    fun decode(bytes: ByteArray): Pcm16Audio? {
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var channels = 0
        var sampleRate = 0
        var data: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = buffer.getInt(offset + 4)
            val dataStart = offset + 8
            if (size < 0 || dataStart + size > bytes.size) return null
            when (id) {
                "fmt " -> {
                    if (size < 16 || buffer.getShort(dataStart).toInt() != 1 || buffer.getShort(dataStart + 14).toInt() != 16) return null
                    channels = buffer.getShort(dataStart + 2).toInt()
                    sampleRate = buffer.getInt(dataStart + 4)
                }
                "data" -> data = bytes.copyOfRange(dataStart, dataStart + size)
            }
            offset = dataStart + size + (size and 1)
        }
        val pcm = data ?: return null
        if (channels <= 0 || sampleRate <= 0 || pcm.size % 2 != 0) return null
        return Pcm16Audio(sampleRate, channels, ShortArray(pcm.size / 2) { i -> ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).getShort(i * 2) })
    }
}

data class CryCandidate(val speciesId: Int, val referenceSha256: String, val similarity: Float)

/**
 * A deliberately modest acoustic measurement: normalized short-window energy envelopes. It ranks
 * possible references but does not assert species identity; mic acoustics need corroboration.
 */
object CryAcousticMatcher {
    fun rank(query: Pcm16Audio, references: List<Pair<CryReference, Pcm16Audio>>, limit: Int = 5): List<CryCandidate> {
        val queryEnvelope = envelope(query.samples, 96)
        return references.map { (reference, audio) ->
            val referenceEnvelope = envelope(audio.samples, 48)
            CryCandidate(reference.speciesId, reference.sha256, bestWindowSimilarity(queryEnvelope, referenceEnvelope))
        }.sortedByDescending { it.similarity }.take(limit)
    }

    private fun envelope(samples: ShortArray, bins: Int): FloatArray {
        if (samples.isEmpty()) return FloatArray(bins)
        return FloatArray(bins) { bin ->
            val start = bin * samples.size / bins
            val end = maxOf(start + 1, (bin + 1) * samples.size / bins)
            var energy = 0.0
            for (i in start until end.coerceAtMost(samples.size)) energy += samples[i].toDouble() * samples[i]
            kotlin.math.sqrt(energy / (end - start)).toFloat()
        }.also { values ->
            val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
            for (i in values.indices) values[i] /= max
        }
    }

    private fun bestWindowSimilarity(query: FloatArray, reference: FloatArray): Float {
        if (reference.size > query.size) return 0f
        var best = 0f
        for (start in 0..query.size - reference.size) {
            var error = 0f
            for (i in reference.indices) error += kotlin.math.abs(query[start + i] - reference[i])
            best = maxOf(best, (1f - error / reference.size).coerceIn(0f, 1f))
        }
        return best
    }
}
