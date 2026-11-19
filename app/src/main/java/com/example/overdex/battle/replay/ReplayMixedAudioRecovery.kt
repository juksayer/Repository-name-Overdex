package com.example.overdex.battle.replay

import kotlinx.serialization.Serializable
import com.example.overdex.battle.audio.CryWaveformMatcher
import com.example.overdex.battle.audio.CryWaveformReference
import com.example.overdex.battle.audio.Pcm16Audio
import com.example.overdex.battle.audio.StatChangeSoundMatcher
import kotlin.math.abs

/** Exploratory acoustic evidence only. It does not establish a move use, side, hit or faint. */
@Serializable
data class ReplayMixedAudioCandidate(
    val moveName: String,
    val estimatedOnsetNanos: Long,
    val fragments: List<ReplayAudioFragment>,
)

/** Finds coherent nonoverlapping parts of each reference independently, allowing mixed sounds. */
internal object ReplayMixedAudioRecovery {
    private const val RATE = 8_000
    private const val FRAGMENT = 3_200
    private const val MIN_SCORE = .20f
    private const val STRONG_SCORE = .30f
    private const val ONSET_TOLERANCE = 5_000_000L

    fun measure(audio: Pcm16Audio, articleId: String, capturedAtNanos: Long, audioSha256: String,
                references: List<ReplayAudioReference>, shortFragments: Boolean = false): List<ReplayMixedAudioCandidate> {
        val length = if (shortFragments) RATE / 5 else FRAGMENT
        val query = StatChangeSoundMatcher.mono8k(audio)
        if (query.size !in length..(RATE * 4) || articleId.isBlank() || audioSha256.isBlank()) return emptyList()
        return references.asSequence().filter { !it.charged && it.speciesId == null }
            .distinctBy { it.name to it.sha256 }.flatMap { reference ->
                val samples = StatChangeSoundMatcher.mono8k(reference.audio)
                if (samples.size !in (length * 2)..(RATE * 8)) return@flatMap emptyList()
                val offsets = (0..samples.size - length step (length / 2)).toList()
                if (offsets.size > CryWaveformMatcher.MAX_REFERENCES) return@flatMap emptyList()
                val matcher = CryWaveformMatcher(offsets.mapIndexed { i, off ->
                    CryWaveformReference(i + 1, reference.sha256,
                        Pcm16Audio(RATE, 1, samples.copyOfRange(off, off + length)))
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
                // One clip can contain the tail of one use and the beginning of the next.
                // Multiple fragment pairs at the same onset still describe one occurrence.
                val matches = mutableListOf<ReplayMixedAudioCandidate>()
                for (pair in pairs.sortedByDescending { it.minOf(ReplayAudioFragment::correlation) }) {
                    val onset = pair[0].estimatedOnsetNanos +
                        (pair[1].estimatedOnsetNanos - pair[0].estimatedOnsetNanos) / 2
                    if (matches.none { abs(it.estimatedOnsetNanos - onset) <= ONSET_TOLERANCE }) {
                        matches += ReplayMixedAudioCandidate(reference.name, onset, pair)
                        if (!shortFragments) break
                    }
                }
                matches
            }.toList()
    }
}
