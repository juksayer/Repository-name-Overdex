package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.FastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private data class LiveHpEffectivenessCrop(
    val resolved: ResolvedBattleCrop,
    val cueArticleId: String?,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long,
)

/**
 * Reads the effectiveness phrase immediately above one tracked HP bar. The
 * text strip is sampled independently while combat is live; damage cues only
 * increase its sampling rate. Only a positively recognized source crop is
 * retained, so routine OCR does not turn every frame into another PNG.
 */
internal class LiveHpEffectivenessTextWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val artifactStore: FileCropArtifactStore,
    private val crop: BattleCropContract,
    private val damagedSide: ActivePokemonSide,
    private val isEnabled: () -> Boolean,
    private val frameHub: LiveActiveHpBarFrameHub,
    override val observerId: ObserverId,
    override val name: String,
) : Observer {
    override val managesAvailability: Boolean = true

    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var sampleChannel: Channel<LiveHpEffectivenessCrop>? = null
    private val burstUntilNanos = AtomicLong(Long.MIN_VALUE)
    private val currentCueId = AtomicReference<String?>(null)
    private val lastQueuedNanos = AtomicLong(Long.MIN_VALUE)
    private val lastEmittedAtByReading = ConcurrentHashMap<String, Long>()

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        reset()
        val textSourceId = SourceId(
            if (damagedSide == ActivePokemonSide.PLAYER) {
                FastMoveEffectivenessSources.PLAYER_TEXT
            } else {
                FastMoveEffectivenessSources.OPPONENT_TEXT
            }
        )
        val captureSourceId = SourceId(
            if (damagedSide == ActivePokemonSide.PLAYER) {
                FastMoveEffectivenessSources.PLAYER_CAPTURE
            } else {
                FastMoveEffectivenessSources.OPPONENT_CAPTURE
            }
        )
        val samples = Channel<LiveHpEffectivenessCrop>(
            capacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { it.resolved.bitmap.recycle() },
        )
        sampleChannel = samples

        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val cueMatchesSide = when (val payload = article.payload) {
                        is HpBarBorderPulse ->
                            payload.barSide == damagedSide && payload.status == ApertureStatus.PRESENT
                        is ActiveHpBarDamageTickMeasured -> payload.damagedSide == damagedSide
                        is FastMoveRecipientVisualArtifactMeasured -> payload.damagedSide == damagedSide
                        is FastMoveUseObserved -> payload.damagedSide == damagedSide
                        else -> false
                    }
                    if (!cueMatchesSide) return@collect
                    val at = maxOf(article.monotonicTimeNanos ?: return@collect, System.nanoTime())
                    currentCueId.set(article.id.value)
                    burstUntilNanos.set(at + BURST_WINDOW_NANOS)
                }
            }
            witnessScope.launch(Dispatchers.Default) {
                match.custody.submitAvailability(textSourceId, true, System.currentTimeMillis())
                input.supplyFrames { frame ->
                    if (!isEnabled()) return@supplyFrames
                    val capturedAt = frame.capturedAtMonotonicTimeNanos
                    val interval = if (capturedAt <= burstUntilNanos.get()) {
                        BURST_SAMPLE_INTERVAL_NANOS
                    } else {
                        CONTINUOUS_SAMPLE_INTERVAL_NANOS
                    }
                    val prior = lastQueuedNanos.get()
                    if (prior != Long.MIN_VALUE && capturedAt - prior < interval) {
                        return@supplyFrames
                    }
                    val barSample = frameHub.latestSample() ?: return@supplyFrames
                    if (abs(capturedAt - barSample.capturedAtMonotonicTimeNanos) > MAX_BAR_LOCK_AGE_NANOS) {
                        return@supplyFrames
                    }
                    val hpRegion = crop.resolve(calibration, frame.bitmap) ?: return@supplyFrames
                    val resolved = try {
                        HpEffectivenessTextCropper.crop(
                            hpRegion,
                            barSample.measurement,
                            cropName = if (damagedSide == ActivePokemonSide.PLAYER) {
                                "PlayerHpEffectivenessTextCrop"
                            } else {
                                "OpponentHpEffectivenessTextCrop"
                            },
                        )
                    } finally {
                        hpRegion.bitmap.recycle()
                    } ?: return@supplyFrames
                    lastQueuedNanos.set(capturedAt)
                    val result = samples.trySend(
                        LiveHpEffectivenessCrop(
                            resolved = resolved,
                            cueArticleId = currentCueId.get()
                                ?.takeIf { capturedAt <= burstUntilNanos.get() },
                            capturedAtWallTimeMillis = frame.capturedAtWallTimeMillis,
                            capturedAtMonotonicTimeNanos = capturedAt,
                        )
                    )
                    if (result.isFailure) resolved.bitmap.recycle()
                }
            }
            witnessScope.launch(Dispatchers.IO) {
                for (sample in samples) {
                    try {
                        val directText = SharedLatinTextRecognizer.readText(sample.resolved.bitmap)
                        val directReading = FastMoveEffectivenessTextResolver.resolve(directText)
                        val candidates = if (directReading != null || sample.cueArticleId == null) {
                            listOf(directText).map(String::trim).filter(String::isNotBlank)
                        } else {
                            // The second OCR pass is valuable around a witnessed
                            // hit, but running it continuously on two HP bars
                            // starves the shared OCR lane and makes live evidence
                            // arrive seconds after a switch.
                            AnnouncementRecognizer.recognizeCandidates(
                                sample.resolved.bitmap,
                                directText = directText,
                            )
                        }
                        for (text in candidates) {
                            val reading = FastMoveEffectivenessTextResolver.resolve(text)
                            val readingKey = reading?.effectiveness?.name ?: "RAW:$text"
                            val lastReadingAt = lastEmittedAtByReading[readingKey]
                            if (lastReadingAt != null &&
                                sample.capturedAtMonotonicTimeNanos - lastReadingAt < REPEAT_SUPPRESSION_NANOS
                            ) continue

                            val cueReferences = listOfNotNull(sample.cueArticleId)
                            var rawReferences = cueReferences
                            if (reading != null) {
                                val artifact = artifactStore.preservePng(sample.resolved.bitmap)
                                if (artifact != null) {
                                    val cropAccepted = match.custody.submitTestimony(
                                        sourceId = captureSourceId,
                                        payload = CropCaptured(artifact, sample.resolved.provenance),
                                        timestamp = sample.capturedAtWallTimeMillis,
                                        confidence = null,
                                        evidenceReferences = cueReferences,
                                        monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos,
                                    )
                                    rawReferences = cueReferences + "custody:${cropAccepted.sequenceNumber}"
                                }
                            }
                            lastEmittedAtByReading[readingKey] = sample.capturedAtMonotonicTimeNanos
                            match.custody.submitTestimony(
                                sourceId = textSourceId,
                                payload = RawTestimony(text),
                                timestamp = sample.capturedAtWallTimeMillis,
                                confidence = reading?.confidence,
                                evidenceReferences = rawReferences,
                                monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos,
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("HP_EFFECTIVENESS_TEXT", "Recognition failed for $damagedSide", error)
                    } finally {
                        sample.resolved.bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() {
        activeMatch?.custody?.submitAvailability(
            SourceId(observerId.id),
            false,
            System.currentTimeMillis(),
        )
        activeMatch = null
        scope?.cancel("Live HP effectiveness text witness stopped")
        scope = null
        sampleChannel?.cancel()
        sampleChannel = null
        reset()
    }

    private fun reset() {
        burstUntilNanos.set(Long.MIN_VALUE)
        currentCueId.set(null)
        lastQueuedNanos.set(Long.MIN_VALUE)
        lastEmittedAtByReading.clear()
    }

    companion object {
        private const val BURST_WINDOW_NANOS = 600_000_000L
        private const val BURST_SAMPLE_INTERVAL_NANOS = 100_000_000L
        private const val CONTINUOUS_SAMPLE_INTERVAL_NANOS = 200_000_000L
        private const val MAX_BAR_LOCK_AGE_NANOS = 500_000_000L
        private const val REPEAT_SUPPRESSION_NANOS = 650_000_000L

        fun player(
            input: ObservationInput,
            calibration: BattleCalibration,
            artifactStore: FileCropArtifactStore,
            isEnabled: () -> Boolean,
            frameHub: LiveActiveHpBarFrameHub,
        ) = LiveHpEffectivenessTextWitness(
            input,
            calibration,
            artifactStore,
            BattleCropContracts.playerHpEvidence,
            ActivePokemonSide.PLAYER,
            isEnabled,
            frameHub,
            ObserverId(FastMoveEffectivenessSources.PLAYER_TEXT, ObserverSource.SCREEN_CAPTURE),
            "Player HP Effectiveness Text Witness",
        )

        fun opponent(
            input: ObservationInput,
            calibration: BattleCalibration,
            artifactStore: FileCropArtifactStore,
            isEnabled: () -> Boolean,
            frameHub: LiveActiveHpBarFrameHub,
        ) = LiveHpEffectivenessTextWitness(
            input,
            calibration,
            artifactStore,
            BattleCropContracts.opponentHpEvidence,
            ActivePokemonSide.OPPONENT,
            isEnabled,
            frameHub,
            ObserverId(FastMoveEffectivenessSources.OPPONENT_TEXT, ObserverSource.SCREEN_CAPTURE),
            "Opponent HP Effectiveness Text Witness",
        )
    }
}

/** Builds the moving OCR strip from the HP bar position already tracked live. */
internal object HpEffectivenessTextCropper {
    fun crop(
        hpRegion: ResolvedBattleCrop,
        measurement: ActiveHpBarMeasurement,
        cropName: String,
    ): ResolvedBattleCrop? {
        val bounds = resolveBounds(hpRegion.bitmap.width, hpRegion.bitmap.height, measurement) ?: return null
        return try {
            val parent = hpRegion.provenance.bounds
            ResolvedBattleCrop(
                provenance = BattleCropProvenance(
                    cropName = cropName,
                    sourceWidth = hpRegion.provenance.sourceWidth,
                    sourceHeight = hpRegion.provenance.sourceHeight,
                    bounds = BattleCropBounds(
                        left = parent.left + bounds.left,
                        top = parent.top + bounds.top,
                        right = parent.left + bounds.right,
                        bottom = parent.top + bounds.bottom,
                    ),
                ),
                bitmap = Bitmap.createBitmap(
                    hpRegion.bitmap,
                    bounds.left,
                    bounds.top,
                    bounds.right - bounds.left,
                    bounds.bottom - bounds.top,
                ),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    internal fun resolveBounds(
        cropWidth: Int,
        cropHeight: Int,
        measurement: ActiveHpBarMeasurement,
    ): BattleCropBounds? {
        if (cropWidth <= 0 || cropHeight <= 0 || measurement.width <= 0) return null
        val barHeight = (measurement.bottom - measurement.top).coerceAtLeast(1)
        val horizontalPadding = max(12, (measurement.width * 0.35f).roundToInt())
        val textHeight = max(32, barHeight * 4)
        val left = (measurement.left - horizontalPadding).coerceAtLeast(0)
        val right = (measurement.right + horizontalPadding).coerceAtMost(cropWidth)
        val bottom = (measurement.top + max(2, barHeight / 4)).coerceIn(0, cropHeight)
        val top = (bottom - textHeight).coerceAtLeast(0)
        return BattleCropBounds(left, top, right, bottom).takeIf {
            it.right - it.left >= 32 && it.bottom - it.top >= 16
        }
    }
}
