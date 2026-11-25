package com.example.overdex.battle.audio

import android.content.Context
import android.util.Log
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.artifact.AudioArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.DroidballService
import com.example.overdex.battle.observation.Match
import com.example.overdex.battle.observation.MatchProcessingDiagnostics.CryPositiveTiming
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
        val participants = BattleCryParticipants()
        val windows = Channel<CryAudioWindowSnapshot>(Channel.CONFLATED)
        val recent = CryRecentWindows()
        data class ChargeJob(val row: RealityArticle, val ids: Set<Int>, val generation: Long)
        val probes = Channel<ChargeJob>(2)
        val retained = ArrayDeque<Triple<Long, Int, Long>>()
        fun seen(epoch: Long, species: Int, onset: Long, duration: Long) = synchronized(retained) {
            retained.any { it.first == epoch && it.second == species && abs(it.third - onset) < duration }
        }
        fun claim(epoch: Long, species: Int, onset: Long, duration: Long) = synchronized(retained) {
            if (seen(epoch, species, onset, duration)) false else {
                retained.addLast(Triple(epoch, species, onset))
                if (retained.size > CryDepartureEvidence.MAX_CRIES) retained.removeFirst()
                true
            }
        }
        val available = java.util.concurrent.atomic.AtomicBoolean(false)
        fun availability(value: Boolean, at: Long = System.currentTimeMillis()) {
            if (available.getAndSet(value) != value) match.custody.submitInputAvailability(source, value, at)
        }
        match.custody.submitAvailability(source, true, System.currentTimeMillis())
        match.custody.submitInputAvailability(source, false, System.currentTimeMillis())
        worker.launch(start = CoroutineStart.UNDISPATCHED) {
            val chargeContext = ChargeClipContext()
            match.articles.collect { article ->
                participants.observe(article)
                chargeContext.observe(article)
                gate.observe(article)
                if (article.payload is MatchEnded || article.payload == ObservationRestartedByUser ||
                    article.payload == ObservationSessionStoppedByUser || article.payload == ScreenIgnoredByUser)
                    availability(false, article.perceivedAt)
                if (!evidence.unavailable && chargeContext.accepts(article))
                    probes.send(ChargeJob(article, participants.species, gate.generation))
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
            for ((row, ids, epoch) in probes) {
                try {
                    val start = row.monotonicTimeNanos ?: continue
                    if (epoch != gate.generation || ids.isEmpty()) continue
                    val clip = row.payload as AudioCaptured
                    val bytes = withContext(Dispatchers.IO) { store.readWav(clip.artifact, ChargeClipCries.MAX_BYTES) } ?: continue
                    val refs = withContext(Dispatchers.IO) { loadReferences(ids) } ?: continue
                    if (refs.size !in 1..CryWaveformMatcher.MAX_REFERENCES || !refs.map { it.speciesId }.containsAll(ids)) continue
                    for (fit in ChargeClipCries.measure(clip, bytes, CryWaveformMatcher(refs))) {
                        ensureActive()
                        val onset = start + fit.offsetNanos
                        if (gate.accepts(onset, onset + fit.durationNanos, epoch) &&
                            claim(epoch, fit.speciesId, onset, fit.durationNanos)) {
                            match.custody.submitTestimony(source, BattleCryLocalized(fit.speciesId, fit.referenceSha256,
                                fit.correlation, fit.durationNanos, "charge-clip-full-waveform-v1"),
                                row.perceivedAt + fit.offsetNanos / 1_000_000, null, listOf(row.id.value), onset)
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { Log.e("LiveLocalizedCry", "Could not inspect retained charge sound", error) }
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
                var referencesStartedNanos = 0L
                var referencesReadyNanos = 0L
                var generation = gate.generation
                suspend fun analyze(window: CryAudioWindowSnapshot, recognizer: CryWaveformMatcher, catchUp: Boolean) {
                    if (!gate.accepts(window.startNanos, window.endNanos, window.generation)) return
                    val began = System.nanoTime()
                    val fits = CryWaveformAcceptance.select(recognizer.measure(window.audio)).filter { fit ->
                        !seen(window.generation, fit.speciesId, window.startNanos + fit.offsetNanos, fit.durationNanos)
                    }
                    val matched = System.nanoTime()
                    if (fits.isNotEmpty() && gate.accepts(window.startNanos, window.endNanos, window.generation)) {
                        val pcm = ByteArray(window.audio.samples.size * 2)
                        window.audio.samples.forEachIndexed { i, sample ->
                            pcm[i * 2] = sample.toByte(); pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte()
                        }
                        val artifact = withContext(Dispatchers.IO) {
                            store.preserveWav(Pcm16WavEncoder.encode(pcm, window.audio.sampleRateHz, window.audio.channelCount))
                        }
                        val preserved = System.nanoTime()
                        ensureActive()
                        if (artifact == null) availability(false, window.endWallMillis)
                        else if (gate.accepts(window.startNanos, window.endNanos, window.generation)) {
                            val record = match.custody.submitTestimony(SourceId("LOCALIZED_CRY_AUDIO_CAPTURE"),
                                AudioCaptured(artifact, window.audio.sampleRateHz, window.audio.channelCount,
                                    window.durationNanos, "LOCALIZED_CRY", window.captureSource, PcmSignalLevel.peak(pcm)),
                                window.endWallMillis - window.durationNanos / 1_000_000, null,
                                gate.triggerIds(), window.startNanos)
                            val submitted = System.nanoTime()
                            val captured = match.awaitRawArticlePublished(record.sequenceNumber)
                            val receipt = System.nanoTime()
                            if (gate.accepts(window.startNanos, window.endNanos, window.generation)) for (fit in fits) {
                                val onset = window.startNanos + fit.offsetNanos
                                if (!claim(window.generation, fit.speciesId, onset, fit.durationNanos)) continue
                                val cry = match.custody.submitTestimony(source, BattleCryLocalized(fit.speciesId,
                                    fit.referenceSha256, fit.correlation, fit.durationNanos),
                                    window.endWallMillis - (window.endNanos - onset) / 1_000_000, null,
                                    listOf(captured.id.value), onset)
                                match.processingDiagnostics?.recordCryPositive(CryPositiveTiming(
                                    speciesId = fit.speciesId, catchUp = catchUp, onsetNanos = onset,
                                    windowEndNanos = window.endNanos, bufferedNanos = window.bufferedAtNanos,
                                    referencesStartedNanos = referencesStartedNanos, referencesReadyNanos = referencesReadyNanos,
                                    analysisStartNanos = began, matchDoneNanos = matched, preservedNanos = preserved,
                                    captureSubmittedNanos = submitted, captureReceiptNanos = receipt,
                                    crySubmittedNanos = System.nanoTime(), captureSequence = record.sequenceNumber,
                                    crySequence = cry.sequenceNumber))
                            }
                        }
                    }
                    // Time spent preserving evidence and waiting for publication already gives the matcher a rest.
                    delay(cryScanRestMillis(matched - began, System.nanoTime() - matched))
                }

                var deferredLive: CryAudioWindowSnapshot? = null
                while (true) {
                    val window = deferredLive ?: windows.receiveCatching().getOrNull() ?: break
                    deferredLive = null
                    if (generation != gate.generation) { generation = gate.generation }
                    if (!gate.accepts(window.startNanos, window.endNanos, window.generation) ||
                        System.nanoTime() - window.endNanos > MAX_LIVE_AGE_NANOS) {
                        availability(false, window.endWallMillis); continue
                    }
                    val ids = participants.species
                    val searchChanged = ids != searched
                    // Freeze this bounded catch-up before loading references can age its PCM out.
                    val history = if (searchChanged) recent.preceding(window) else emptyList()
                    if (searchChanged) {
                        searched = ids
                        referencesStartedNanos = System.nanoTime()
                        val verified = withContext(Dispatchers.IO) { loadReferences(ids) }
                        matcher = verified?.takeIf { it.isNotEmpty() && it.size <= CryWaveformMatcher.MAX_REFERENCES }
                            ?.let(::CryWaveformMatcher)
                        referencesReadyNanos = System.nanoTime()
                    }
                    val recognizer = matcher
                    availability(recognizer != null && !evidence.unavailable && gate.allows(window.endNanos), window.endWallMillis)
                    if (recognizer == null || evidence.unavailable) continue
                    analyze(window, recognizer, catchUp = false)
                    for (past in history) {
                        // Alternate one fresh window with one historical window. Finish every admitted
                        // historical window even if fresh PCM arrives faster than we can match it.
                        if (deferredLive == null) {
                            val fresh = windows.tryReceive().getOrNull()
                            if (fresh != null) {
                                if (fresh.generation != generation || participants.species != ids) deferredLive = fresh
                                else if (!evidence.unavailable && System.nanoTime() - fresh.endNanos <= MAX_LIVE_AGE_NANOS) {
                                    analyze(fresh, recognizer, catchUp = false)
                                }
                            }
                        }
                        analyze(past, recognizer, catchUp = true)
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
        private const val MAX_LIVE_AGE_NANOS = 2_000_000_000L
        private fun verifiedReferenceLoader(context: Context): suspend (Set<Int>) -> List<CryWaveformReference>? {
            val catalog by lazy { CryReferenceCatalog.load(context) }
            return { ids ->
                val refs = ids.sorted().flatMap(catalog::referencesFor)
                if (refs.isEmpty() || refs.size > CryWaveformMatcher.MAX_REFERENCES) null else {
                    val verified = refs.mapNotNull { ref ->
                        catalog.openVerified(context, ref)?.let(WavPcm16::decodeBundledReference)?.let {
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

/** Keep the one-third matcher duty budget without charging storage or custody waits as matching work. */
internal fun cryScanRestMillis(matchingNanos: Long, elapsedSinceMatchNanos: Long): Long =
    (2 * matchingNanos - elapsedSinceMatchNanos).coerceAtLeast(0) / 1_000_000
