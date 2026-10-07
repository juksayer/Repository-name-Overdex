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
import com.example.overdex.model.AnchorRegion
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

/** A durable species crop delivered immediately after Custody accepts CropCaptured. */
internal data class PreservedSpeciesCrop(
    val crop: CropCaptured,
    val bitmap: android.graphics.Bitmap,
    val cropEvidenceId: String,
    val searchIndex: Int,
    val speciesCheckWindowId: Long,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long
)

/** A private bitmap copy made immediately while the published live frame is current. */
private data class LiveSpeciesCrop(
    val resolved: ResolvedBattleCrop,
    val searchIndex: Int,
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
    @Volatile private var activeSearchWindowId = Long.MIN_VALUE
    @Volatile private var cropSearchIndex = 0

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastSubmittedSpecies = null
        lastRawReadings = emptyList()
        lastCaptureQueuedAtNanos = 0L
        captureOperating = null
        activeSearchWindowId = Long.MIN_VALUE
        cropSearchIndex = 0
        val sourceId = SourceId(observerId.id)
        val rawSourceId = SourceId("${observerId.id}_OCR")
        val cropContract = when (side) {
            ActivePokemonSide.PLAYER -> BattleWitnessContracts.playerActiveSpeciesTextCapture
            ActivePokemonSide.OPPONENT -> BattleWitnessContracts.opponentActiveSpeciesTextCapture
        }
        val cropSourceId = SourceId(cropContract.witnessId)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { pipelineScope ->
            pipelineScope.launch {
                match.articles.collect { article ->
                    val check = article.payload as? SpeciesCheckMeasured ?: return@collect
                    if (check.side == side && check.status == "OPENED") {
                        activeSearchWindowId = check.windowId
                        cropSearchIndex = 0
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
                    val enabled = captureAllowed() && match.speciesChecks.captureEnabled(side)
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
                    if (activeSearchWindowId != window.id) {
                        activeSearchWindowId = window.id
                        cropSearchIndex = 0
                    }
                    val searchIndex = cropSearchIndex
                    val calibratedRegion = cropContract.crop.region.regionIn(calibration)
                    val searchRegion = SpeciesCropSearchPlan.region(calibratedRegion, searchIndex)
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
                            searchIndex,
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
                        val crop = CropCaptured(artifact, sample.resolved.provenance)
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
                        // Custody has accepted the immutable raw crop before OCR
                        // sees it. Queue it directly from that acceptance rather
                        // than waiting for the general Match article consumer,
                        // which can be busy deriving combat evidence.
                        handedToOcr = crops.trySend(
                            PreservedSpeciesCrop(
                                crop = crop,
                                bitmap = sample.resolved.bitmap,
                                cropEvidenceId = cropArticle.id.value,
                                searchIndex = sample.searchIndex,
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
                val knownNames = match.pokemonKnowledge.getAllSpeciesNames()
                for (sample in crops) {
                    val readings = try {
                        SpeciesNameRecognizer.recognizeCandidates(sample.bitmap) { rawText ->
                            FastOverlaySpeciesResolver.resolveDetailed(rawText, knownNames) != null
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("FAST_OVERLAY_SPECIES", "OCR failed side=$side", error)
                        continue
                    } finally {
                        sample.bitmap.recycle()
                    }

                    val readingSignature = readings.map { "${it.treatment}:${it.text}" }
                    if (readingSignature != lastRawReadings) {
                        lastRawReadings = readingSignature
                        readings.forEach { reading ->
                            match.custody.submitTestimony(
                                sourceId = SourceId("${rawSourceId.id}_${reading.treatment}"),
                                payload = RawTestimony(reading.text),
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
                    if (resolvedReading == null) {
                        advanceCropSearch(sample.speciesCheckWindowId, sample.searchIndex)
                        continue
                    }
                    val (reading, resolution) = resolvedReading
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
                        resetCropSearch(sample.speciesCheckWindowId)
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
                    resetCropSearch(sample.speciesCheckWindowId)
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

    @Synchronized
    private fun advanceCropSearch(windowId: Long, completedIndex: Int) {
        if (activeSearchWindowId == windowId && cropSearchIndex == completedIndex) {
            cropSearchIndex = SpeciesCropSearchPlan.next(completedIndex)
        }
    }

    @Synchronized
    private fun resetCropSearch(windowId: Long) {
        if (activeSearchWindowId == windowId) cropSearchIndex = 0
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

/**
 * Keeps a species worker on its single assignment while recovering from a
 * calibrated strip that has drifted by one text row in the live capture.
 * Every attempted crop retains its real bounds in [BattleCropProvenance].
 */
internal object SpeciesCropSearchPlan {
    private val rowOffsets = intArrayOf(0, -1, 1, -2, 2)

    fun region(calibrated: AnchorRegion, searchIndex: Int): AnchorRegion {
        val rowOffset = rowOffsets[searchIndex.coerceIn(rowOffsets.indices)]
        return calibrated.copy(
            y = (calibrated.y + calibrated.height * rowOffset)
                .coerceIn(0f, 1f - calibrated.height)
        )
    }

    fun next(searchIndex: Int): Int = (searchIndex + 1) % rowOffsets.size
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
