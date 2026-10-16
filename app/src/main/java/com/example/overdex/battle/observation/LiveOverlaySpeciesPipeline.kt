package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrSourceContext
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A durable species crop delivered after its CropCaptured Timeline article exists. */
internal data class PreservedSpeciesCrop(
    val crop: CropCaptured,
    val bitmap: android.graphics.Bitmap,
    val cropEvidenceId: String,
    val speciesCheckWindowId: Long,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long
)

/** A private bitmap copy made immediately while the published live frame is current. */
private data class LiveSpeciesCrop(
    val resolved: ResolvedBattleCrop,
    val overlayBounds: com.example.overdex.model.observation.OcrBox?,
    val speciesCheckWindowId: Long,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long
)

/**
 * Low-latency evidence lane modeled on StartOvermon's successful badge OCR.
 *
 * This lane owns active-badge OCR. It runs padded, thresholded and raw ML Kit
 * treatments over the already-preserved crop, then submits the first
 * catalogue-supported result to Custody immediately. Live presentation and
 * replay therefore consume the same immutable species testimony; later
 * testimony can confirm or refute it.
 */
internal class LiveOverlaySpeciesPipeline(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val artifactStore: FileCropArtifactStore,
    private val side: ActivePokemonSide,
    private val captureAllowed: () -> Boolean,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    override val managesAvailability: Boolean = true

    private val crops = Channel<PreservedSpeciesCrop>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { sample -> sample.bitmap.recycle() },
    )
    private val liveCrops = Channel<LiveSpeciesCrop>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { sample -> sample.resolved.bitmap.recycle() }
    )
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    @Volatile private var lastSubmittedSpecies: String? = null
    private var lastRawReadings: List<String> = emptyList()
    private var lastCaptureQueuedAtNanos = 0L
    private var captureOperating: Boolean? = null

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastSubmittedSpecies = null
        lastRawReadings = emptyList()
        lastCaptureQueuedAtNanos = 0L
        captureOperating = null
        val sourceId = SourceId(observerId.id)
        val rawSourceId = SourceId("${observerId.id}_OCR")
        val cropContract = when (side) {
            ActivePokemonSide.PLAYER -> BattleWitnessContracts.playerActiveSpeciesTextCapture
            ActivePokemonSide.OPPONENT -> BattleWitnessContracts.opponentActiveSpeciesTextCapture
        }
        val cropSourceId = SourceId(cropContract.witnessId)
        val heightProfile = OcrHeightProfile(OcrField.SPECIES)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { pipelineScope ->
            pipelineScope.launch {
                match.articles.collect { article ->
                    val check = article.payload as? SpeciesCheckMeasured ?: return@collect
                    if (check.side == side && check.status == "OPENED") {
                        // Permit the same species to produce a new identity article
                        // after it leaves and later returns to this side.
                        if (check.reason in setOf("ENTRY", "SWITCH", "FAINT", "CRY", "MATCH_START")) {
                            lastSubmittedSpecies = null
                        }
                    }
                }
            }
            // Copy the measured strip before PNG or OCR work. Identity must not
            // wait behind larger forensic captures. The one-item queue keeps the
            // freshest strip while the prior sample is being preserved.
            pipelineScope.launch(Dispatchers.Default) {
                input.supplyFrames { frame ->
                    // Always let the coordinator advance its recovery clock while
                    // the battle surface is visible. Short-circuiting here on the
                    // previous successful recognition prevented recovery windows
                    // from ever opening when a switch cue was missed.
                    val calibratedRegion = cropContract.crop.region.regionIn(calibration)
                    val enabled = captureAllowed() && match.speciesChecks.captureEnabled(side) &&
                        SpeciesCaptureVisibility.canRead(calibratedRegion, frame.overlayRegion) && frame.canObserve(calibratedRegion)
                    if (captureOperating != enabled) {
                        match.custody.submitAvailability(
                            cropSourceId,
                            enabled,
                            frame.capturedAtWallTimeMillis
                        )
                        captureOperating = enabled
                    }
                    if (!enabled) return@supplyFrames
                    if (lastCaptureQueuedAtNanos > 0L &&
                        frame.capturedAtMonotonicTimeNanos - lastCaptureQueuedAtNanos <
                        SpeciesCheckCoordinator.SAMPLE_INTERVAL_NANOS
                    ) return@supplyFrames
                    val window = match.speciesChecks.windowFor(
                        side,
                        frame.capturedAtMonotonicTimeNanos
                    ) ?: return@supplyFrames
                    // Locate the name's ink before spending an OCR pass on an
                    // empty row. This temporary geometry never rewrites calibration.
                    // Do not add a blind row offset after locating it: a failed
                    // pre-battle read otherwise walks a correct strip into the
                    // status bar and keeps displacing subsequent valid names.
                    val searchRegion = SpeciesBadgeTextLocator.locate(frame.bitmap, calibratedRegion)
                    if (!SpeciesCaptureVisibility.canRead(searchRegion, frame.overlayRegion) || !frame.canObserve(searchRegion)) return@supplyFrames
                    val resolved = BattleCropResolver.resolve(
                        cropName = cropContract.crop.cropName,
                        region = searchRegion,
                        source = frame.bitmap,
                        minimumSize = cropContract.crop.minimumSize,
                        area = cropContract.crop.areaInRegion,
                    )
                        ?: return@supplyFrames
                    lastCaptureQueuedAtNanos = frame.capturedAtMonotonicTimeNanos
                    liveCrops.trySend(
                        LiveSpeciesCrop(
                            resolved,
                            resolved.provenance.overlayBounds(frame.overlayRegion),
                            window.id,
                            frame.capturedAtWallTimeMillis,
                            frame.capturedAtMonotonicTimeNanos
                        )
                    )
                }
            }
            pipelineScope.launch {
                for (sample in liveCrops) {
                    var handedToOcr = false
                    try {
                        val artifact = artifactStore.preservePng(sample.resolved.bitmap)
                            ?: continue
                        val crop = CropCaptured(artifact, sample.resolved.provenance, sample.overlayBounds)
                        val accepted = match.custody.submitTestimony(
                            sourceId = cropSourceId,
                            payload = crop,
                            timestamp = sample.capturedAtWallTimeMillis,
                            confidence = null,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                        )
                        val cropArticle = match.articles.first { article ->
                            article.sequenceNumber == accepted.sequenceNumber &&
                                article.payload is CropCaptured
                        }
                        // The immutable raw crop is already on the Timeline.
                        // OCR does not wait for downstream inference consumers.
                        handedToOcr = crops.trySend(
                            PreservedSpeciesCrop(
                                crop = crop,
                                bitmap = sample.resolved.bitmap,
                                cropEvidenceId = cropArticle.id.value,
                                speciesCheckWindowId = sample.speciesCheckWindowId,
                                capturedAtWallTimeMillis = sample.capturedAtWallTimeMillis,
                                capturedAtMonotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                            )
                        ).isSuccess
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e(
                            "FAST_OVERLAY_SPECIES",
                            "Priority crop preservation failed side=$side",
                            error
                        )
                    } finally {
                        if (!handedToOcr) sample.resolved.bitmap.recycle()
                    }
                }
            }
            pipelineScope.launch {
                match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                // Deploying Droidball normally precedes the first battle badge.
                // Initialize the reader during that time instead of on the first name.
                try { SpeciesNameRecognizer.warmUp() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w("FAST_OVERLAY_SPECIES", "OCR warm-up failed; will retry on a crop", error) }
                val knownNames = match.pokemonKnowledge.getAllSpeciesNames()
                for (firstWaitingCrop in crops) {
                    var sample = firstWaitingCrop
                    val queuedAt = System.nanoTime()
                    var admittedAt = queuedAt
                    var nativeReadNanos = 0L
                    var passes = 0
                    var replacedCrops = 0
                    val readings = try {
                        SpeciesNameRecognizer.withReadingTurn {
                            admittedAt = System.nanoTime()
                            // Choose pixels after admission, not before waiting
                            // behind another reader. Preserve all raw crop articles;
                            // only the obsolete pending OCR work is superseded.
                            while (true) {
                                val newer = crops.tryReceive().getOrNull() ?: break
                                sample.bitmap.recycle()
                                sample = newer
                                replacedCrops++
                            }
                            if (!match.speciesChecks.needsReading(side, sample.speciesCheckWindowId,
                                    sample.capturedAtMonotonicTimeNanos)) return@withReadingTurn null
                            val context = OcrSourceContext(sample.crop.cropProvenance.sourceWidth, sample.crop.overlayBounds)
                            recognizeCandidates(sample.bitmap, context, heightProfile) { rawText ->
                                FastOverlaySpeciesResolver.resolveDetailed(rawText, knownNames) != null
                            }.also {
                                nativeReadNanos = this.nativeReadNanos
                                passes = readCount
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("FAST_OVERLAY_SPECIES", "OCR failed side=$side", error)
                        continue
                    } finally {
                        sample.bitmap.recycle()
                    }
                    readings ?: continue
                    val completedAt = System.nanoTime()
                    val sourceContext = OcrSourceContext(sample.crop.cropProvenance.sourceWidth, sample.crop.overlayBounds)
                    // Record where time was spent. These are diagnostics, not
                    // additional species votes or a replacement source timestamp.
                    match.custody.submitTestimony(
                        sourceId = SourceId("${observerId.id}_LATENCY"),
                        payload = RawTestimony("side=$side queueMs=${(admittedAt - queuedAt) / 1_000_000} " +
                            "captureToAdmissionMs=${(admittedAt - sample.capturedAtMonotonicTimeNanos) / 1_000_000} " +
                            "nativeReadMs=${nativeReadNanos / 1_000_000} processingMs=${(completedAt - admittedAt) / 1_000_000} " +
                            "totalMs=${(completedAt - sample.capturedAtMonotonicTimeNanos) / 1_000_000} " +
                            "passes=$passes replacedCrops=$replacedCrops"),
                        timestamp = sample.capturedAtWallTimeMillis,
                        confidence = null,
                        evidenceReferences = listOf(sample.cropEvidenceId),
                        monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos,
                    )

                    // Keep one empty-read diagnostic per measured position. Compact
                    // archives otherwise discard every failed strip, making a bad
                    // calibration indistinguishable from a capture lane that never ran.
                    val readingSignature = listOf("crop=${sample.crop.cropProvenance}") +
                        readings.map { "${it.treatment}:${it.evidence?.rawText}:${it.text}" }
                    if (readingSignature != lastRawReadings) {
                        lastRawReadings = readingSignature
                        val reportedReadings = readings.ifEmpty {
                            listOf(SpeciesNameRecognizer.Candidate("", "EMPTY"))
                        }
                        reportedReadings.forEach { reading ->
                            match.custody.submitTestimony(
                                sourceId = SourceId("${rawSourceId.id}_${reading.treatment}"),
                                payload = RawTestimony(reading.evidence?.rawText ?: reading.text, reading.evidence),
                                timestamp = sample.capturedAtWallTimeMillis,
                                confidence = null,
                                evidenceReferences = listOf(sample.cropEvidenceId),
                                monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                            )
                        }
                        Log.d(
                            "FAST_OVERLAY_SPECIES",
                            "read side=$side values=${readingSignature.joinToString()}"
                        )
                    }

                    val resolvedReading = readings.firstNotNullOfOrNull { reading ->
                        FastOverlaySpeciesResolver.resolveDetailed(reading.text, knownNames)
                            ?.let { resolution -> reading to resolution }
                    }
                    if (resolvedReading == null) continue
                    val (reading, resolution) = resolvedReading
                    reading.evidence?.let { evidence ->
                        heightProfile.learn(evidence, sourceContext,
                            knownNames.any { it.equals(reading.text.trim(), ignoreCase = true) })
                    }
                    if (resolution.speciesName.equals(lastSubmittedSpecies, ignoreCase = true)) {
                        // A recovery read that confirms the current combatant still
                        // completes its window. Leaving it open made OCR chase the
                        // unchanged badge until expiry and delayed later switches.
                        match.speciesChecks.acceptImmediate(
                            side = side,
                            windowId = sample.speciesCheckWindowId,
                            species = resolution.speciesName,
                            cropId = sample.cropEvidenceId,
                            capturedAtMonotonicTimeNanos = sample.capturedAtMonotonicTimeNanos,
                        )
                        continue
                    }
                    val species = match.pokemonKnowledge.getPokemonByName(resolution.speciesName) ?: continue
                    val evidenceReferences = match.speciesChecks.acceptImmediate(
                        side = side,
                        windowId = sample.speciesCheckWindowId,
                        species = species.name,
                        cropId = sample.cropEvidenceId,
                        capturedAtMonotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                    ) ?: continue
                    lastSubmittedSpecies = species.name
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = ActivePokemonSpeciesWitnessed(side, species.name, species.id),
                        timestamp = sample.capturedAtWallTimeMillis,
                        confidence = resolution.confidence,
                        evidenceReferences = evidenceReferences,
                        monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                    )
                    Log.i(
                        "FAST_OVERLAY_SPECIES",
                        "submitted side=$side species=${species.name} basis=${resolution.basis} " +
                        "confidence=${resolution.confidence} treatment=${reading.treatment} " +
                            "raw=${reading.text.replace("\n", "|")}"
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.let {
            // The availability record is accepted synchronously before cancellation.
            activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
            val cropWitnessId = when (side) {
                ActivePokemonSide.PLAYER -> BattleWitnessContracts.playerActiveSpeciesTextCapture.witnessId
                ActivePokemonSide.OPPONENT -> BattleWitnessContracts.opponentActiveSpeciesTextCapture.witnessId
            }
            if (captureOperating == true) {
                activeMatch?.custody?.submitAvailability(
                    SourceId(cropWitnessId),
                    false,
                    System.currentTimeMillis()
                )
            }
        }
        activeMatch = null
        captureOperating = false
        scope?.cancel("Live overlay species pipeline stopped")
        scope = null
    }

    companion object {
        fun player(
            input: ObservationInput,
            calibration: BattleCalibration,
            artifactStore: FileCropArtifactStore,
            captureAllowed: () -> Boolean
        ) = LiveOverlaySpeciesPipeline(
            input,
            calibration,
            artifactStore,
            ActivePokemonSide.PLAYER,
            captureAllowed,
            ObserverId("PLAYER_SPECIES_OVERLAY_PIPELINE", ObservationSource.SCREEN_CAPTURE),
            "Player Species Overlay Pipeline"
        )

        fun opponent(
            input: ObservationInput,
            calibration: BattleCalibration,
            artifactStore: FileCropArtifactStore,
            captureAllowed: () -> Boolean
        ) = LiveOverlaySpeciesPipeline(
            input,
            calibration,
            artifactStore,
            ActivePokemonSide.OPPONENT,
            captureAllowed,
            ObserverId("OPPONENT_SPECIES_OVERLAY_PIPELINE", ObservationSource.SCREEN_CAPTURE),
            "Opponent Species Overlay Pipeline"
        )
    }
}

internal data class FastOverlaySpeciesResolution(
    val speciesName: String,
    val confidence: Float,
    val basis: String
)

/** Lenient single-frame matching equivalent to Overmon's exact/prefix path. */
internal object FastOverlaySpeciesResolver {
    fun resolve(rawText: String, knownSpeciesNames: Set<String>): String? =
        resolveDetailed(rawText, knownSpeciesNames)?.speciesName

    fun resolveDetailed(rawText: String, knownSpeciesNames: Set<String>): FastOverlaySpeciesResolution? {
        val normalizedText = normalize(rawText)
        val normalizedLines = rawText.lineSequence()
            .map(::normalize)
            .filter(String::isNotEmpty)
            .toSet()
        knownSpeciesNames
            .filter { normalize(it).isNotEmpty() }
            .filter {
                val species = normalize(it)
                normalizedText == species || species in normalizedLines
            }
            .maxByOrNull { normalize(it).length }
            ?.let {
                val caseEvidence = SpeciesOcrTypography.caseEvidence(rawText, it)
                return FastOverlaySpeciesResolution(
                    it,
                    0.92f + caseEvidence * 0.06f,
                    if (caseEvidence == 1f) "EXACT_CATALOGUE_TEXT_EXPECTED_CASE" else "EXACT_CATALOGUE_TEXT_CASE_NORMALIZED"
                )
            }

        SpeciesTextResolver.resolve(rawText, knownSpeciesNames)?.let {
            return FastOverlaySpeciesResolution(it, 0.82f, "UNAMBIGUOUS_CATALOGUE_FUZZY_MATCH")
        }

        val tokens = rawText
            .replace(Regex("[^A-Za-z0-9]"), " ")
            .split(Regex("\\s+"))
            .map { normalize(it) }
            .filter { it.length >= 5 }

        for (token in tokens) {
            val matches = knownSpeciesNames.filter { species ->
                val normalizedSpecies = normalize(species)
                normalizedSpecies.length >= token.length &&
                    SpeciesOcrTypography.weightedDistance(token, normalizedSpecies.take(token.length)) <=
                        SpeciesOcrTypography.allowedCost(normalizedSpecies.length)
            }
            if (matches.size == 1) {
                return FastOverlaySpeciesResolution(matches.single(), 0.68f, "UNIQUE_OVERMON_PREFIX_MATCH")
            }
        }
        return null
    }

    private fun normalize(value: String): String = value.uppercase().filter(Char::isLetterOrDigit)

}
