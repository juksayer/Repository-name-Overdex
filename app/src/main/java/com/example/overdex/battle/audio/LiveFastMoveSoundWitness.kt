package com.example.overdex.battle.audio

import android.content.Context
import android.util.Log
import com.example.overdex.battle.artifact.AudioArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.Observer
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Recognizes independent audio windows from VS through the final faint/result.
 * Strong matches and rate-limited plausible comparisons retain their analyzed PCM. One background worker
 * and a conflated queue keep active-combatant reference searches off the capture path.
 */
class LiveFastMoveSoundWitness(
    private val context: Context,
    private val store: AudioArtifactStore,
    private val input: Flow<LivePcmChunk> = DroidballService.livePcm,
    private val loadMatcher: suspend () -> FastMoveReferenceMatcher = { FastMoveSoundReferences.load(context) },
) : Observer {
    override val observerId = ObserverId("FAST_MOVE_SOUND_WITNESS", ObservationSource.AUDIO_CAPTURE)
    override val name = "Live Fast Move Sound"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var gate = FastMoveListeningGate()
    @Volatile private var failed = false
    @Volatile private var prepared = false
    private data class PendingSound(
        val window: FastMoveAudioWindowSnapshot,
        val candidates: FastMoveAudioCandidateSnapshot,
        val measurement: FastMoveSoundMeasured,
    )

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val worker = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = worker
        val source = SourceId(observerId.id)
        val windows = Channel<FastMoveAudioWindowSnapshot>(Channel.CONFLATED)
        val pending = ConcurrentHashMap<Pair<String, Long>, PendingSound>()
        val candidateContext = FastMoveAudioCandidateContext(match.pokemonKnowledge)
        val matcher = worker.async { loadMatcher() }
        match.custody.submitAvailability(source, true, System.currentTimeMillis())
        match.custody.submitInputAvailability(source, false, System.currentTimeMillis())

        worker.launch(start = CoroutineStart.UNDISPATCHED) {
            match.articles.collect { article ->
                val previousGeneration = gate.generation
                val previousCandidates = candidateContext.generation
                gate.observe(article.payload, article.monotonicTimeNanos, article.id.value)
                candidateContext.observe(article)
                if (gate.generation != previousGeneration || candidateContext.generation != previousCandidates) pending.clear()
                val audio = article.payload as? AudioCaptured ?: return@collect
                if (audio.cueKind != CUE_KIND) return@collect
                val at = article.monotonicTimeNanos ?: return@collect
                pending.remove(audio.artifact.sha256 to at)?.let { saved ->
                    if (!gate.accepts(saved.window.startNanos, saved.window.endNanos, saved.window.generation) ||
                        !candidateContext.isCurrent(saved.candidates)) return@collect
                    match.custody.submitTestimony(source, saved.measurement, article.perceivedAt, null,
                        listOf(article.id.value), at)
                }
            }
        }
        worker.launch {
            val window = FastMoveAudioWindow()
            var available: Boolean? = null
            var generation = gate.generation
            try {
                input.collect { chunk ->
                    if (generation != gate.generation) { window.reset(); generation = gate.generation }
                    // Keep a small volatile pre-roll while waiting for delayed VS/countdown recognition.
                    val snapshot = window.ingest(chunk, generation)
                    val operating = prepared && !failed && gate.allows(chunk.endMonotonicNanos) && window.ready
                    if (available != operating) {
                        available = operating
                        match.custody.submitInputAvailability(source, operating, chunk.endWallTimeMillis)
                    }
                    if (!failed && snapshot != null && gate.accepts(snapshot.startNanos, snapshot.endNanos, generation)) {
                        windows.trySend(snapshot)
                    }
                }
            } finally {
                windows.close()
                match.custody.submitInputAvailability(source, false, System.currentTimeMillis())
            }
        }
        worker.launch {
            try {
                val recognizer = matcher.await()
                prepared = true
                val retained = ArrayDeque<Pair<String, Long>>()
                var retainedGeneration: Pair<Long, Long>? = null
                var lastComparisonAt: Long? = null
                var previousSequence: Long? = null
                var scans = 0
                var skipped = 0L
                var totalNanos = 0L
                var maximumNanos = 0L
                var reportAt = Long.MIN_VALUE
                for (window in windows) {
                    if (!gate.accepts(window.startNanos, window.endNanos, window.generation)) continue
                    // PCM acquisition starts at VS, but unknown combatants never trigger a
                    // full-library search. A window crossing a switch/configuration is discarded.
                    val search = candidateContext.snapshot(window.startNanos, window.endNanos) ?: continue
                    val generation = window.generation to search.generation
                    if (retainedGeneration != generation) {
                        retained.clear(); lastComparisonAt = null; retainedGeneration = generation
                    }
                    previousSequence?.let { skipped += (window.sequence - it - 1).coerceAtLeast(0) }
                    previousSequence = window.sequence
                    if (System.nanoTime() - window.endNanos > 2_000_000_000L) continue
                    val started = System.nanoTime()
                    val candidates = recognizer.measureMono8k(window.samples, search.allowedMoveNames)
                    if (!candidateContext.isCurrent(search)) continue
                    val accepted = search.allActiveSidesKnown && FastMoveReferenceMatcher.accepts(candidates)
                    // Preserve each side's legal comparison even when the other side is louder.
                    // Shared names remain acoustically unassigned; downstream evidence owns that decision.
                    val comparisonPeaks = search.sideMoves.values.mapNotNull { names ->
                        FastMoveReferenceMatcher.plausibleCandidate(candidates.filter { candidate ->
                            names.any { moveKey(it) == moveKey(candidate.moveName) }
                        })
                    }.distinctBy { moveKey(it.moveName) }
                    val keepComparison = !accepted && comparisonPeaks.isNotEmpty() &&
                        (lastComparisonAt == null || window.startNanos - lastComparisonAt!! >= 1_500_000_000L)
                    val elapsed = System.nanoTime() - started
                    scans++; totalNanos += elapsed; maximumNanos = maxOf(maximumNanos, elapsed)
                    if (reportAt == Long.MIN_VALUE || window.endNanos - reportAt >= 5_000_000_000L) {
                        val best = candidates.firstOrNull()
                        match.custody.submitTestimony(SourceId("FAST_MOVE_AUDIO_ANALYSIS_STATUS"), RawTestimony(
                            "STREAM; scans=$scans; skippedWindows=$skipped; " +
                                "meanMatchMs=${totalNanos / scans / 1_000_000}; maxMatchMs=${maximumNanos / 1_000_000}; " +
                                "legalReferences=${search.allowedMoveNames.size}; activeSidesKnown=${search.allActiveSidesKnown}; " +
                                "accepted=$accepted; best=${best?.moveName}; similarity=${best?.similarity}; " +
                                "changingSimilarity=${best?.changingSimilarity}"
                        ), window.endWallMillis, null, gate.triggerIds(), window.endNanos)
                        reportAt = window.endNanos
                    }
                    if ((accepted || keepComparison) && pending.size < 16 &&
                        gate.accepts(window.startNanos, window.endNanos, window.generation) && candidateContext.isCurrent(search)) {
                        val peaks = (comparisonPeaks + candidates.take(1)).distinctBy { moveKey(it.moveName) }
                            .map { moveKey(it.moveName) to window.startNanos + it.matchingOffsetNanos - it.referenceOffsetNanos }
                        if (peaks.any { peak -> retained.none { it.first == peak.first && abs(it.second - peak.second) < 250_000_000L } }) {
                            val pcm = ByteArray(window.samples.size * 2)
                            window.samples.forEachIndexed { index, sample ->
                                pcm[index * 2] = sample.toByte(); pcm[index * 2 + 1] = (sample.toInt() shr 8).toByte()
                            }
                            val artifact = withContext(Dispatchers.IO) {
                                store.preserveWav(Pcm16WavEncoder.encode(pcm, FastMoveAudioWindow.RATE, 1))
                            }
                            ensureActive()
                            if (artifact != null && gate.accepts(window.startNanos, window.endNanos, window.generation) &&
                                candidateContext.isCurrent(search)) {
                                val measured = FastMoveSoundAnalyzer.measureMono8k(window.samples)
                                pending[artifact.sha256 to window.startNanos] = PendingSound(window, search, measured.copy(
                                    analysisVersion = FastMoveReferenceMatcher.VERSION,
                                    // Keep all scored legal alternatives and the requested lists, so
                                    // missing sound assets remain visible rather than becoming exclusions.
                                    referenceCandidates = candidates,
                                    referenceSearchScopes = search.sideMoves.map { (side, names) ->
                                        FastMoveSoundSearchScope(side, search.sideSpeciesNames.getValue(side),
                                            names.sorted(), search.sideEvidenceIds.getValue(side))
                                    },
                                    referenceMatchAccepted = accepted,
                                    soundOnsetMonotonicNanos = measured.onsetOffsetNanos?.let { window.startNanos + it },
                                ))
                                match.custody.submitTestimony(SourceId("FAST_MOVE_STREAM_AUDIO_CAPTURE"),
                                    AudioCaptured(artifact, FastMoveAudioWindow.RATE, 1, window.durationNanos, CUE_KIND,
                                        window.captureSource, PcmSignalLevel.peak(pcm)),
                                    window.endWallMillis - window.durationNanos / 1_000_000, null,
                                    (gate.triggerIds() + search.evidenceIds).distinct(), window.startNanos)
                                retained.addAll(peaks)
                                if (keepComparison) lastComparisonAt = window.startNanos
                                while (retained.size > 64) retained.removeFirst()
                            } else if (artifact == null) {
                                match.custody.submitInputAvailability(source, false, window.endWallMillis)
                            }
                        }
                    }
                    // At most a third of one worker's wall time is spent searching. Slow phones
                    // skip obsolete overlapping windows, not PCM acquisition; diagnostics expose it.
                    delay((elapsed * 2 / 1_000_000).coerceAtLeast(1))
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                failed = true
                Log.e("FastMoveSound", "Streaming recognition unavailable", error)
                match.custody.submitAvailability(source, false, System.currentTimeMillis())
            }
        }
    }

    override fun stop() {
        scope?.cancel(); scope = null
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        gate = FastMoveListeningGate()
        failed = false
        prepared = false
    }

    companion object {
        const val CUE_KIND = "FAST_MOVE_AUDIO_STREAM"
        private fun moveKey(name: String) = name.uppercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit)
    }
}
