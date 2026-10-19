package com.example.overdex.battle.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Pcm16Audio(val sampleRateHz: Int, val channelCount: Int, val samples: ShortArray) {
    /** Recognition projection only; the interleaved source samples are never changed. */
    fun monoSamples(): ShortArray {
        require(channelCount > 0 && samples.size % channelCount == 0)
        if (channelCount == 1) return samples
        return ShortArray(samples.size / channelCount) { frame ->
            var sum = 0L
            for (channel in 0 until channelCount) sum += samples[frame * channelCount + channel]
            (sum / channelCount).toShort()
        }
    }
}

/** Strict reader for the PCM-16 WAV artifacts and references used by Droidball. */
object WavPcm16 {
    /**
     * Some verified bundled exports have an 18-byte PCM format header but leave
     * both RIFF/data lengths at zero. Recover lengths from that known layout in
     * a temporary copy. Captured match evidence still uses strict [decode].
     */
    internal fun decodeBundledReference(bytes: ByteArray): Pcm16Audio? {
        decode(bytes)?.let { return it }
        if (bytes.size <= 46 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE" ||
            String(bytes, 12, 4) != "fmt " || String(bytes, 38, 4) != "data") return null
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val channels = header.getShort(22).toInt()
        if (header.getInt(4) != 0 || header.getInt(16) != 18 || header.getInt(42) != 0 ||
            header.getShort(20).toInt() != 1 || header.getShort(34).toInt() != 16 ||
            header.getShort(36).toInt() != 0 || channels !in 1..2 ||
            header.getShort(32).toInt() != channels * 2 || (bytes.size - 46) % (channels * 2) != 0) return null
        val finalized = bytes.copyOf()
        ByteBuffer.wrap(finalized).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, finalized.size - 8)
            putInt(42, finalized.size - 46)
        }
        return decode(finalized)
    }

    fun decode(bytes: ByteArray): Pcm16Audio? {
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt(4) <= 0) return null // unfinished streams are not complete captured evidence
        var offset = 12
        var channels = 0
        var sampleRate = 0
        var data: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = buffer.getInt(offset + 4)
            val dataStart = offset + 8
            if (size < 0 || size > bytes.size - dataStart) return null
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
        if (channels <= 0 || sampleRate <= 0 || pcm.size % (channels * 2) != 0) return null
        // A 1.2-second capture contains 57,600 samples. Wrapping a new buffer
        // per sample produced that many temporary objects for every witness,
        // plus millions while preparing the cry corpus at match startup.
        val samples = ShortArray(pcm.size / 2)
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        return Pcm16Audio(sampleRate, channels, samples)
    }
}

data class CryCandidate(val speciesId: Int, val referenceSha256: String, val similarity: Float)

/**
 * A deliberately modest acoustic measurement: normalized short-window energy envelopes. It ranks
 * possible references but does not assert species identity; mic acoustics need corroboration.
 */
object CryAcousticMatcher {
    data class PreparedReference(val reference: CryReference, val envelope: FloatArray)
    fun prepare(reference: CryReference, audio: Pcm16Audio) = PreparedReference(reference, envelope(audio.monoSamples(), 48))
    fun rankPrepared(query: Pcm16Audio, references: List<PreparedReference>, limit: Int = 5): List<CryCandidate> {
        val queryEnvelope = envelope(query.monoSamples(), 96)
        return references.map { (reference, referenceEnvelope) ->
            CryCandidate(reference.speciesId, reference.sha256, bestWindowSimilarity(queryEnvelope, referenceEnvelope))
        }.sortedByDescending { it.similarity }.take(limit)
    }

    fun rank(query: Pcm16Audio, references: List<Pair<CryReference, Pcm16Audio>>, limit: Int = 5): List<CryCandidate> {
        val queryEnvelope = envelope(query.monoSamples(), 96)
        return references.map { (reference, audio) ->
            val referenceEnvelope = envelope(audio.monoSamples(), 48)
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
