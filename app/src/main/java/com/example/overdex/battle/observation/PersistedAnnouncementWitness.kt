package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OcrSourceContext
import com.example.overdex.model.observation.OcrTextEvidence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Reads the latest preserved announcement crop selected after shared OCR admission. */
class PersistedAnnouncementWitness internal constructor(
    private val reader: AnnouncementCropReader,
    private val now: () -> Long = System::nanoTime,
    override val observerId: ObserverId = ObserverId("ANNOUNCEMENT_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Announcement",
    private val cropContract: BattleCropContract = BattleCropContracts.announcement,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Observer {
    constructor(
        artifactStore: FileCropArtifactStore,
        observerId: ObserverId = ObserverId("ANNOUNCEMENT_WITNESS", ObserverSource.SCREEN_CAPTURE),
        name: String = "Announcement",
        cropContract: BattleCropContract = BattleCropContracts.announcement,
    ) : this(ArtifactAnnouncementCropReader(artifactStore), System::nanoTime, observerId, name, cropContract)

    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        val rawSourceId = SourceId("${observerId.id}_OCR")
        val heightProfile = OcrHeightProfile(OcrField.ANNOUNCEMENT)
        scope = CoroutineScope(dispatcher + SupervisorJob()).also { witnessScope ->
            // This bounds scheduling to one artifact reference. Replaced or
            // out-of-order crops remain immutable Articles in the Timeline.
            val pending = AtomicReference(PendingAnnouncement())
            val wake = Channel<Unit>(Channel.CONFLATED)
            witnessScope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion {
                pending.updateAndGet { it.copy(article = null) }
                wake.close()
            }
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                var lastOfferedAt = Long.MIN_VALUE
                match.articles.filter { article ->
                    (article.payload as? CropCaptured)?.cropProvenance?.cropName == cropContract.cropName
                }.collect { article ->
                    val capturedAt = article.monotonicTimeNanos ?: return@collect
                    if (capturedAt <= lastOfferedAt) return@collect
                    lastOfferedAt = capturedAt
                    // Keep the replacement count and selected reference in one
                    // atomic state, including when admission races this offer.
                    pending.updateAndGet {
                        PendingAnnouncement(article, it.replacedTotal + if (it.article == null) 0 else 1)
                    }
                    wake.trySend(Unit)
                }
            }
            witnessScope.launch {
                val speciesNames = match.pokemonKnowledge.getAllSpeciesNames()
                for (signal in wake) {
                    while (pending.get().article != null) {
                        val queuedAt = now()
                        var admittedAt = queuedAt
                        var sample: RealityArticle? = null
                        var nativeReadNanos = 0L
                        var passes = 0
                        var replacedAtSelection = 0L
                        val readings = try {
                            reader.withReadingTurn(OcrPriority.TRANSIENT) { turn ->
                                currentCoroutineContext().ensureActive()
                                admittedAt = now()
                                // Do not choose or load an image while this turn
                                // is still queued behind another OCR consumer.
                                val selected = pending.getAndUpdate { it.copy(article = null) }
                                val article = selected.article
                                    ?: return@withReadingTurn emptyList()
                                sample = article
                                replacedAtSelection = selected.replacedTotal
                                try { turn.recognize(article, heightProfile) } finally {
                                    nativeReadNanos = turn.nativeReadNanos
                                    passes = turn.readCount
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Log.w("ANNOUNCEMENT", "Recognition failed for ${sample?.id?.value}", error)
                            emptyList()
                        }
                        // Native callbacks may complete after Stop; none of
                        // their late text or diagnostics may enter custody.
                        currentCoroutineContext().ensureActive()
                        val article = sample ?: continue
                        val crop = article.payload as CropCaptured
                        val capturedAt = article.monotonicTimeNanos ?: continue
                        val completedAt = now()
                        match.custody.submitTestimony(
                            SourceId("${observerId.id}_LATENCY"),
                            RawTestimony("cropId=${article.id.value} priority=${OcrPriority.TRANSIENT} " +
                                "queueMs=${(admittedAt - queuedAt) / 1_000_000} " +
                                "captureToAdmissionMs=${(admittedAt - capturedAt) / 1_000_000} " +
                                "nativeReadMs=${nativeReadNanos / 1_000_000} " +
                                "processingMs=${(completedAt - admittedAt) / 1_000_000} " +
                                "totalMs=${(completedAt - capturedAt) / 1_000_000} " +
                                "passes=$passes replacedPendingTotal=$replacedAtSelection " +
                                "pending=${if (pending.get().article == null) 0 else 1} " +
                                "nonblank=${readings.any { it.rawText.isNotBlank() }}"),
                            article.perceivedAt, null, emptyList(), capturedAt,
                        )
                        // Failed/missing artifacts have no reading to report.
                        if (readings.isEmpty()) continue
                        val context = OcrSourceContext(crop.cropProvenance.sourceWidth, crop.overlayBounds)
                        var acceptedText = ""
                        val references = mutableListOf(article.id.value)
                        for (reading in readings) {
                            currentCoroutineContext().ensureActive()
                            val receipt = match.custody.submitTestimony(rawSourceId,
                                RawTestimony(reading.rawText, reading), article.perceivedAt, null,
                                listOf(article.id.value), capturedAt)
                            val rawArticle = match.articles.first { it.sequenceNumber == receipt.sequenceNumber }
                            references += rawArticle.id.value
                            if (acceptedText.isBlank()) acceptedText = reading.acceptedText
                            val announcedSpecies = ENTRY.matchEntire(reading.acceptedText.trim())?.groupValues?.get(1)
                            heightProfile.learn(reading, context, announcedSpecies != null &&
                                speciesNames.any { it.equals(announcedSpecies, ignoreCase = true) })
                        }
                        currentCoroutineContext().ensureActive()
                        // Existing consumers receive surviving words; the raw
                        // Articles above retain every word returned by ML Kit.
                        match.custody.submitTestimony(sourceId, RawTestimony(acceptedText), article.perceivedAt,
                            null, references, capturedAt)
                    }
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }

    private companion object {
        val ENTRY = Regex("^Go,\\s+(.+?)[!.]?$", RegexOption.IGNORE_CASE)
    }

    private data class PendingAnnouncement(val article: RealityArticle? = null, val replacedTotal: Long = 0)
}

/** The backend grants a turn before the witness chooses its current crop. */
internal interface AnnouncementCropReader {
    suspend fun <T> withReadingTurn(priority: OcrPriority, block: suspend (AnnouncementCropReadingTurn) -> T): T
}

internal interface AnnouncementCropReadingTurn {
    val nativeReadNanos: Long
    val readCount: Int
    suspend fun recognize(article: RealityArticle, profile: OcrHeightProfile): List<OcrTextEvidence>
}

private class ArtifactAnnouncementCropReader(private val artifactStore: FileCropArtifactStore) : AnnouncementCropReader {
    override suspend fun <T> withReadingTurn(
        priority: OcrPriority,
        block: suspend (AnnouncementCropReadingTurn) -> T,
    ): T = AnnouncementRecognizer.withReadingTurn(priority) {
        val nativeTurn = this
        block(object : AnnouncementCropReadingTurn {
            override val nativeReadNanos: Long get() = nativeTurn.nativeReadNanos
            override val readCount: Int get() = nativeTurn.readCount
            override suspend fun recognize(article: RealityArticle, profile: OcrHeightProfile): List<OcrTextEvidence> =
                withContext(Dispatchers.IO) {
                    val crop = article.payload as CropCaptured
                    val bitmap = artifactStore.loadVerifiedPng(crop.artifact, crop.cropProvenance)
                        ?: return@withContext emptyList()
                    try {
                        nativeTurn.recognizeEvidence(bitmap,
                            OcrSourceContext(crop.cropProvenance.sourceWidth, crop.overlayBounds), profile)
                    } finally { bitmap.recycle() }
                }
        })
    }
}
