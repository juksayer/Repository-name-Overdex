package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.audio.*
import java.security.MessageDigest
import kotlin.math.abs

/** A reference is a possibility, never an observed use. Fast effects remain competing references. */
data class ReplayAudioReference(val name: String, val charged: Boolean, val speciesId: Int?, val sha256: String, val audio: Pcm16Audio)
data class ReplayAudioFragment(val articleId: String, val captureAtNanos: Long, val audioSha256: String,
    val referenceSha256: String, val queryOffsetNanos: Long, val referenceOffsetNanos: Long,
    val durationNanos: Long, val correlation: Float,
    val runnerUpName: String? = null, val runnerUpCorrelation: Float? = null) {
    val estimatedOnsetNanos get() = captureAtNanos + queryOffsetNanos - referenceOffsetNanos
}
data class ReplayRecoveredCharge(val moveName: String, val estimatedOnsetNanos: Long, val referenceDurationNanos: Long,
    val fragments: List<ReplayAudioFragment>)
/** Acoustic Fast effect candidate only: ownership, use count and cadence remain unresolved. */
data class ReplayRecoveredFastEffect(val moveName: String, val estimatedOnsetNanos: Long, val fragments: List<ReplayAudioFragment>)
data class ReplayRecoveredCry(val speciesId: Int, val estimatedOnsetNanos: Long, val referenceDurationNanos: Long,
    val fragments: List<ReplayAudioFragment>)
data class ReplayAudioRecovery(val charges: List<ReplayRecoveredCharge> = emptyList(),
    val cries: List<ReplayRecoveredCry> = emptyList(), val unavailableReferences: List<String> = emptyList(),
    val unavailableArtifacts: List<String> = emptyList(),
    val referenceCandidateNames: List<String> = emptyList(),
    val fastEffects: List<ReplayRecoveredFastEffect> = emptyList(),
    /** Unconfirmed multi-sound candidates; never substitute for an impact or faint. */
    val mixedCandidates: List<ReplayMixedAudioCandidate> = emptyList(),
    /** Partial cry fits retain their limits; they do not identify side, departure or a faint. */
    val partialCryCandidates: List<ReplayPartialCryCandidate> = emptyList())

/** Bounded completed-archive study path. No writes, original Article mutations or live submissions. */
object ReplayRetainedAudioRecovery {
    private const val SECOND = 1_000_000_000L
    private const val FRAGMENT_SAMPLES = 4800
    fun selectedArticles(archive: MatchArchive): List<ArchivedRealityArticle> {
        val end = archive.articles.filter { it.matchId == archive.matchId && it.payload is ArchivedMatchEnded }
            .mapNotNull { it.monotonicTimeNanos }.minOrNull() ?: return emptyList()
        val start = archive.articles.filter { it.matchId == archive.matchId && it.payload is ArchivedMatchStarted }.mapNotNull { it.monotonicTimeNanos }.minOrNull()
            ?: return emptyList()
        val unique = archive.articles.groupBy { it.articleId }.values.mapNotNull { it.distinct().singleOrNull() }
        val previous = mutableMapOf<String, String>()
        val entries = unique.sortedBy { it.monotonicTimeNanos ?: Long.MAX_VALUE }.mapNotNull { a ->
            if (a.matchId != archive.matchId) return@mapNotNull null
            val p = a.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return@mapNotNull null
            val t = a.monotonicTimeNanos ?: return@mapNotNull null
            if (t !in start..end) return@mapNotNull null
            val name = p.speciesName.trim().lowercase()
            if (previous.put(p.side, name) == name) null else t
        }
        val clips = unique
            .filter { a -> val p = a.payload as? ArchivedAudioCaptured
                a.matchId == archive.matchId && a.monotonicTimeNanos?.let { it in start..end } == true &&
                    p != null && p.captureSource == "PLAYBACK_POKEMON_GO" && p.durationNanos in 600_000_000L..(16 * SECOND) &&
                    p.byteCount in 44..1_600_044 }
            .sortedBy { it.monotonicTimeNanos }
        val ending = clips.filter { it.monotonicTimeNanos!! >= end - 40 * SECOND }.takeLast(64)
        val departureCandidates = unique.mapNotNull { a ->
            a.monotonicTimeNanos.takeIf { a.payload is ArchivedRepeatCryDepartureCandidate }
        }
        val earlierEntries = clips.filter { a -> a.monotonicTimeNanos!! < end - 40 * SECOND &&
            (entries.any { kotlin.math.abs(a.monotonicTimeNanos!! - it) <= 2 * SECOND } ||
                departureCandidates.any { a.monotonicTimeNanos!! in (it - 3 * SECOND)..(it + SECOND) }) }.takeLast(64)
        return (earlierEntries + ending).sortedBy { it.monotonicTimeNanos }
    }

    fun recover(archive: MatchArchive, references: List<ReplayAudioReference>,
                unavailableReferences: List<String> = emptyList(), checkCancelled: () -> Unit = {}, captureWindow: LongRange? = null, read: (ArchivedAudioCaptured) -> ByteArray?): ReplayAudioRecovery {
        if (references.size > 128) return ReplayAudioRecovery(unavailableReferences = listOf("Reference pool exceeds recovery bound"))
        data class Prepared(val ref: ReplayAudioReference, val offsets: List<Int>, val duration: Long, val matcher: CryWaveformMatcher)
        data class Hit(val ref: ReplayAudioReference, val duration: Long, val fragment: ReplayAudioFragment)
        val prepared = references.distinctBy { Triple(it.name, it.speciesId, it.sha256) }.mapNotNull { ref ->
            val mono = StatChangeSoundMatcher.mono8k(ref.audio)
            if (mono.size !in 800..(8 * 8000)) return@mapNotNull null
            // Keep the actual tail, even when duration is not a multiple of the stride.
            val length = minOf(FRAGMENT_SAMPLES, mono.size)
            val offsets = ((0..mono.size - length step 2400).toList() + (mono.size - length)).distinct()
            Prepared(ref, offsets, mono.size * SECOND / 8000, CryWaveformMatcher(offsets.mapIndexed { i, offset ->
                CryWaveformReference(i + 1, ref.sha256, Pcm16Audio(8000, 1, mono.copyOfRange(offset, offset + length)))
            }))
        }
        if (prepared.sumOf { it.offsets.size } > 384) return ReplayAudioRecovery(
            unavailableReferences = listOf("Reference fragments exceed recovery memory bound"))
        val missing = unavailableReferences + references.filter { r -> prepared.none { it.ref.name == r.name && it.ref.speciesId == r.speciesId && it.ref.sha256 == r.sha256 } }.map { it.name }
        val hits = mutableListOf<Hit>(); val unavailable = mutableListOf<String>()
        val mixed = mutableListOf<ReplayMixedAudioCandidate>()
        val partialCries = mutableListOf<ReplayPartialCryCandidate>()
        val soundsByCaptureTime = archive.articles.mapNotNull { a ->
            val sound = a.payload as? ArchivedFastMoveSoundMeasured ?: return@mapNotNull null
            a.monotonicTimeNanos?.let { it to sound }
        }.groupBy({ it.first }, { it.second })
        val selected = selectedArticles(archive).filter { captureWindow == null || it.monotonicTimeNanos in captureWindow }
        val end = archive.articles.filter { it.matchId == archive.matchId && it.payload is ArchivedMatchEnded }
            .mapNotNull { it.monotonicTimeNanos }.minOrNull()
        if (end != null && (selected.count { it.monotonicTimeNanos!! < end - 40 * SECOND } >= 64 ||
                    selected.count { it.monotonicTimeNanos!! >= end - 40 * SECOND } >= 64))
            unavailable += "Audio selection may have reached its bound"
        for (article in selected) {
            checkCancelled()
            val p = article.payload as ArchivedAudioCaptured
            val bytes = read(p)
            if (bytes == null || bytes.size.toLong() != p.byteCount || sha(bytes) != p.sha256) { unavailable += article.articleId; continue }
            val audio = WavPcm16.decode(bytes)
            if (audio == null || audio.sampleRateHz != p.sampleRateHz || audio.channelCount != p.channelCount ||
                abs(audio.samples.size.toLong() * SECOND / (audio.sampleRateHz * audio.channelCount) - p.durationNanos) > 1_000_000L) {
                unavailable += article.articleId; continue
            }
            val mono = StatChangeSoundMatcher.mono8k(audio)
            if (article.sourceId == "FAST_MOVE_STREAM_AUDIO_CAPTURE" && mono.size <= 4 * 8000) {
                if (end != null && article.monotonicTimeNanos!! >= end - 20 * SECOND) {
                    partialCries += ReplayPartialCryRecovery.measure(audio, article.articleId,
                        article.monotonicTimeNanos!!, p.sha256, references)
                }
                val names = soundsByCaptureTime[article.monotonicTimeNanos].orEmpty()
                    .flatMap { it.referenceCandidates }.filter { it.similarity >= .85f }
                    .map { it.moveName.uppercase().filter(Char::isLetterOrDigit) }.toSet()
                val eligible = references.filter { !it.charged && it.speciesId == null &&
                    it.name.uppercase().filter(Char::isLetterOrDigit) in names }
                if (captureWindow == null && eligible.size <= 8) mixed += ReplayMixedAudioRecovery.measure(audio,
                    article.articleId, article.monotonicTimeNanos!!, p.sha256, eligible)
            }
            if (captureWindow != null && mono.size <= 4 * 8000) {
                // The caller has already bounded this comparison to the two active species and
                // the terminal exchange. A mixed-in cry can obscure part of a longer effect.
                val pool = references.filter { !it.charged && it.speciesId == null }
                if (pool.size <= 16 && pool.distinctBy { it.name }.size <= 8) {
                    mixed += ReplayMixedAudioRecovery.measure(audio, article.articleId,
                        article.monotonicTimeNanos!!, p.sha256, pool, shortFragments = true)
                }
            }
            // A 1.2 s window keeps cached FFTs bounded for the full reference
            // pool. Half-window overlap covers every 600 ms reference fragment.
            val window = 9600
            val stride = 4800
            val starts = if (mono.size <= window) listOf(0) else
                ((0..mono.size - window step stride).toList() + (mono.size - window)).distinct()
            for (startSample in starts) {
                checkCancelled()
                val slice = mono.copyOfRange(startSample, minOf(startSample + window, mono.size))
                val fits = prepared.mapNotNull { r -> r.matcher.measureMono8k(slice).firstOrNull()?.let { fit ->
                    Hit(r.ref, r.duration, ReplayAudioFragment(article.articleId, article.monotonicTimeNanos!!, p.sha256,
                        r.ref.sha256, startSample * SECOND / 8000 + fit.offsetNanos, r.offsets[fit.speciesId - 1] * SECOND / 8000,
                        fit.durationNanos, fit.correlation))
                } }.sortedByDescending { it.fragment.correlation }
                val best = fits.firstOrNull() ?: continue
                val competitor = fits.firstOrNull { it.ref.name != best.ref.name || it.ref.speciesId != best.ref.speciesId }
                    ?: continue // Never force a single-option match.
                val threshold = if (best.ref.speciesId != null) .95f else .98f
                if (best.fragment.durationNanos >= 600_000_000L && best.fragment.correlation >= threshold && best.fragment.correlation - competitor.fragment.correlation >= .15f) hits += best.copy(fragment = best.fragment.copy(runnerUpName = competitor.ref.name, runnerUpCorrelation = competitor.fragment.correlation))
            }
        }
        val charges = mutableListOf<ReplayRecoveredCharge>()
        val cries = mutableListOf<ReplayRecoveredCry>()
        val fastEffects = mutableListOf<ReplayRecoveredFastEffect>()
        for ((_, group) in hits.groupBy { Triple(it.ref.name, it.ref.speciesId, it.ref.sha256) }) {
            val remaining = group.sortedBy { it.fragment.estimatedOnsetNanos }.toMutableList()
            while (remaining.isNotEmpty()) {
                val first = remaining.first()
                val cluster = remaining.takeWhile { it.fragment.estimatedOnsetNanos - first.fragment.estimatedOnsetNanos <= 80_000_000L }
                remaining.removeAll(cluster.toSet())
                val fragments = cluster.map { it.fragment }.distinctBy { it.articleId }
                val onset = fragments.map { it.estimatedOnsetNanos }.sorted().let { it[it.size / 2] }
                if (first.ref.speciesId != null) cries += ReplayRecoveredCry(first.ref.speciesId, onset, first.duration, fragments)
                else if (!first.ref.charged) fastEffects += ReplayRecoveredFastEffect(first.ref.name, onset, fragments)
                else if (fragments.any { a -> fragments.any { b ->
                        a.articleId != b.articleId && a.audioSha256 != b.audioSha256 &&
                            abs(a.referenceOffsetNanos - b.referenceOffsetNanos) >= a.durationNanos &&
                            abs((a.captureAtNanos + a.queryOffsetNanos) - (b.captureAtNanos + b.queryOffsetNanos)) >= a.durationNanos
                    } }) charges += ReplayRecoveredCharge(first.ref.name, onset, first.duration, fragments)
            }
        }
        val groupedMixed = mutableListOf<ReplayMixedAudioCandidate>()
        mixed.groupBy { it.moveName }.values.forEach { group ->
            val pending = group.sortedBy { it.estimatedOnsetNanos }.toMutableList()
            while (pending.isNotEmpty()) {
                val cluster = pending.takeWhile { it.estimatedOnsetNanos - pending.first().estimatedOnsetNanos <= 80_000_000L }
                pending.removeAll(cluster.toSet())
                val onset = cluster.map { it.estimatedOnsetNanos }.sorted()[cluster.size / 2]
                groupedMixed += ReplayMixedAudioCandidate(cluster.first().moveName, onset,
                    cluster.flatMap { it.fragments }.distinctBy { Triple(it.articleId, it.referenceOffsetNanos, it.queryOffsetNanos) })
            }
        }
        val groupedPartial = mutableListOf<ReplayPartialCryCandidate>()
        partialCries.groupBy { it.speciesId }.values.forEach { group ->
            val pending = group.sortedBy { it.estimatedOnsetNanos }.toMutableList()
            while (pending.isNotEmpty()) {
                val cluster = pending.takeWhile { it.estimatedOnsetNanos - pending.first().estimatedOnsetNanos <= 80_000_000L }
                pending.removeAll(cluster.toSet())
                groupedPartial += ReplayPartialCryCandidate(cluster.first().speciesId,
                    cluster.map { it.estimatedOnsetNanos }.sorted()[cluster.size / 2],
                    cluster.flatMap { it.fragments }.distinctBy { Triple(it.articleId, it.referenceOffsetNanos, it.queryOffsetNanos) },
                    cluster.maxOf { it.competingScore })
            }
        }
        return ReplayAudioRecovery(charges, cries, missing.distinct(), unavailable,
            prepared.map { it.ref.name }.distinct().sorted(), fastEffects, groupedMixed, groupedPartial)
    }
    fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
