package com.example.overdex.battle.replay

import kotlinx.serialization.Serializable
import com.example.overdex.battle.audio.CryWaveformMatcher
import com.example.overdex.battle.audio.CryWaveformReference
import com.example.overdex.battle.audio.Pcm16Audio
import com.example.overdex.battle.audio.StatChangeSoundMatcher
import kotlin.math.abs

/** A partial sound match, not evidence of the cry's side or reason. */
@Serializable
data class ReplayPartialCryCandidate(
    override val speciesId: Int,
    override val estimatedOnsetNanos: Long,
    override val fragments: List<ReplayAudioFragment>,
    val competingScore: Float,
) : ReplayCryMatch

/** Compare two nonoverlapping reference fragments at a shared source-time onset. */
internal object ReplayPartialCryRecovery {
    private const val RATE = 8_000
    private const val SEGMENT = 1_600
    private const val STRIDE = 1_600
    private const val MIN_SCORE = .85f
    private const val MIN_MARGIN = .15f
    private const val TOLERANCE = 5_000_000L

    fun measure(audio: Pcm16Audio, articleId: String, capturedAtNanos: Long, audioSha256: String,
                references: List<ReplayAudioReference>): List<ReplayPartialCryCandidate> {
        val query = StatChangeSoundMatcher.mono8k(audio)
        if (query.size !in SEGMENT..(RATE * 4) || references.size > 128 || articleId.isBlank() || audioSha256.isBlank()) return emptyList()
        data class Fit(val reference: ReplayAudioReference, val fragments: List<ReplayAudioFragment>) {
            val score get() = fragments.minOf { it.correlation }
            val onset get() = fragments.minOf { it.estimatedOnsetNanos } +
                abs(fragments[0].estimatedOnsetNanos - fragments[1].estimatedOnsetNanos) / 2
        }
        fun fit(reference: ReplayAudioReference): Fit? {
            val pcm = StatChangeSoundMatcher.mono8k(reference.audio)
            if (pcm.size !in SEGMENT * 2..RATE * 8) return null
            val offsets = (0..pcm.size - SEGMENT step STRIDE).toList()
            val fragments = offsets.chunked(CryWaveformMatcher.MAX_REFERENCES).flatMap { batch ->
                val matcher = CryWaveformMatcher(batch.mapIndexed { i, off ->
                    CryWaveformReference(i + 1, reference.sha256,
                        Pcm16Audio(RATE, 1, pcm.copyOfRange(off, off + SEGMENT)))
                })
                matcher.measureMono8k(query).map { match ->
                    ReplayAudioFragment(articleId, capturedAtNanos, audioSha256, reference.sha256,
                        match.offsetNanos, batch[match.speciesId - 1].toLong() * 1_000_000_000L / RATE,
                        match.durationNanos, match.correlation)
                }
            }
            val pairs = fragments.flatMapIndexed { i, first -> fragments.drop(i + 1).mapNotNull { second ->
                if (abs(first.referenceOffsetNanos - second.referenceOffsetNanos) < first.durationNanos ||
                    abs(first.estimatedOnsetNanos - second.estimatedOnsetNanos) > TOLERANCE) null else listOf(first, second)
            } }
            return pairs.maxByOrNull { minOf(it[0].correlation, it[1].correlation) }?.let { Fit(reference, it) }
        }
        val unique = references.distinctBy { Triple(it.name, it.speciesId, it.sha256) }
        // Only run the more expensive cross-reference comparison when a cry already fits well.
        val cryFits = unique.filter { it.speciesId != null }.mapNotNull(::fit)
        val cries = cryFits.filter { it.score >= MIN_SCORE }
        if (cries.isEmpty()) return emptyList()
        val competitors = unique.filter { it.speciesId == null }.mapNotNull(::fit) + cryFits
        return cries.mapNotNull { cry ->
            val comparable = unique.filter { (it.speciesId != cry.reference.speciesId || it.name != cry.reference.name) &&
                StatChangeSoundMatcher.mono8k(it.audio).size in SEGMENT * 2..RATE * 8 }
            if (comparable.isEmpty()) return@mapNotNull null
            val others = competitors.filter { it !== cry && (it.reference.speciesId != cry.reference.speciesId || it.reference.name != cry.reference.name) }
            // A searched competitor with no coherent pair is not an unavailable reference.
            val rival = others.maxOfOrNull { it.score } ?: 0f
            if (cry.score - rival < MIN_MARGIN) return@mapNotNull null
            ReplayPartialCryCandidate(cry.reference.speciesId!!, cry.onset, cry.fragments, rival)
        }
    }
}
