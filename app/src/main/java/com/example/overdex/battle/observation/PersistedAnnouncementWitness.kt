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
import com.example.overdex.data.observation.OcrOverlayPolicy
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Retains brief visual text episodes and selects preserved pixels after shared OCR admission. */
class PersistedAnnouncementWitness internal constructor(
    private val reader: AnnouncementCropReader,
    private val now: () -> Long = System::nanoTime,
    override val observerId: ObserverId = ObserverId("ANNOUNCEMENT_WITNESS", ObserverSource.SCREEN_CAPTURE),
    override val name: String = "Announcement",
    private val cropContract: BattleCropContract = BattleCropContracts.announcement,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val directCapture: Boolean = false,
) : Observer {
    constructor(
        artifactStore: FileCropArtifactStore,
        observerId: ObserverId = ObserverId("ANNOUNCEMENT_WITNESS", ObserverSource.SCREEN_CAPTURE),
        name: String = "Announcement",
        cropContract: BattleCropContract = BattleCropContracts.announcement,
    ) : this(ArtifactAnnouncementCropReader(artifactStore), System::nanoTime, observerId, name, cropContract)

    internal constructor(store: FileCropArtifactStore, preview: LiveAnnouncementPreview) :
        this(ArtifactAnnouncementCropReader(store, preview), directCapture = true)

    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        val rawSourceId = SourceId("${observerId.id}_OCR")
        val heightProfile = OcrHeightProfile(OcrField.ANNOUNCEMENT)
        scope = CoroutineScope(dispatcher + SupervisorJob()).also { witnessScope ->
            // Keep only bounded artifact references, never queued Bitmaps or
            // unbounded native requests. Probe results are scheduling hints;
            // only OCR of the selected preserved crop can produce testimony.
            val pending = PendingTransientTextCrops()
            val gradeGate = if (cropContract == BattleCropContracts.qteProgressText) QteGradeReadGate() else null
            val recentSleep = ArrayDeque<Pair<RealityArticle, TransientTextProbe.Reading?>>()
            val gradeSignatures = LinkedHashMap<Long, Long>()
            val wake = Channel<Unit>(Channel.CONFLATED)
            witnessScope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion {
                pending.clear()
                wake.close()
            }
            witnessScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val crops = if (directCapture) match.capturedAnnouncements else match.articles
                crops.collect { article ->
                    val at = article.monotonicTimeNanos ?: return@collect
                    if ((article.payload as? CropCaptured)?.cropProvenance?.cropName != cropContract.cropName) {
                        if (gradeGate != null) {
                            if (article.sourceId.id == observerId.id && article.payload is RawTestimony) {
                                gradeGate.confirm(article.payload.data as? String ?: "", at, gradeSignatures[at] ?: 0L)
                            }
                            val start = gradeGate.observe(article.payload, at, article.sourceId.id)
                            if (start != null) {
                                val iterator = recentSleep.iterator()
                                while (iterator.hasNext()) {
                                    val (old, probe) = iterator.next()
                                    val oldAt = old.monotonicTimeNanos ?: continue
                                    if (oldAt >= start && gradeGate.inCharge(oldAt) && pending.canOffer(old) &&
                                        now() - oldAt in 0..10_000_000_000L) {
                                        match.processingDiagnostics?.recordGradeDecision("lateCue", old.id.value, oldAt)
                                        pending.offer(old, probe)
                                        iterator.remove()
                                        wake.trySend(Unit)
                                    }
                                }
                            }
                        }
                        return@collect
                    }
                    if (!pending.canOffer(article)) return@collect
                    val visual = try { reader.inspect(article) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        Log.w("ANNOUNCEMENT", "Visual scheduling probe failed; retaining latest fallback", error)
                        null
                    }
                    currentCoroutineContext().ensureActive()
                    if (gradeGate != null) {
                        val decision = gradeGate.decide(at, visual)
                        match.processingDiagnostics?.recordGradeDecision(decision.name, article.id.value, at)
                        if (!decision.read) {
                            if (decision == QteGradeReadGate.Reason.NO_GRADE) {
                                if (recentSleep.size == 3) recentSleep.removeFirst()
                                recentSleep.addLast(article to visual)
                            }
                            return@collect
                        }
                        gradeSignatures[at] = visual?.signature ?: 0L
                        if (gradeSignatures.size > 32) gradeSignatures.remove(gradeSignatures.keys.first())
                    }
                    pending.offer(article, visual)
                    wake.trySend(Unit)
                }
            }
            witnessScope.launch {
                val speciesNames = match.pokemonKnowledge.getAllSpeciesNames()
                for (signal in wake) {
                    while (pending.hasPending(now())) {
                        val queuedAt = now()
                        var admittedAt = queuedAt
                        var sample: RealityArticle? = null
                        var nativeReadNanos = 0L
                        var passes = 0
                        var replacedAtSelection = 0L
                        var selection: PendingTransientTextCrops.Selection? = null
                        val readings = try {
                            if (gradeGate != null) match.processingDiagnostics?.recordReaderWork("grade.queueRequest")
                            reader.withReadingTurn(OcrPriority.TRANSIENT) { turn ->
                                currentCoroutineContext().ensureActive()
                                admittedAt = now()
                                // Native recognition still chooses its crop
                                // only after admission. The cheap visual probe
                                // has retained brief messages that a newer blank
                                // frame must not erase while this turn waits.
                                val selected = pending.select(admittedAt)
                                    ?: return@withReadingTurn emptyList()
                                selection = selected
                                val article = selected.article
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
                        var acceptedText = readings.firstOrNull { it.acceptedText.isNotBlank() }?.acceptedText.orEmpty()
                        if (cropContract == BattleCropContracts.announcement) {
                            acceptedText = extractCompleteUsedAnnouncement(acceptedText, match.pokemonKnowledge.chargedMoveNames())
                        }
                        val vocabularyResolution = if (observerId.id == "QTE_PROGRESS_TEXT_WITNESS" &&
                            cropContract == BattleCropContracts.qteProgressText)
                            resolveQteProgressText(acceptedText) else null
                        // The preserved crop already exists. This small scheduling handoff
                        // must not wait behind raw/accepted/typed publication. It carries
                        // no battle event; the complete evidence chain still follows below.
                        if (readings.isNotEmpty()) match.acceptedChargeCapturePreparation.offer(
                            observerId.id, cropContract, article,
                            vocabularyResolution?.acceptedText ?: acceptedText, completedAt)
                        match.custody.submitTestimony(
                            SourceId("${observerId.id}_LATENCY"),
                            RawTestimony("cropId=${article.id.value} priority=${OcrPriority.TRANSIENT} " +
                                "queueMs=${(admittedAt - queuedAt) / 1_000_000} " +
                                "captureToAdmissionMs=${(admittedAt - capturedAt) / 1_000_000} " +
                                "nativeReadMs=${nativeReadNanos / 1_000_000} " +
                                "processingMs=${(completedAt - admittedAt) / 1_000_000} " +
                                "totalMs=${(completedAt - capturedAt) / 1_000_000} " +
                                "passes=$passes replacedPendingTotal=$replacedAtSelection " +
                                "selection=${selection?.kind} buffered=${selection?.pendingCount ?: 0} " +
                                "coalescedTotal=${selection?.coalescedTotal ?: 0} " +
                                "overflowTotal=${selection?.overflowTotal ?: 0} expiredTotal=${selection?.expiredTotal ?: 0} " +
                                "pending=${if (pending.hasPending(completedAt)) 1 else 0} " +
                                "nonblank=${readings.any { it.rawText.isNotBlank() }}"),
                            article.perceivedAt, null, emptyList(), capturedAt,
                        )
                        // Failed/missing artifacts have no reading to report.
                        if (readings.isEmpty()) {
                            selection?.let(pending::failed)
                            continue
                        }
                        selection?.let {
                            if (readings.none { reading -> reading.rawText.isNotBlank() }) pending.failed(it)
                            else pending.succeeded(it)
                        }
                        val context = OcrSourceContext(crop.cropProvenance.sourceWidth, crop.overlayBounds, OcrOverlayPolicy.SMALL_WORDS)
                        val references = mutableListOf(article.id.value)
                        for (reading in readings) {
                            currentCoroutineContext().ensureActive()
                            val receipt = match.custody.submitTestimony(rawSourceId,
                                RawTestimony(reading.rawText, reading), article.perceivedAt, null,
                                listOf(article.id.value), capturedAt)
                            references += if (directCapture) match.announcementEvidenceId(receipt).value
                                else match.awaitRawArticlePublished(receipt.sequenceNumber).id.value
                            val announcedSpecies = ENTRY.matchEntire(reading.acceptedText.trim())?.groupValues?.get(1)
                            heightProfile.learn(reading, context, announcedSpecies != null &&
                                speciesNames.any { it.equals(announcedSpecies, ignoreCase = true) })
                        }
                        currentCoroutineContext().ensureActive()
                        // Vocabulary repair belongs to this dedicated reader,
                        // before custody publishes accepted grade text. Native
                        // OCR remains untouched above; preserve a cited reason
                        // for the one observed alias rather than teaching the
                        // Timeline or replay to guess at malformed grades.
                        vocabularyResolution?.let { resolution ->
                            val receipt = match.custody.submitTestimony(
                                SourceId("${observerId.id}_VOCABULARY_RESOLUTION"),
                                RawTestimony("basis=${resolution.basis} original=${resolution.originalText} " +
                                    "accepted=${resolution.acceptedText}"),
                                article.perceivedAt, null, references.toList(), capturedAt)
                            val resolvedArticle = match.awaitRawArticlePublished(receipt.sequenceNumber)
                            references += resolvedArticle.id.value
                            acceptedText = resolution.acceptedText
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

}

private val USED_WORD = Regex("\\bused\\b", RegexOption.IGNORE_CASE)
private val COMPLETE_USED_LINE = Regex("^.+?\\s+used\\s+([\\p{L}\\p{N}][\\p{L}\\p{N} '’()\\-]*)!", RegexOption.IGNORE_CASE)

/** A known move ends at its own terminator. Keep unresolved or wrapped readings available for review. */
internal fun extractCompleteUsedAnnouncement(text: String, chargedMoveNames: Set<String> = emptySet()): String {
    val moveLines = text.lineSequence().map(String::trim).filter { USED_WORD.containsMatchIn(it) }.iterator()
    if (!moveLines.hasNext()) return text
    val first = moveLines.next()
    val banner = COMPLETE_USED_LINE.find(first) ?: return text
    if (banner.groupValues[1].lowercase() in chargedMoveNames) return banner.value
    return if (banner.value == first && !moveLines.hasNext()) first else text
}

/** The backend grants a turn before the witness chooses its current crop. */
internal interface AnnouncementCropReader {
    /** Non-semantic pixel ranking, outside the shared native OCR turn. Null retains the latest fallback. */
    suspend fun inspect(article: RealityArticle): TransientTextProbe.Reading? = null
    suspend fun <T> withReadingTurn(priority: OcrPriority, block: suspend (AnnouncementCropReadingTurn) -> T): T
}

internal interface AnnouncementCropReadingTurn {
    val nativeReadNanos: Long
    val readCount: Int
    suspend fun recognize(article: RealityArticle, profile: OcrHeightProfile): List<OcrTextEvidence>
}

private class ArtifactAnnouncementCropReader(
    private val artifactStore: FileCropArtifactStore,
    private val preview: LiveAnnouncementPreview? = null,
) : AnnouncementCropReader {
    override suspend fun inspect(article: RealityArticle): TransientTextProbe.Reading? = withContext(Dispatchers.IO) {
        val crop = article.payload as CropCaptured
        preview?.readingFor(article.monotonicTimeNanos, crop.cropProvenance)?.let { return@withContext it }
        val bitmap = artifactStore.loadVerifiedOcrPng(crop) ?: return@withContext null
        try {
            readTransientTextPixels(bitmap, crop.overlayBounds, crop.cropProvenance.cropName == BattleCropContracts.qteProgressText.cropName)
        } finally { bitmap.recycle() }
    }
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
                    val bitmap = artifactStore.loadVerifiedOcrPng(crop)
                        ?: return@withContext emptyList()
                    try {
                        nativeTurn.recognizeEvidence(bitmap,
                            OcrSourceContext(crop.cropProvenance.sourceWidth, crop.overlayBounds, OcrOverlayPolicy.SMALL_WORDS), profile)
                    } finally { bitmap.recycle() }
                }
        })
    }
}
