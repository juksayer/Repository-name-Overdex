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
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.observation.OcrTextEvidence

/** Reads recent result candidates promptly while recovering older crops at routine priority. */
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
            val recentWake = Channel<Unit>(Channel.CONFLATED)
            val recoveryWake = Channel<Unit>(Channel.CONFLATED)
            witnessScope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion {
                pending.close()
                recentWake.close()
                recoveryWake.close()
            }
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                match.articles.filter { article ->
                    (article.payload as? CropCaptured)?.cropProvenance?.cropName == BattleCropContracts.matchOutcome.cropName
                }.collect { article ->
                    val capturedAt = article.monotonicTimeNanos ?: return@collect
                    if (pending.offer(article, capturedAt, now())) {
                        recentWake.trySend(Unit)
                        recoveryWake.trySend(Unit)
                    }
                }
            }
            // Pay model startup while the battle is underway, before the brief result.
            witnessScope.launch { reader.warmUp() }
            fun launchLane(recent: Boolean, wake: Channel<Unit>) = witnessScope.launch {
                val lane = if (recent) "RECENT" else "RECOVERY"
                val priority = if (recent) OcrPriority.URGENT else OcrPriority.NORMAL
                for (signal in wake) {
                    while (pending.counts(now()).let { if (recent) it.recent else it.recovery } > 0) {
                        var sample: RealityArticle? = null
                        val queuedAt = now()
                        var admittedAt = queuedAt
                        var nativeReadNanos = 0L
                        var passes = 0
                        var remaining = pending.counts(queuedAt)
                        val readings: List<OcrTextEvidence> = try {
                            reader.withReadingTurn(priority) { turn ->
                                admittedAt = now()
                                // Selection and expiry happen after admission.
                                // A newer blank cannot leap over a brief recent result.
                                val article = if (recent) pending.pollRecent(admittedAt)
                                    else pending.pollRecovery(admittedAt)
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
                                "passes=$passes pending=${remaining.total} recentPending=${remaining.recent} " +
                                "recoveryPending=${remaining.recovery} nonblank=${readings.any { it.rawText.isNotBlank() }}"),
                            article.perceivedAt, null, emptyList(), capturedAt,
                        )
                        readings.filter { it.rawText.isNotBlank() }.forEach { reading ->
                            match.custody.submitTestimony(
                                sourceId, RawTestimony(reading.rawText, reading), article.perceivedAt, null,
                                listOf(article.id.value), capturedAt,
                            )
                        }
                        if (readings.any { MatchOutcomeTextResolver.resolve(it.rawText) != null }) {
                            // Stop collection and both lanes, not just this worker.
                            // Typed phrase witnesses independently consume the text.
                            pending.close()
                            witnessScope.cancel("Decisive result recorded")
                            return@launch
                        }
                    }
                    // The count check can demote expired recent frames even when
                    // there is no following capture to wake recovery.
                    if (recent && pending.counts(now()).recovery > 0) recoveryWake.trySend(Unit)
                }
            }
            launchLane(recent = true, recentWake)
            launchLane(recent = false, recoveryWake)
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }
}

/** Admission is separate from crop selection, so a waiting turn never fixes stale pixels. */
internal interface OutcomeCropReader {
    suspend fun warmUp()
    suspend fun <T> withReadingTurn(priority: OcrPriority, block: suspend (OutcomeCropReadingTurn) -> T): T
}

internal interface OutcomeCropReadingTurn {
    val nativeReadNanos: Long
    val readCount: Int
    suspend fun recognize(article: RealityArticle): List<OcrTextEvidence>
}

private class ArtifactOutcomeCropReader(private val artifactStore: FileCropArtifactStore) : OutcomeCropReader {
    override suspend fun warmUp() { OutcomeTextRecognizer.warmUp() }
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
