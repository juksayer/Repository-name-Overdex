package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
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

private data class LiveHpEffectivenessCrop(
    val resolved: ResolvedBattleCrop,
    val pulseArticleId: String,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long,
)

/**
 * Reads the brief effectiveness phrase from one HP territory after its border
 * reports a hit. Only a positively recognized source crop is retained, so this
 * does not turn every live HP frame into another PNG.
 */
internal class LiveHpEffectivenessTextWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val artifactStore: FileCropArtifactStore,
    private val crop: BattleCropContract,
    private val damagedSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String,
) : Observer {
    override val managesAvailability: Boolean = true

    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var sampleChannel: Channel<LiveHpEffectivenessCrop>? = null
    private val scanFromNanos = AtomicLong(Long.MAX_VALUE)
    private val scanUntilNanos = AtomicLong(Long.MIN_VALUE)
    private val currentPulseId = AtomicReference<String?>(null)
    private val lastQueuedNanos = AtomicLong(Long.MIN_VALUE)
    private val emittedPulseId = AtomicReference<String?>(null)

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
                    val pulse = article.payload as? HpBarBorderPulse ?: return@collect
                    if (pulse.barSide != damagedSide || pulse.status != ApertureStatus.PRESENT) return@collect
                    val at = article.monotonicTimeNanos ?: return@collect
                    currentPulseId.set(article.id.value)
                    scanFromNanos.set(at)
                    scanUntilNanos.set(at + SCAN_WINDOW_NANOS)
                    lastQueuedNanos.set(Long.MIN_VALUE)
                }
            }
            witnessScope.launch(Dispatchers.Default) {
                match.custody.submitAvailability(textSourceId, true, System.currentTimeMillis())
                input.supplyFrames { frame ->
                    val capturedAt = frame.capturedAtMonotonicTimeNanos
                    if (capturedAt < scanFromNanos.get() || capturedAt > scanUntilNanos.get()) {
                        return@supplyFrames
                    }
                    val prior = lastQueuedNanos.get()
                    if (prior != Long.MIN_VALUE && capturedAt - prior < SAMPLE_INTERVAL_NANOS) {
                        return@supplyFrames
                    }
                    val pulseId = currentPulseId.get() ?: return@supplyFrames
                    val resolved = crop.resolve(calibration, frame.bitmap) ?: return@supplyFrames
                    lastQueuedNanos.set(capturedAt)
                    val result = samples.trySend(
                        LiveHpEffectivenessCrop(
                            resolved = resolved,
                            pulseArticleId = pulseId,
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
                        if (emittedPulseId.get() == sample.pulseArticleId) continue
                        val text = AnnouncementRecognizer.recognize(sample.resolved.bitmap).value
                            ?.takeIf(String::isNotBlank) ?: continue
                        val reading = FastMoveEffectivenessTextResolver.resolve(text) ?: continue
                        val artifact = artifactStore.preservePng(sample.resolved.bitmap) ?: continue
                        val cropAccepted = match.custody.submitTestimony(
                            sourceId = captureSourceId,
                            payload = CropCaptured(artifact, sample.resolved.provenance),
                            timestamp = sample.capturedAtWallTimeMillis,
                            confidence = null,
                            evidenceReferences = listOf(sample.pulseArticleId),
                            monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos,
                        )
                        emittedPulseId.set(sample.pulseArticleId)
                        if (currentPulseId.get() == sample.pulseArticleId) {
                            scanUntilNanos.set(Long.MIN_VALUE)
                        }
                        match.custody.submitTestimony(
                            sourceId = textSourceId,
                            payload = RawTestimony(text),
                            timestamp = sample.capturedAtWallTimeMillis,
                            confidence = reading.confidence,
                            evidenceReferences = listOf(
                                sample.pulseArticleId,
                                "custody:${cropAccepted.sequenceNumber}",
                            ),
                            monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos,
                        )
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
        scanFromNanos.set(Long.MAX_VALUE)
        scanUntilNanos.set(Long.MIN_VALUE)
        currentPulseId.set(null)
        lastQueuedNanos.set(Long.MIN_VALUE)
        emittedPulseId.set(null)
    }

    companion object {
        private const val SCAN_WINDOW_NANOS = 450_000_000L
        private const val SAMPLE_INTERVAL_NANOS = 100_000_000L

        fun player(
            input: ObservationInput,
            calibration: BattleCalibration,
            artifactStore: FileCropArtifactStore,
        ) = LiveHpEffectivenessTextWitness(
            input,
            calibration,
            artifactStore,
            BattleCropContracts.playerHpEvidence,
            ActivePokemonSide.PLAYER,
            ObserverId(FastMoveEffectivenessSources.PLAYER_TEXT, ObserverSource.SCREEN_CAPTURE),
            "Player HP Effectiveness Text Witness",
        )

        fun opponent(
            input: ObservationInput,
            calibration: BattleCalibration,
            artifactStore: FileCropArtifactStore,
        ) = LiveHpEffectivenessTextWitness(
            input,
            calibration,
            artifactStore,
            BattleCropContracts.opponentHpEvidence,
            ActivePokemonSide.OPPONENT,
            ObserverId(FastMoveEffectivenessSources.OPPONENT_TEXT, ObserverSource.SCREEN_CAPTURE),
            "Opponent HP Effectiveness Text Witness",
        )
    }
}
