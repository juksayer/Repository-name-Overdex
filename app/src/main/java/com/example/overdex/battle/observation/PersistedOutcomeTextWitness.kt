package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchOutcomeLabelWitnessed
import com.example.overdex.battle.custody.ObservationRestartedByUser
import com.example.overdex.battle.custody.ObservationSessionStoppedByUser
import com.example.overdex.battle.custody.ScreenIgnoredByUser
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
import com.example.overdex.model.BattleResult

/** Reads near-KO and promising result pixels urgently, with routine fallback checks for concessions. */
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
            val probePending = OutcomeProbeEvidenceQueue<RealityArticle>()
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
            // Partial glyph confirmation belongs to the phrase witness. End
            // reading only after its accepted evidence actually ends the Match,
            // rather than duplicating that state machine in every OCR lane.
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.collect { article ->
                    if (article.payload is MatchEnded) witnessScope.cancel("Match result accepted")
                }
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
            // Inspect sparse checks separately so their I/O cannot delay urgent
            // captures. A weak visual heuristic must not keep a brief result
            // behind the routine OCR backlog, or let a newer blank replace it.
            // Both lanes are urgent. The normal check is 0.5 Hz; a bounded late
            // charged-result tail is 2 Hz, with at most 16 references / eight seconds.
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
                    if (visual?.hasText == true && visual.signature != 0L) {
                        val capturedAt = article.monotonicTimeNanos ?: return@collect
                        if (pending.offer(article, capturedAt, now())) {
                            recentWake.trySend(Unit)
                            recoveryWake.trySend(Unit)
                        }
                    } else {
                        val capturedAt = article.monotonicTimeNanos ?: return@collect
                        probePending.offer(article, capturedAt, now())
                        probeWake.trySend(Unit)
                    }
                }
            }
            // Pay model startup while the battle is underway, before the brief result.
            witnessScope.launch { reader.warmUp() }
            fun launchLane(lane: OutcomeReadLane, wake: Channel<Unit>) = witnessScope.launch {
                val priority = if (lane == OutcomeReadLane.RECOVERY) OcrPriority.NORMAL else OcrPriority.URGENT
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
                        var probeSelection: OutcomeProbeEvidenceQueue.Selection<RealityArticle>? = null
                        val readings: List<OcrTextEvidence> = try {
                            reader.withReadingTurn(priority) { turn ->
                                admittedAt = now()
                                // Selection and expiry happen after admission.
                                // A newer blank cannot leap over a brief recent result.
                                val article = when (lane) {
                                    OutcomeReadLane.RECENT -> pending.pollRecent(admittedAt)
                                    OutcomeReadLane.RECOVERY -> pending.pollRecovery(admittedAt)
                                    OutcomeReadLane.PROBE -> probePending.select(admittedAt)
                                        ?.also { probeSelection = it }?.value
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
                        // The no-shields banner lives below the announcement crops on some
                        // layouts. Reuse its existing OCR immediately, before custody publication.
                        // Invalid/blank passes never advance the phase episode: these lanes may
                        // complete out of source order. Raw result testimony remains unchanged.
                        if (observerId.id == "MATCH_OUTCOME_TEXT_WITNESS") {
                            readings.forEach { reading ->
                                match.acceptedChargeCapturePreparation.offerOutcomePrompt(article, reading, completedAt)
                            }
                        }
                        val probeDetails = probeSelection?.let {
                            " selection=RETAINED_FALLBACK probePending=${it.pendingCount} " +
                                "overflowTotal=${it.overflowTotal} expiredTotal=${it.expiredTotal}"
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
                                "recentPending=${remaining.recent} recoveryPending=${remaining.recovery} " +
                                "promotedProbe=${article.sourceId.id == probeSource && lane != OutcomeReadLane.PROBE}" + probeDetails +
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
    override val name: String,
    private val allowPartialOutcome: Boolean = false,
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                var accepted = false
                var paused = false
                var attemptFrom = Long.MIN_VALUE
                var lifecycleAt = Long.MIN_VALUE
                val crops = LinkedHashMap<String, RealityArticle>()
                var confirmation = PartialOutcomeConfirmation()
                match.articles.collect { article ->
                    if (accepted) return@collect
                    val at = article.monotonicTimeNanos ?: return@collect
                    when (article.payload) {
                        ObservationRestartedByUser -> {
                            if (at > lifecycleAt) {
                                lifecycleAt = at
                                attemptFrom = at
                                paused = false
                                crops.clear()
                                confirmation = PartialOutcomeConfirmation()
                            }
                            return@collect
                        }
                        ScreenIgnoredByUser, ObservationSessionStoppedByUser -> {
                            if (at >= lifecycleAt) {
                                lifecycleAt = at
                                paused = true
                                crops.clear()
                                confirmation = PartialOutcomeConfirmation()
                            }
                            return@collect
                        }
                        else -> Unit
                    }
                    if (paused || at < attemptFrom) return@collect
                    if (article.payload is MatchEnded) {
                        accepted = true
                        crops.clear()
                        return@collect
                    }
                    if (allowPartialOutcome) {
                        val crop = article.payload as? CropCaptured
                        if (crop?.cropProvenance?.cropName == BattleCropContracts.matchOutcome.cropName) {
                            crops[article.id.value] = article
                            // Metadata only. Most OCR finishes while its crop
                            // is here; older recovery reads can use the ledger.
                            while (crops.size > 256) crops.remove(crops.keys.first())
                            return@collect
                        }
                    }
                    if (article.sourceId.id != "MATCH_OUTCOME_TEXT_WITNESS") return@collect
                    val raw = article.payload as? RawTestimony ?: return@collect
                    val text = raw.data as? String ?: return@collect
                    if (!accepts(text)) {
                        if (!allowPartialOutcome) return@collect
                        val reading = raw.ocr ?: return@collect
                        val references = article.evidenceReferences.orEmpty()
                        if (references.isEmpty()) return@collect
                        val cropArticle = (references.firstNotNullOfOrNull(crops::get)
                            // Recovery retains old references beyond the small
                            // cache. Resolve a cache miss from immutable metadata,
                            // without loading PNGs or rescanning on normal reads.
                            ?: match.realityTimeline.getArticles().firstOrNull {
                                it.id.value in references &&
                                    (it.payload as? CropCaptured)?.cropProvenance?.cropName ==
                                    BattleCropContracts.matchOutcome.cropName
                            })
                            ?.takeIf { it.monotonicTimeNanos == at } ?: return@collect
                        val crop = cropArticle.payload as? CropCaptured ?: return@collect
                        val decision = PartialOutcomeTextResolver.resolve(reading, crop.cropProvenance)
                        val support = confirmation.accept(decision, cropArticle.id.value, at, article.id.value)
                            ?: return@collect
                        val canonical = when (support.decision.phrase) {
                            MatchOutcomePhrase.WIN -> "YOU WIN!"
                            MatchOutcomePhrase.LOSS -> "GOOD EFFORT"
                        }
                        if (!accepts(canonical)) return@collect
                        match.custody.submitTestimony(
                            sourceId,
                            MatchOutcomeLabelWitnessed(
                                result = if (support.decision.phrase == MatchOutcomePhrase.WIN) BattleResult.WIN else BattleResult.LOSS,
                                rawText = text,
                                basis = support.decision.basis.name,
                                ocr = reading,
                                supportingFrameCount = support.rawArticleIds.size,
                            ),
                            article.perceivedAt, null, support.rawArticleIds, at,
                        )
                        accepted = true
                        crops.clear()
                        return@collect
                    }
                    match.custody.submitTestimony(
                        sourceId, RawTestimony(text, raw.ocr), article.perceivedAt, null,
                        listOf(article.id.value), at
                    )
                    accepted = true
                    crops.clear()
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}
