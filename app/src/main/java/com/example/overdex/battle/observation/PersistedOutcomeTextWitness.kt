package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.observation.OutcomeTextRecognizer
import com.example.overdex.data.observation.OcrPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.observation.OcrTextEvidence

/** Reads near-KO results urgently, with independent routine checks for early concessions. */
class PersistedOutcomeTextWitness internal constructor(
    private val reader: OutcomeCropReader,
    private val now: () -> Long = System::nanoTime,
    override val observerId: ObserverId = ObserverId("MATCH_OUTCOME_TEXT_WITNESS", ObservationSource.SCREEN_CAPTURE),
    override val name: String = "Match Outcome Text",
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Observer {
    constructor(
        artifactStore: FileCropArtifactStore,
        observerId: ObserverId = ObserverId("MATCH_OUTCOME_TEXT_WITNESS", ObservationSource.SCREEN_CAPTURE),
        name: String = "Match Outcome Text",
    ) : this(ArtifactOutcomeCropReader(artifactStore), System::nanoTime, observerId, name)

    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(dispatcher + SupervisorJob()).also { witnessScope ->
            // Only artifact references are queued. Source crops remain in the
            // Timeline even when their live-reading window has expired.
            val pending = OutcomeEvidenceQueue<RealityArticle>()
            val probePending = PendingTransientTextCrops()
            val probeSource = BattleWitnessContracts.matchOutcomeProbeCapture.witnessId
            val recentWake = Channel<Unit>(Channel.CONFLATED)
            val recoveryWake = Channel<Unit>(Channel.CONFLATED)
            val probeWake = Channel<Unit>(Channel.CONFLATED)
            witnessScope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion {
                pending.close()
                probePending.clear()
                recentWake.close()
                recoveryWake.close()
                probeWake.close()
            }
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.filter { article ->
                    article.sourceId.id != probeSource &&
                        (article.payload as? CropCaptured)?.cropProvenance?.cropName == BattleCropContracts.matchOutcome.cropName
                }.collect { article ->
                    val capturedAt = article.monotonicTimeNanos ?: return@collect
                    if (pending.offer(article, capturedAt, now())) {
                        recentWake.trySend(Unit)
                        recoveryWake.trySend(Unit)
                    }
                }
            }
            // Routine checks never enter the urgent result backlog. Keep the
            // visual inspection separate so its I/O cannot delay an urgent crop.
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.filter { article ->
                    article.sourceId.id == probeSource &&
                        (article.payload as? CropCaptured)?.cropProvenance?.cropName == BattleCropContracts.matchOutcome.cropName
                }.collect { article ->
                    val visual = try { reader.inspect(article) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        Log.w("OutcomeText", "Result probe failed; retaining latest OCR fallback", error)
                        null
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    probePending.offer(article, visual)
                    probeWake.trySend(Unit)
                }
            }
            // Pay model startup while the battle is underway, before the brief result.
            witnessScope.launch { reader.warmUp() }
            fun launchLane(lane: OutcomeReadLane, wake: Channel<Unit>) = witnessScope.launch {
                val priority = if (lane == OutcomeReadLane.RECENT) OcrPriority.URGENT else OcrPriority.NORMAL
                for (signal in wake) {
                    fun hasPending() = when (lane) {
                        OutcomeReadLane.RECENT -> pending.counts(now()).recent > 0
                        OutcomeReadLane.RECOVERY -> pending.counts(now()).recovery > 0
                        OutcomeReadLane.PROBE -> probePending.hasPending(now())
                    }
                    while (hasPending()) {
                        var sample: RealityArticle? = null
                        val queuedAt = now()
                        var admittedAt = queuedAt
                        var nativeReadNanos = 0L
                        var passes = 0
                        var remaining = pending.counts(queuedAt)
                        var probeSelection: PendingTransientTextCrops.Selection? = null
                        val readings: List<OcrTextEvidence> = try {
                            reader.withReadingTurn(priority) { turn ->
                                admittedAt = now()
                                // Selection and expiry happen after admission.
                                // A newer blank cannot leap over a brief recent result.
                                val article = when (lane) {
                                    OutcomeReadLane.RECENT -> pending.pollRecent(admittedAt)
                                    OutcomeReadLane.RECOVERY -> pending.pollRecovery(admittedAt)
                                    OutcomeReadLane.PROBE -> probePending.select(admittedAt)
                                        ?.also { probeSelection = it }?.article
                                }
                                recoveryWake.trySend(Unit) // expiry may have moved work
                                article ?: return@withReadingTurn emptyList()
                                sample = article
                                remaining = pending.counts(admittedAt)
                                try { turn.recognize(article) } finally {
                                    nativeReadNanos = turn.nativeReadNanos
                                    passes = turn.readCount
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            // One bad artifact/read cannot terminate the Match reader.
                            Log.e("OutcomeText", "Result OCR failed for ${sample?.id?.value}", error)
                            emptyList()
                        }
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val article = sample ?: continue
                        val capturedAt = article.monotonicTimeNanos ?: continue
                        val completedAt = now()
                        probeSelection?.let {
                            if (readings.any { reading -> reading.rawText.isNotBlank() }) probePending.succeeded(it)
                            else probePending.failed(it)
                        }
                        val probeDetails = probeSelection?.let {
                            " selection=${it.kind} probePending=${it.pendingCount} " +
                                "coalescedTotal=${it.coalescedTotal} overflowTotal=${it.overflowTotal} expiredTotal=${it.expiredTotal}"
                        }.orEmpty()
                        // queueMs is this backend request's wait. Crop age at
                        // admission also includes time waiting for local selection.
                        match.custody.submitTestimony(
                            SourceId("${observerId.id}_LATENCY"),
                            RawTestimony("cropId=${article.id.value} lane=$lane priority=$priority " +
                                "queueMs=${(admittedAt - queuedAt) / 1_000_000} " +
                                "captureToAdmissionMs=${(admittedAt - capturedAt) / 1_000_000} " +
                                "nativeReadMs=${nativeReadNanos / 1_000_000} " +
                                "processingMs=${(completedAt - admittedAt) / 1_000_000} " +
                                "totalMs=${(completedAt - capturedAt) / 1_000_000} " +
                                "passes=$passes pending=${if (lane == OutcomeReadLane.PROBE) probeSelection?.pendingCount ?: 0 else remaining.total} " +
                                "recentPending=${remaining.recent} recoveryPending=${remaining.recovery}" + probeDetails +
                                " nonblank=${readings.any { it.rawText.isNotBlank() }}"),
                            article.perceivedAt, null, emptyList(), capturedAt,
                        )
                        readings.filter { it.rawText.isNotBlank() }.forEach { reading ->
                            match.custody.submitTestimony(
                                sourceId, RawTestimony(reading.rawText, reading), article.perceivedAt, null,
                                listOf(article.id.value), capturedAt,
                            )
                        }
                        if (readings.any { MatchOutcomeTextResolver.resolve(it.rawText) != null }) {
                            // Stop collection and all lanes, not just this worker.
                            // Typed phrase witnesses independently consume the text.
                            pending.close()
                            witnessScope.cancel("Decisive result recorded")
                            return@launch
                        }
                    }
                    // The count check can demote expired recent frames even when
                    // there is no following capture to wake recovery.
                    if (lane == OutcomeReadLane.RECENT && pending.counts(now()).recovery > 0) recoveryWake.trySend(Unit)
                }
            }
            launchLane(OutcomeReadLane.RECENT, recentWake)
            launchLane(OutcomeReadLane.RECOVERY, recoveryWake)
            launchLane(OutcomeReadLane.PROBE, probeWake)
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}

/** Admission is separate from crop selection, so a waiting turn never fixes stale pixels. */
internal interface OutcomeCropReader {
    suspend fun warmUp()
    /** Scheduling hint only; weak/failed probes still retain an OCR fallback. */
    suspend fun inspect(article: RealityArticle): TransientTextProbe.Reading? = null
    suspend fun <T> withReadingTurn(priority: OcrPriority, block: suspend (OutcomeCropReadingTurn) -> T): T
}

internal interface OutcomeCropReadingTurn {
    val nativeReadNanos: Long
    val readCount: Int
    suspend fun recognize(article: RealityArticle): List<OcrTextEvidence>
}

private class ArtifactOutcomeCropReader(private val artifactStore: FileCropArtifactStore) : OutcomeCropReader {
    override suspend fun warmUp() { OutcomeTextRecognizer.warmUp() }
    override suspend fun inspect(article: RealityArticle): TransientTextProbe.Reading? = withContext(Dispatchers.IO) {
        val crop = article.payload as CropCaptured
        val bitmap = artifactStore.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return@withContext null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val excluded = crop.overlayBounds?.let {
                listOf(TransientTextProbe.PixelBounds(kotlin.math.floor(it.left).toInt(),
                    kotlin.math.floor(it.top).toInt(), kotlin.math.ceil(it.right).toInt(), kotlin.math.ceil(it.bottom).toInt()))
            }.orEmpty()
            TransientTextProbe.analyze(pixels, bitmap.width, bitmap.height, excluded)
        } finally { bitmap.recycle() }
    }
    override suspend fun <T> withReadingTurn(
        priority: OcrPriority,
        block: suspend (OutcomeCropReadingTurn) -> T,
    ): T = OutcomeTextRecognizer.withReadingTurn(priority) {
        val nativeTurn = this
        block(object : OutcomeCropReadingTurn {
            override val nativeReadNanos: Long get() = nativeTurn.nativeReadNanos
            override val readCount: Int get() = nativeTurn.readCount
            override suspend fun recognize(article: RealityArticle): List<OcrTextEvidence> {
                val crop = article.payload as CropCaptured
                val bitmap = artifactStore.loadVerifiedPng(crop.artifact, crop.cropProvenance) ?: return emptyList()
                return try {
                    nativeTurn.recognizeCandidates(bitmap) { MatchOutcomeTextResolver.resolve(it) != null }
                } finally { bitmap.recycle() }
            }
        })
    }
}

private enum class OutcomeReadLane { RECENT, RECOVERY, PROBE }

internal enum class MatchOutcomePhrase { WIN, LOSS }

/** Tolerates one OCR edit inside the gated phrase while refusing unrelated words. */
internal object MatchOutcomeTextResolver {
    fun resolve(rawText: String): MatchOutcomePhrase? {
        val letters = rawText.uppercase().filter(Char::isLetter)
        return when (letters) {
            "YOUWIN" -> MatchOutcomePhrase.WIN
            "GOODEFFORT" -> MatchOutcomePhrase.LOSS
            else -> when {
                editDistanceAtMostOne(letters, "YOUWIN") -> MatchOutcomePhrase.WIN
                editDistanceAtMostOne(letters, "GOODEFFORT") -> MatchOutcomePhrase.LOSS
                else -> null
            }
        }
    }

    private fun editDistanceAtMostOne(actual: String, expected: String): Boolean {
        if (kotlin.math.abs(actual.length - expected.length) > 1) return false
        if (actual == expected) return true
        if (actual.length == expected.length) {
            return actual.indices.count { actual[it] != expected[it] } <= 1
        }
        val shorter = if (actual.length < expected.length) actual else expected
        val longer = if (actual.length < expected.length) expected else actual
        var shortIndex = 0
        var longIndex = 0
        var skipped = false
        while (shortIndex < shorter.length && longIndex < longer.length) {
            if (shorter[shortIndex] == longer[longIndex]) {
                shortIndex++
                longIndex++
            } else if (!skipped) {
                skipped = true
                longIndex++
            } else {
                return false
            }
        }
        return true
    }
}

/** Derives one typed outcome signal from the shared immutable outcome-text article. */
class PersistedOutcomePhraseWitness(
    private val accepts: (String) -> Boolean,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                var accepted = false
                match.articles.collect { article ->
                    if (accepted || article.sourceId.id != "MATCH_OUTCOME_TEXT_WITNESS") return@collect
                    val text = (article.payload as? RawTestimony)?.data as? String ?: return@collect
                    if (!accepts(text)) return@collect
                    match.custody.submitTestimony(
                        sourceId, RawTestimony(text), article.perceivedAt, null,
                        listOf(article.id.value), article.monotonicTimeNanos ?: return@collect
                    )
                    accepted = true
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}
