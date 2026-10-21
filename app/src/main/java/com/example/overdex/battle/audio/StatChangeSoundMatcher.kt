package com.example.overdex.battle.audio

import kotlin.math.*

enum class StatChangeCue(val stat: String, val direction: Int, val fileName: String) {
    ATTACK_UP("ATTACK", 1, "buff_attack.wav"),
    DEFENSE_UP("DEFENSE", 1, "buff_defense.wav"),
    ATTACK_DOWN("ATTACK", -1, "debuff_attack.wav"),
    DEFENSE_DOWN("DEFENSE", -1, "debuff_defense.wav"),
}

data class StatSoundReference(val cue: StatChangeCue, val sha256: String, val audio: Pcm16Audio)
data class StatSoundMatch(
    val cue: StatChangeCue, val referenceSha256: String,
    val similarity: Float, val runnerUpSimilarity: Float,
    val offsetNanos: Long, val durationNanos: Long,
)

/** Spectral sequence matching, not a loudness/cry-envelope classification. Scores are not probabilities. */
class StatChangeSoundMatcher(references: List<StatSoundReference>) {
    private data class Prepared(val reference: StatSoundReference, val frames: List<FloatArray>, val length: Int)
    private val prepared = references.map { ref ->
        val mono = mono8k(ref.audio)
        Prepared(ref, features(mono), mono.size)
    }

    fun match(audio: Pcm16Audio): StatSoundMatch? {
        return bestCandidate(audio)?.takeIf(::accepts)
    }

    /** Diagnostic ranking is not an accepted observation. */
    fun bestCandidate(audio: Pcm16Audio): StatSoundMatch? {
        val mono = mono8k(audio)
        if (mono.isEmpty() || mono.maxOf { abs(it.toInt()) } < 120) return null
        val query = features(mono)
        val ranked = prepared.mapNotNull { ref ->
            if (ref.frames.isEmpty() || query.size < ref.frames.size) return@mapNotNull null
            var best = -1f
            var bestStart = 0
            for (start in 0..query.size - ref.frames.size) {
                var sum = 0f
                for (frame in ref.frames.indices) {
                    val expected = ref.frames[frame]
                    val actual = query[start + frame]
                    for (bin in expected.indices) sum += expected[bin] * actual[bin]
                }
                val similarity = sum / ref.frames.size
                if (similarity > best) { best = similarity; bestStart = start }
            }
            Triple(ref, best, bestStart)
        }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)?.second ?: 0f
        return StatSoundMatch(best.first.reference.cue, best.first.reference.sha256,
            best.second, runnerUp, best.third * HOP * 1_000_000_000L / RATE,
            best.first.length * 1_000_000_000L / RATE)
    }

    companion object {
        const val RATE = 8_000
        const val MIN_SIMILARITY = .91f
        const val MIN_MARGIN = .055f
        fun accepts(candidate: StatSoundMatch): Boolean = candidate.similarity >= MIN_SIMILARITY &&
            candidate.similarity - candidate.runnerUpSimilarity >= MIN_MARGIN
        private const val HOP = 128

        /** Explicit channel downmix and sample-rate conversion, shared by reference and capture. */
        fun mono8k(audio: Pcm16Audio): ShortArray {
            if (audio.channelCount <= 0 || audio.sampleRateHz <= 0) return ShortArray(0)
            if (audio.channelCount == 1 && audio.sampleRateHz == RATE) return audio.samples
            val frames = audio.samples.size / audio.channelCount
            if (frames == 0) return ShortArray(0)
            fun sample(frame: Int): Float {
                var sum = 0f
                for (channel in 0 until audio.channelCount) sum += audio.samples[frame * audio.channelCount + channel]
                return sum / audio.channelCount
            }
            return ShortArray((frames.toLong() * RATE / audio.sampleRateHz).toInt()) { index ->
                val at = index.toDouble() * audio.sampleRateHz / RATE
                val left = at.toInt().coerceAtMost(frames - 1)
                val fraction = (at - left).toFloat()
                (sample(left) * (1 - fraction) + sample(minOf(left + 1, frames - 1)) * fraction).toInt().toShort()
            }
        }

        private fun features(samples: ShortArray): List<FloatArray> =
            BattleSoundFeatures.spectra(samples).map(BattleSoundFeatures::normalizedCopy)
    }
}

/** A bounded volatile window. A missing input chunk resets it rather than stitching across a gap. */
class StatSoundWindow {
    private var sequence: Long? = null
    private val samples = ShortArray(12_000)
    private var next = 0
    private var size = 0
    private var sinceScan = 0
    fun reset() { sequence = null; next = 0; size = 0; sinceScan = 0 }
    fun ingest(chunk: LivePcmChunk): Pcm16Audio? {
        if (sequence?.let { chunk.sequence != it + 1 } == true) reset()
        sequence = chunk.sequence
        val mono = StatChangeSoundMatcher.mono8k(chunk.audio)
        for (sample in mono) {
            samples[next] = sample
            next = (next + 1) % samples.size
            size = minOf(size + 1, samples.size)
        }
        sinceScan += mono.size
        if (size < 9_600 || sinceScan < 1_600) return null
        sinceScan = 0
        val first = if (size < samples.size) 0 else next
        return Pcm16Audio(StatChangeSoundMatcher.RATE, 1, ShortArray(size) { samples[(first + it) % samples.size] })
    }
}

/** Transient PCM from the existing recorder; never a new microphone or whole-match recording. */
data class LivePcmChunk(
    val audio: Pcm16Audio, val sequence: Long,
    val endMonotonicNanos: Long, val endWallTimeMillis: Long, val captureSource: String,
)
