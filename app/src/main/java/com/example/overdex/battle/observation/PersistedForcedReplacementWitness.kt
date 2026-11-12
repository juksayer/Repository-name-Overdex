package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.ForcedReplacementPromptWitnessed
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrOverlayPolicy
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OcrSourceContext
import com.example.overdex.data.observation.OcrWordGeometry
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.observation.OcrTextEvidence
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** Reads only its preserved prompt strip and reports text, never a faint or the outgoing identity. */
class PersistedForcedReplacementWitness internal constructor(
    private val reader: ForcedReplacementCropReader,
    private val needsReading: (Long) -> Boolean = { true },
    private val now: () -> Long = System::nanoTime,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Observer {
    constructor(artifactStore: FileCropArtifactStore, needsReading: (Long) -> Boolean = { true }) :
        this(ArtifactForcedReplacementCropReader(artifactStore), needsReading)

    override val observerId = ObserverId("FORCED_REPLACEMENT_MESSAGE_WITNESS", ObservationSource.SCREEN_CAPTURE)
    override val name = "Forced Replacement Message"
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(dispatcher + SupervisorJob()).also { worker ->
            worker.launch(start = CoroutineStart.UNDISPATCHED) {
                val policy = ForcedReplacementPhrasePolicy()
                // At most one waiting crop reference; recognition has no frame or
                // Bitmap backlog. The independent capture retains every admitted PNG.
                match.articles.filter {
                    (it.payload as? CropCaptured)?.cropProvenance?.cropName ==
                        BattleCropContracts.forcedReplacementMessage.cropName
                }.conflate().collect { article ->
                    val at = article.monotonicTimeNanos ?: return@collect
                    fun fresh(): Boolean = at <= now() && now() - at < ForcedReplacementCaptureGate.WINDOW_NANOS &&
                        needsReading(at)
                    if (!fresh()) return@collect
                    try {
                        val reading = reader.read(article, ::fresh) ?: return@collect
                        currentCoroutineContext().ensureActive()
                        // Even if the assignment expired during a native read,
                        // its unchanged text remains auditable at the crop's time.
                        val receipt = match.custody.submitTestimony(SourceId("${observerId.id}_OCR"),
                            RawTestimony(reading.rawText, reading), article.perceivedAt, null,
                            listOf(article.id.value), at)
                        val raw = match.articles.first { it.sequenceNumber == receipt.sequenceNumber }
                        if (!fresh()) return@collect
                        val resolution = policy.observe(reading.acceptedText, at,
                            listOf(article.id.value, raw.id.value)) ?: return@collect
                        if (!fresh()) return@collect
                        match.custody.submitTestimony(SourceId(observerId.id),
                            ForcedReplacementPromptWitnessed(reading.rawText, resolution.basis, reading),
                            article.perceivedAt, null, resolution.evidenceReferences, at)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e("ForcedReplacement", "Unable to read preserved replacement message", error)
                    }
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Replacement message witness stopped"); scope = null }
}

internal fun interface ForcedReplacementCropReader {
    suspend fun read(article: RealityArticle, isFresh: () -> Boolean): OcrTextEvidence?
}

private class ArtifactForcedReplacementCropReader(private val artifacts: FileCropArtifactStore) : ForcedReplacementCropReader {
    private val heightProfile = OcrHeightProfile(OcrField.ANNOUNCEMENT)
    override suspend fun read(article: RealityArticle, isFresh: () -> Boolean): OcrTextEvidence? =
        SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) { read ->
            if (!isFresh()) return@withReadingTurn null
            val crop = article.payload as CropCaptured
            val bitmap = withContext(Dispatchers.IO) {
                artifacts.loadVerifiedPng(crop.artifact, crop.cropProvenance)
            } ?: return@withReadingTurn null
            try {
                if (!isFresh()) return@withReadingTurn null
                OcrWordGeometry.read(bitmap, bitmap, 0, "RAW",
                    OcrSourceContext(crop.cropProvenance.sourceWidth, crop.overlayBounds, OcrOverlayPolicy.SMALL_WORDS),
                    heightProfile, read = read)
            } finally { bitmap.recycle() }
        }
}
