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
import kotlin.math.abs

/** Shared PCM -> preserved localized cries -> bounded independent ball-count checks. */
class LiveLocalizedCryWitness internal constructor(
    private val store: AudioArtifactStore,
    private val requestDepartureCheck: (ActivePokemonSide, Long) -> Unit,
    private val input: Flow<LivePcmChunk>,
    private val loadReferences: suspend (Set<Int>) -> List<CryWaveformReference>?,
) : Observer {
    constructor(context: Context, store: AudioArtifactStore,
        requestDepartureCheck: (ActivePokemonSide, Long) -> Unit,
        input: Flow<LivePcmChunk> = DroidballService.livePcm,
    ) : this(store, requestDepartureCheck, input, verifiedReferenceLoader(context))
    override val observerId = ObserverId(CryDepartureEvidence.SOURCE, ObservationSource.AUDIO_CAPTURE)
    override val name = "Localized Battle Cry"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val worker = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = worker
        val source = SourceId(observerId.id)
        val gate = CryListeningGate()
        val evidence = CryDepartureEvidence()
        val windows = Channel<CryAudioWindowSnapshot>(Channel.CONFLATED)
        val recent = CryRecentWindows()
        val available = java.util.concurrent.atomic.AtomicBoolean(false)
        fun availability(value: Boolean, at: Long = System.currentTimeMillis()) {
            if (available.getAndSet(value) != value) match.custody.submitInputAvailability(source, value, at)
        }
        match.custody.submitAvailability(source, true, System.currentTimeMillis())
        match.custody.submitInputAvailability(source, false, System.currentTimeMillis())
        worker.launch(start = CoroutineStart.UNDISPATCHED) {
            match.articles.collect { article ->
                gate.observe(article)
                if (article.payload is MatchEnded || article.payload == ObservationRestartedByUser ||
                    article.payload == ObservationSessionStoppedByUser || article.payload == ScreenIgnoredByUser)
                    availability(false, article.perceivedAt)
                for (candidate in evidence.observe(article)) {
                    match.custody.submitTestimony(SourceId("REPEAT_CRY_DEPARTURE_WITNESS"),
                        RepeatCryDepartureCandidateObserved(candidate.speciesId, candidate.side,
                            candidate.possibleAppearanceIds, candidate.cryOccurrenceIds),
                        article.perceivedAt - ((article.monotonicTimeNanos ?: candidate.atNanos) - candidate.atNanos) / 1_000_000,
                        null, candidate.evidenceReferences, candidate.atNanos)
                    // An unresolved mirror wakes both independent counters, never an invented side.
                    (candidate.side?.let(::listOf) ?: ActivePokemonSide.entries).forEach {
                        requestDepartureCheck(it, candidate.atNanos)
                    }
                }
            }
        }
        worker.launch {
            val buffer = CryAudioWindow()
            try {
                input.collect { chunk ->
                    val snapshot = buffer.ingest(chunk, gate.generation)
                    if (!buffer.ready || !gate.allows(chunk.endMonotonicNanos)) availability(false, chunk.endWallTimeMillis)
                    if (snapshot != null && gate.accepts(snapshot.startNanos, snapshot.endNanos, snapshot.generation)) {
                        recent.remember(snapshot)
                        windows.trySend(snapshot)
                    }
                }
            } finally { windows.close(); availability(false) }
        }
        worker.launch {
            try {
                var searched = emptySet<Int>()
                var matcher: CryWaveformMatcher? = null
                val retained = ArrayDeque<Pair<Int, Long>>()
                var generation = gate.generation
                for (window in windows) {
                    if (generation != gate.generation) { retained.clear(); generation = gate.generation }
                    if (!gate.accepts(window.startNanos, window.endNanos, window.generation) ||
                        System.nanoTime() - window.endNanos > 2_000_000_000L) {
                        availability(false, window.endWallMillis); continue
                    }
                    val ids = evidence.speciesIds()
                    val searchChanged = ids != searched
                    if (searchChanged) {
                        searched = ids
                        val verified = withContext(Dispatchers.IO) { loadReferences(ids) }
                        matcher = verified?.takeIf { it.isNotEmpty() && it.size <= CryWaveformMatcher.MAX_REFERENCES }
                            ?.let(::CryWaveformMatcher)
                    }
                    val recognizer = matcher
                    availability(recognizer != null && !evidence.unavailable && gate.allows(window.endNanos), window.endWallMillis)
                    if (recognizer == null || evidence.unavailable) continue
                    // Current audio first. A newly known reference gets one bounded catch-up
                    // through recent original PCM, because delayed OCR can miss its entrance.
                    val work = listOf(window) + if (searchChanged) recent.preceding(window) else emptyList()
                    for (analysisWindow in work) {
                        val window = analysisWindow
                        if (!gate.accepts(window.startNanos, window.endNanos, window.generation)) continue
                        val began = System.nanoTime()
                        val fits = CryWaveformAcceptance.select(recognizer.measure(window.audio)).filter { fit ->
                            retained.none { it.first == fit.speciesId && abs(it.second - (window.startNanos + fit.offsetNanos)) < fit.durationNanos }
                        }
                        if (fits.isNotEmpty() && gate.accepts(window.startNanos, window.endNanos, window.generation)) {
                            val pcm = ByteArray(window.audio.samples.size * 2)
                            window.audio.samples.forEachIndexed { i, sample ->
                                pcm[i * 2] = sample.toByte(); pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte()
                            }
                            val artifact = withContext(Dispatchers.IO) {
                                store.preserveWav(Pcm16WavEncoder.encode(pcm, window.audio.sampleRateHz, window.audio.channelCount))
                            }
                            ensureActive()
                            if (artifact == null) availability(false, window.endWallMillis)
                            else if (gate.accepts(window.startNanos, window.endNanos, window.generation)) {
                                val record = match.custody.submitTestimony(SourceId("LOCALIZED_CRY_AUDIO_CAPTURE"),
                                    AudioCaptured(artifact, window.audio.sampleRateHz, window.audio.channelCount,
                                        window.durationNanos, "LOCALIZED_CRY", window.captureSource, PcmSignalLevel.peak(pcm)),
                                    window.endWallMillis - window.durationNanos / 1_000_000, null,
                                    gate.triggerIds(), window.startNanos)
                                val captured = match.awaitRawArticlePublished(record.sequenceNumber)
                                if (gate.accepts(window.startNanos, window.endNanos, window.generation)) for (fit in fits) {
                                    val onset = window.startNanos + fit.offsetNanos
                                    match.custody.submitTestimony(source, BattleCryLocalized(fit.speciesId,
                                        fit.referenceSha256, fit.correlation, fit.durationNanos),
                                        window.endWallMillis - (window.endNanos - onset) / 1_000_000, null,
                                        listOf(captured.id.value), onset)
                                    retained.addLast(fit.speciesId to onset)
                                }
                                while (retained.size > CryDepartureEvidence.MAX_CRIES) retained.removeFirst()
                            }
                        }
                        // Bound work to roughly one third of this worker's time. Acquisition keeps
                        // original PCM; obsolete overlapping analysis windows may be superseded.
                        delay(((System.nanoTime() - began) * 2 / 1_000_000).coerceAtLeast(1))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                Log.e("LiveLocalizedCry", "Cry analysis unavailable", error)
            } finally { availability(false) }
        }
    }

    override fun stop() {
        scope?.cancel(); scope = null
        activeMatch?.custody?.submitInputAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
    }
    companion object {
        private fun verifiedReferenceLoader(context: Context): suspend (Set<Int>) -> List<CryWaveformReference>? {
            var catalog: CryReferenceCatalog? = null
            return { ids ->
                val corpus = catalog ?: CryReferenceCatalog.load(context).also { catalog = it }
                val refs = corpus.references.filter { it.speciesId in ids }
                if (refs.isEmpty() || refs.size > CryWaveformMatcher.MAX_REFERENCES) null else {
                    val verified = refs.mapNotNull { ref ->
                        corpus.openVerified(context, ref)?.let(WavPcm16::decodeBundledReference)?.let {
                            CryWaveformReference(ref.speciesId, ref.sha256, it)
                        }
                    }
                    // Missing/corrupt references cannot silently disappear from the comparison.
                    verified.takeIf { it.size == refs.size }
                }
            }
        }
    }
}
