package com.example.overdex.battle.replay

import com.example.overdex.battle.audio.CryWaveformMatcher
import com.example.overdex.battle.audio.CryWaveformReference
import com.example.overdex.battle.audio.Pcm16Audio
import com.example.overdex.battle.audio.StatChangeSoundMatcher
import kotlin.math.abs

/** Exploratory acoustic evidence only. It does not establish a move use, side, hit or faint. */
data class ReplayMixedAudioCandidate(
    val moveName: String,
    val estimatedOnsetNanos: Long,
    val fragments: List<ReplayAudioFragment>,
)

/** Finds coherent nonoverlapping parts of each reference independently, allowing mixed sounds. */
internal object ReplayMixedAudioRecovery {
    private const val RATE = 8_000
    private const val FRAGMENT = 3_200
    private const val STRIDE = 1_600
    private const val MIN_SCORE = .20f
    private const val STRONG_SCORE = .30f
    private const val ONSET_TOLERANCE = 5_000_000L

    fun measure(audio: Pcm16Audio, articleId: String, capturedAtNanos: Long, audioSha256: String,
                references: List<ReplayAudioReference>): List<ReplayMixedAudioCandidate> {
        val query = StatChangeSoundMatcher.mono8k(audio)
        if (query.size !in FRAGMENT..(RATE * 4) || articleId.isBlank() || audioSha256.isBlank()) return emptyList()
        return references.asSequence().filter { !it.charged && it.speciesId == null }
            .distinctBy { it.name to it.sha256 }.mapNotNull { reference ->
                val samples = StatChangeSoundMatcher.mono8k(reference.audio)
                if (samples.size !in (FRAGMENT * 2)..(RATE * 8)) return@mapNotNull null
                val offsets = (0..samples.size - FRAGMENT step STRIDE).toList()
                if (offsets.size > CryWaveformMatcher.MAX_REFERENCES) return@mapNotNull null
                val matcher = CryWaveformMatcher(offsets.mapIndexed { i, off ->
                    CryWaveformReference(i + 1, reference.sha256,
                        Pcm16Audio(RATE, 1, samples.copyOfRange(off, off + FRAGMENT)))
                })
                val fragments = matcher.measureMono8k(query).map { fit ->
                    ReplayAudioFragment(articleId, capturedAtNanos, audioSha256, reference.sha256,
                        fit.offsetNanos, offsets[fit.speciesId - 1].toLong() * 1_000_000_000L / RATE,
                        fit.durationNanos, fit.correlation)
                }.filter { it.correlation >= MIN_SCORE }
                val pairs = fragments.flatMapIndexed { i, first -> fragments.drop(i + 1).mapNotNull { second ->
                    if (abs(first.referenceOffsetNanos - second.referenceOffsetNanos) < first.durationNanos ||
                        abs(first.estimatedOnsetNanos - second.estimatedOnsetNanos) > ONSET_TOLERANCE ||
                        maxOf(first.correlation, second.correlation) < STRONG_SCORE) null else listOf(first, second)
                } }
                val best = pairs.maxByOrNull { minOf(it[0].correlation, it[1].correlation) } ?: return@mapNotNull null
                ReplayMixedAudioCandidate(reference.name,
                    best.minOf { it.estimatedOnsetNanos } + abs(best[0].estimatedOnsetNanos - best[1].estimatedOnsetNanos) / 2,
                    best)
            }.toList()
    }
}
