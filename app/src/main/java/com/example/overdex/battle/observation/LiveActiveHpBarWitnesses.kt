package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.FastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.custody.FastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max

/** A transient, bitmap-free handoff from the live HP witness to derived witnesses. */
internal data class LiveActiveHpBarSample(
    val measurement: ActiveHpBarMeasurement,
    val appearance: ActiveHpBarBorderAppearance?,
    val recipientVisual: FastMoveRecipientVisualSample?,
    val capturedAtWallTimeMillis: Long,
    val capturedAtMonotonicTimeNanos: Long
)

/** A small color grid sampled below the tracked HP bar; it owns no Bitmap. */
internal data class FastMoveRecipientVisualSample(val pixels: IntArray)

internal class LiveActiveHpBarFrameHub {
    private val latest = AtomicReference<LiveActiveHpBarSample?>(null)
    private val _samples = MutableSharedFlow<LiveActiveHpBarSample>(
        replay = 0,
        // Border pulses can be shorter than one rendered frame. Keep enough
        // bitmap-free measurements for the detector coroutines to survive a
        // brief scheduling stall without silently losing several move uses.
        // At 30 fps this preserves about one second of bitmap-free history for
        // every independent detector. It costs only small measurements and
        // sampled color grids, never source-frame Bitmaps.
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val samples = _samples.asSharedFlow()

    fun publish(sample: LiveActiveHpBarSample) {
        latest.set(sample)
        _samples.tryEmit(sample)
    }

    fun latestSample(): LiveActiveHpBarSample? = latest.get()
}

/**
 * Measures the active bar directly from the captured frame stream.
 *
 * The live measurement path is deliberately separate from [CropCaptureWitness].
 * It can inspect every usable frame without turning each frame into a PNG. The
 * crop witness still preserves a sparse forensic sample, so the Timeline keeps
 * a durable source when a later investigation needs one.
 */
internal class LiveActiveHpBarWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val crop: BattleCropContract,
    private val side: ActivePokemonSide,
    private val isEnabled: () -> Boolean,
    private val frameHub: LiveActiveHpBarFrameHub,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSpeciesName: String? = null
    private val stateLock = Any()
    private val tracker = ActiveHpBarTracker()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastEnabled = null
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is ActivePokemonSpeciesWitnessed -> if (payload.side == side) {
                            synchronized(stateLock) {
                                // A fast single-frame identity and its later formal
                                // confirmation describe the same combatant. Do not
                                // throw away HP lock and cadence merely because the
                                // confirmation has a different article id.
                                if (payload.speciesName != lastSpeciesName) {
                                    tracker.resetForNextCombatant()
                                    lastSpeciesName = payload.speciesName
                                }
                            }
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.side == side && payload.status == "OPENED" && payload.reason == "EMPTY_HP"
                        ) {
                            synchronized(stateLock) {
                                tracker.resetForNextCombatant()
                                lastSpeciesName = null
                            }
                        }
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    lastEnabled = true
                    input.supplyFrames { frame ->
                        val enabled = isEnabled()
                        if (lastEnabled != enabled) {
                            match.custody.submitAvailability(sourceId, enabled, frame.capturedAtWallTimeMillis)
                            lastEnabled = enabled
                        }
                        if (!enabled) return@supplyFrames
                        val resolved = crop.resolve(calibration, frame.bitmap) ?: return@supplyFrames
                        try {
                            val measurement = synchronized(stateLock) {
                                tracker.measure(resolved.bitmap)
                            } ?: return@supplyFrames
                            val appearance = ActiveHpBarBorderAppearanceMeasurer.measure(
                                resolved.bitmap,
                                measurement
                            )
                            val recipientVisual = FastMoveRecipientVisualSampler.sample(
                                resolved.bitmap,
                                measurement
                            )
                            match.custody.submitTestimony(
                                sourceId = sourceId,
                                payload = ActiveHpBarMeasured(
                                    side = side,
                                    barLeft = measurement.left,
                                    barTop = measurement.top,
                                    barRight = measurement.right,
                                    barBottom = measurement.bottom,
                                    filledFraction = measurement.filledFraction
                                ),
                                timestamp = frame.capturedAtWallTimeMillis,
                                confidence = measurement.confidence,
                                evidenceReferences = emptyList(),
                                monotonicTimeNanos = frame.capturedAtMonotonicTimeNanos
                            )
                            frameHub.publish(
                                LiveActiveHpBarSample(
                                    measurement = measurement,
                                    appearance = appearance,
                                    recipientVisual = recipientVisual,
                                    capturedAtWallTimeMillis = frame.capturedAtWallTimeMillis,
                                    capturedAtMonotonicTimeNanos = frame.capturedAtMonotonicTimeNanos
                                )
                            )
                        } finally {
                            resolved.bitmap.recycle()
                        }
                    }
                } finally {
                    if (lastEnabled == true) {
                        match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                    }
                    lastEnabled = false
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) {
            activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        }
        activeMatch = null
        scope?.cancel("Live active HP bar witness stopped")
        scope = null
        synchronized(stateLock) {
            tracker.resetForNextCombatant()
            lastSpeciesName = null
        }
        lastEnabled = false
    }

    companion object {
        fun player(
            input: ObservationInput,
            calibration: BattleCalibration,
            isEnabled: () -> Boolean,
            frameHub: LiveActiveHpBarFrameHub
        ) =
            LiveActiveHpBarWitness(
                input = input,
                calibration = calibration,
                crop = BattleCropContracts.playerHpEvidence,
                side = ActivePokemonSide.PLAYER,
                isEnabled = isEnabled,
                frameHub = frameHub,
                observerId = ObserverId(BattleWitnessContracts.playerActiveHpBar.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = "Player Active HP Bar Witness"
            )

        fun opponent(
            input: ObservationInput,
            calibration: BattleCalibration,
            isEnabled: () -> Boolean,
            frameHub: LiveActiveHpBarFrameHub
        ) =
            LiveActiveHpBarWitness(
                input = input,
                calibration = calibration,
                crop = BattleCropContracts.opponentHpEvidence,
                side = ActivePokemonSide.OPPONENT,
                isEnabled = isEnabled,
                frameHub = frameHub,
                observerId = ObserverId(BattleWitnessContracts.opponentActiveHpBar.witnessId, ObserverSource.SCREEN_CAPTURE),
                name = "Opponent Active HP Bar Witness"
            )
    }
}

/** Measures border cadence from the live crop without retaining a PNG per frame. */
internal class LiveActiveHpBarBorderCadenceWitness(
    private val frameHub: LiveActiveHpBarFrameHub,
    private val damagedBarSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSpeciesName: String? = null
    private val stateLock = Any()
    private val detector = ActiveHpBarBorderPulseDetector()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastEnabled = null
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is ActivePokemonSpeciesWitnessed -> if (payload.side == damagedBarSide) {
                            synchronized(stateLock) {
                                if (payload.speciesName != lastSpeciesName) {
                                    resetForNextCombatant()
                                    lastSpeciesName = payload.speciesName
                                }
                            }
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.side == damagedBarSide && payload.status == "OPENED" && payload.reason == "EMPTY_HP"
                        ) {
                            synchronized(stateLock) {
                                resetForNextCombatant()
                                lastSpeciesName = null
                            }
                        }
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    lastEnabled = true
                    frameHub.samples.collect { sample ->
                        val appearance = sample.appearance ?: return@collect
                        val cadence = synchronized(stateLock) {
                            detector.accept(
                                "${observerId.id}:${sample.capturedAtMonotonicTimeNanos}",
                                sample.capturedAtMonotonicTimeNanos,
                                appearance
                            )
                        } ?: return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = ActiveHpBarBorderCadenceMeasured(
                                damagedBarSide = damagedBarSide,
                                intervalNanos = cadence.intervalNanos,
                                peakColorDistance = cadence.colorDistanceAtOnset,
                                sampleCount = cadence.baselineSampleCount,
                                peakOrangeFraction = cadence.orangeFractionAtOnset,
                                baselineWhiteFraction = cadence.whiteFractionBeforePulse
                            ),
                            timestamp = sample.capturedAtWallTimeMillis,
                            confidence = cadence.confidence,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                        )
                    }
                } finally {
                    if (lastEnabled == true) match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                    lastEnabled = false
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Live active HP border cadence witness stopped")
        scope = null
        synchronized(stateLock) { resetForNextCombatant(); lastSpeciesName = null }
        lastEnabled = false
    }

    private fun resetForNextCombatant() = detector.reset()

    companion object {
        fun player(frameHub: LiveActiveHpBarFrameHub) =
            LiveActiveHpBarBorderCadenceWitness(
                frameHub,
                ActivePokemonSide.PLAYER,
                ObserverId(BattleWitnessContracts.playerActiveHpBarBorderCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
                "Player Active HP Bar Border Cadence Witness"
            )

        fun opponent(frameHub: LiveActiveHpBarFrameHub) =
            LiveActiveHpBarBorderCadenceWitness(
                frameHub,
                ActivePokemonSide.OPPONENT,
                ObserverId(BattleWitnessContracts.opponentActiveHpBarBorderCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
                "Opponent Active HP Bar Border Cadence Witness"
            )
    }
}

/** Emits one border-pulse article for each live rising edge. */
internal class LiveActiveHpBarBorderPulseWitness(
    private val frameHub: LiveActiveHpBarFrameHub,
    private val damagedBarSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSpeciesName: String? = null
    private val stateLock = Any()
    private val detector = ActiveHpBarBorderPulseDetector()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastEnabled = null
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is ActivePokemonSpeciesWitnessed -> if (payload.side == damagedBarSide) {
                            synchronized(stateLock) {
                                if (payload.speciesName != lastSpeciesName) {
                                    resetForNextCombatant()
                                    lastSpeciesName = payload.speciesName
                                }
                            }
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.side == damagedBarSide && payload.status == "OPENED" && payload.reason == "EMPTY_HP"
                        ) {
                            synchronized(stateLock) {
                                resetForNextCombatant()
                                lastSpeciesName = null
                            }
                        }
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    lastEnabled = true
                    frameHub.samples.collect { sample ->
                        val appearance = sample.appearance ?: return@collect
                        val transition = synchronized(stateLock) {
                            detector.acceptTransition(
                                "${observerId.id}:${sample.capturedAtMonotonicTimeNanos}",
                                sample.capturedAtMonotonicTimeNanos,
                                appearance
                            )
                        } ?: return@collect
                        val pulse = transition.pulse
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = HpBarBorderPulse(
                                barSide = damagedBarSide,
                                status = transition.status,
                                peakColorDistance = pulse?.colorDistanceAtOnset,
                                sampleCount = pulse?.baselineSampleCount,
                                peakOrangeFraction = pulse?.orangeFractionAtOnset,
                                baselineWhiteFraction = pulse?.whiteFractionBeforePulse
                            ),
                            timestamp = sample.capturedAtWallTimeMillis,
                            confidence = pulse?.confidence ?: 1f,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                        )
                    }
                } finally {
                    if (lastEnabled == true) match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                    lastEnabled = false
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Live active HP border pulse witness stopped")
        scope = null
        synchronized(stateLock) { resetForNextCombatant(); lastSpeciesName = null }
        lastEnabled = false
    }

    private fun resetForNextCombatant() = detector.reset()

    companion object {
        fun player(frameHub: LiveActiveHpBarFrameHub) =
            LiveActiveHpBarBorderPulseWitness(
                frameHub,
                ActivePokemonSide.PLAYER,
                ObserverId(BattleWitnessContracts.playerActiveHpBarBorderPulse.witnessId, ObserverSource.SCREEN_CAPTURE),
                "Player Active HP Bar Border Pulse Witness"
            )

        fun opponent(frameHub: LiveActiveHpBarFrameHub) =
            LiveActiveHpBarBorderPulseWitness(
                frameHub,
                ActivePokemonSide.OPPONENT,
                ObserverId(BattleWitnessContracts.opponentActiveHpBarBorderPulse.witnessId, ObserverSource.SCREEN_CAPTURE),
                "Opponent Active HP Bar Border Pulse Witness"
            )
    }
}

internal data class ActiveHpBarDamageTickMeasurement(
    val beforeFraction: Float,
    val afterFraction: Float,
    val lostFraction: Float,
    val confidence: Float
)

/** Measures one persistent HP-fill decrease without naming its cause. */
internal class ActiveHpBarDamageTickDetector {
    private var baselineFraction: Float? = null
    private var pendingAfterFraction: Float? = null
    private var pendingChangedAtNanos: Long? = null
    private var pendingConfidence = 0f

    @Synchronized
    fun reset() {
        baselineFraction = null
        pendingAfterFraction = null
        pendingChangedAtNanos = null
        pendingConfidence = 0f
    }

    @Synchronized
    fun accept(
        filledFraction: Float,
        measurementConfidence: Float,
        monotonicTimeNanos: Long
    ): ActiveHpBarDamageTickMeasurement? {
        if (!filledFraction.isFinite() || filledFraction !in 0f..1f) return null
        val current = filledFraction
        val baseline = baselineFraction
        if (baseline == null) {
            baselineFraction = current
            return null
        }
        // The same active combatant cannot heal. An orange hit frame or a
        // tracking jump must not reset its baseline and manufacture another hit
        // when the real fill becomes visible again. Entry/species cues reset it.
        if (baseline - current < MIN_DAMAGE_FRACTION) {
            pendingAfterFraction = null
            pendingChangedAtNanos = null
            return null
        }

        val pending = pendingAfterFraction
        if (pending == null || kotlin.math.abs(current - pending) > UPDATE_EPSILON) {
            pendingAfterFraction = current
            pendingChangedAtNanos = monotonicTimeNanos
            pendingConfidence = measurementConfidence
            return null
        }
        val changedAt = pendingChangedAtNanos ?: return null
        if (monotonicTimeNanos - changedAt < SETTLE_NANOS) return null

        val after = minOf(pending, current)
        val lost = (baseline - after).coerceIn(0f, 1f)
        baselineFraction = after
        pendingAfterFraction = null
        pendingChangedAtNanos = null
        return ActiveHpBarDamageTickMeasurement(
            beforeFraction = baseline,
            afterFraction = after,
            lostFraction = lost,
            confidence = (pendingConfidence * (0.75f + lost.coerceAtMost(0.25f))).coerceIn(0f, 0.99f)
        )
    }

    private companion object {
        const val MIN_DAMAGE_FRACTION = 0.006f
        const val UPDATE_EPSILON = 0.003f
        const val SETTLE_NANOS = 90_000_000L
    }
}

/** Emits one article per settled active-HP loss. */
internal class LiveActiveHpBarDamageTickWitness(
    private val frameHub: LiveActiveHpBarFrameHub,
    private val damagedSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSpeciesName: String? = null
    private val stateLock = Any()
    private val detector = ActiveHpBarDamageTickDetector()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is ActivePokemonSpeciesWitnessed -> if (payload.side == damagedSide) {
                            synchronized(stateLock) {
                                if (payload.speciesName != lastSpeciesName) {
                                    detector.reset()
                                    lastSpeciesName = payload.speciesName
                                }
                            }
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.side == damagedSide && payload.status == "OPENED" &&
                            payload.reason in setOf("ENTRY", "EMPTY_HP")
                        ) {
                            synchronized(stateLock) {
                                detector.reset()
                                lastSpeciesName = null
                            }
                        }
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    lastEnabled = true
                    frameHub.samples.collect { sample ->
                        val tick = synchronized(stateLock) {
                            detector.accept(
                                sample.measurement.filledFraction,
                                sample.measurement.confidence,
                                sample.capturedAtMonotonicTimeNanos
                            )
                        } ?: return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = ActiveHpBarDamageTickMeasured(
                                damagedSide = damagedSide,
                                beforeFraction = tick.beforeFraction,
                                afterFraction = tick.afterFraction,
                                lostFraction = tick.lostFraction
                            ),
                            timestamp = sample.capturedAtWallTimeMillis,
                            confidence = tick.confidence,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = sample.capturedAtMonotonicTimeNanos
                        )
                    }
                } finally {
                    if (lastEnabled == true) match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                    lastEnabled = false
                }
            }
        }
    }

    override fun stop() {
        if (lastEnabled == true) activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Live active HP damage tick witness stopped")
        scope = null
        synchronized(stateLock) {
            detector.reset()
            lastSpeciesName = null
        }
        lastEnabled = false
    }

    companion object {
        fun player(frameHub: LiveActiveHpBarFrameHub) = LiveActiveHpBarDamageTickWitness(
            frameHub,
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerActiveHpBarDamageTick.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Active HP Bar Damage Tick Witness"
        )

        fun opponent(frameHub: LiveActiveHpBarFrameHub) = LiveActiveHpBarDamageTickWitness(
            frameHub,
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentActiveHpBarDamageTick.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Active HP Bar Damage Tick Witness"
        )
    }
}

/** Samples the visual territory surrounding the recipient without retaining a frame. */
internal object FastMoveRecipientVisualSampler {
    private const val GRID_SIZE = 24

    fun sample(bitmap: Bitmap, bar: ActiveHpBarMeasurement): FastMoveRecipientVisualSample? {
        val barWidth = (bar.right - bar.left).coerceAtLeast(1)
        val barHeight = (bar.bottom - bar.top).coerceAtLeast(1)
        val left = (bar.left - barWidth / 2).coerceAtLeast(0)
        val right = (bar.right + barWidth / 2).coerceAtMost(bitmap.width)
        val top = (bar.top - barWidth / 4).coerceAtLeast(0)
        val bottom = (bar.bottom + max(barWidth * 3 / 2, barHeight * 12)).coerceAtMost(bitmap.height)
        if (right <= left || bottom <= top) return null

        val pixels = IntArray(GRID_SIZE * GRID_SIZE)
        for (gridY in 0 until GRID_SIZE) {
            val y = top + ((gridY + 0.5f) * (bottom - top) / GRID_SIZE).toInt()
                .coerceIn(0, bitmap.height - 1)
            for (gridX in 0 until GRID_SIZE) {
                val x = left + ((gridX + 0.5f) * (right - left) / GRID_SIZE).toInt()
                    .coerceIn(0, bitmap.width - 1)
                pixels[gridY * GRID_SIZE + gridX] = bitmap.getPixel(x, y)
            }
        }
        return FastMoveRecipientVisualSample(pixels)
    }
}

internal data class FastMoveRecipientVisualArtifactMeasurement(
    val changedPixelFraction: Float,
    val meanColorDistance: Float,
    val meanRed: Float,
    val meanGreen: Float,
    val meanBlue: Float,
    val centroidX: Float,
    val centroidY: Float,
    val changedSampleCount: Int,
    val confidence: Float,
    val observedAtMonotonicTimeNanos: Long
)

/**
 * Detects the rising edge of a transient color change in the recipient area.
 * It measures the artifact without claiming a move name or elemental type.
 */
internal class FastMoveRecipientVisualArtifactDetector {
    private data class Peak(
        val measurement: FastMoveRecipientVisualArtifactMeasurement,
        val score: Float
    )

    private var baseline: IntArray? = null
    private var transientStartedAtNanos: Long? = null
    private var peak: Peak? = null
    private var lastEmissionNanos: Long? = null

    @Synchronized
    fun reset() {
        baseline = null
        transientStartedAtNanos = null
        peak = null
        lastEmissionNanos = null
    }

    @Synchronized
    fun accept(sample: FastMoveRecipientVisualSample, monotonicTimeNanos: Long): FastMoveRecipientVisualArtifactMeasurement? {
        val earlier = baseline
        if (earlier == null || earlier.size != sample.pixels.size) {
            baseline = sample.pixels.copyOf()
            transientStartedAtNanos = null
            peak = null
            return null
        }

        var changed = 0
        var distanceTotal = 0f
        var redTotal = 0f
        var greenTotal = 0f
        var blueTotal = 0f
        var xTotal = 0f
        var yTotal = 0f
        val gridSize = kotlin.math.sqrt(sample.pixels.size.toFloat()).toInt().coerceAtLeast(1)
        sample.pixels.indices.forEach { index ->
            val before = earlier[index]
            val after = sample.pixels[index]
            val red = after ushr 16 and 0xff
            val green = after ushr 8 and 0xff
            val blue = after and 0xff
            val colorDistance = (
                abs(red - (before ushr 16 and 0xff)) +
                    abs(green - (before ushr 8 and 0xff)) +
                    abs(blue - (before and 0xff))
                ) / (255f * 3f)
            if (colorDistance < CHANGED_COLOR_DISTANCE) return@forEach
            changed++
            distanceTotal += colorDistance
            redTotal += red / 255f
            greenTotal += green / 255f
            blueTotal += blue / 255f
            xTotal += (index % gridSize + 0.5f) / gridSize
            yTotal += (index / gridSize + 0.5f) / gridSize
        }
        val fraction = changed.toFloat() / sample.pixels.size
        val meanDistance = if (changed == 0) 0f else distanceTotal / changed
        val score = fraction * meanDistance
        val currentPeak = peak

        val receded = currentPeak != null && (
            fraction <= REARM_FRACTION ||
                meanDistance < MIN_MEAN_COLOR_DISTANCE ||
                score <= currentPeak.score * PEAK_RELEASE_RATIO
            )
        if (receded) {
            baseline = sample.pixels.copyOf()
            transientStartedAtNanos = null
            peak = null
            val observed = currentPeak.measurement
            if (lastEmissionNanos?.let {
                    observed.observedAtMonotonicTimeNanos - it < MIN_EMISSION_INTERVAL_NANOS
                } == true
            ) return null
            lastEmissionNanos = observed.observedAtMonotonicTimeNanos
            return observed
        }

        if (fraction >= ONSET_FRACTION && meanDistance >= MIN_MEAN_COLOR_DISTANCE) {
            if (transientStartedAtNanos == null) transientStartedAtNanos = monotonicTimeNanos
            val measured = FastMoveRecipientVisualArtifactMeasurement(
                changedPixelFraction = fraction,
                meanColorDistance = meanDistance,
                meanRed = redTotal / changed,
                meanGreen = greenTotal / changed,
                meanBlue = blueTotal / changed,
                centroidX = xTotal / changed,
                centroidY = yTotal / changed,
                changedSampleCount = changed,
                confidence = ((fraction / 0.30f) * 0.55f + (meanDistance / 0.45f) * 0.45f).coerceIn(0.20f, 1f),
                observedAtMonotonicTimeNanos = monotonicTimeNanos
            )
            if (currentPeak == null || score > currentPeak.score) peak = Peak(measured, score)
        } else if (currentPeak == null) {
            // A quiet recipient frame is a better baseline for the next transient.
            baseline = sample.pixels.copyOf()
        }

        if (transientStartedAtNanos?.let { monotonicTimeNanos - it >= MAX_TRANSIENT_NANOS } == true) {
            // A camera move or a changed combatant that never receded is a new
            // scene, not a stream of attack events. Absorb it without testimony.
            baseline = sample.pixels.copyOf()
            transientStartedAtNanos = null
            peak = null
        }
        return null
    }

    private companion object {
        const val CHANGED_COLOR_DISTANCE = 0.12f
        const val ONSET_FRACTION = 0.08f
        const val REARM_FRACTION = 0.035f
        const val MIN_MEAN_COLOR_DISTANCE = 0.16f
        const val PEAK_RELEASE_RATIO = 0.55f
        const val MIN_EMISSION_INTERVAL_NANOS = 300_000_000L
        const val MAX_TRANSIENT_NANOS = 1_500_000_000L
    }
}

/** Measures one signal: transient visual artifacts near the damaged Pokémon. */
internal class LiveFastMoveRecipientVisualArtifactWitness(
    private val frameHub: LiveActiveHpBarFrameHub,
    private val damagedSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    override val managesAvailability: Boolean = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastSpeciesName: String? = null
    private val detector = FastMoveRecipientVisualArtifactDetector()

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val species = article.payload as? ActivePokemonSpeciesWitnessed ?: return@collect
                    if (species.side == damagedSide && species.speciesName != lastSpeciesName) {
                        detector.reset()
                        lastSpeciesName = species.speciesName
                    }
                }
            }
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    frameHub.samples.collect { sample ->
                        val visual = sample.recipientVisual ?: return@collect
                        val measured = detector.accept(visual, sample.capturedAtMonotonicTimeNanos) ?: return@collect
                        val lagNanos = (sample.capturedAtMonotonicTimeNanos - measured.observedAtMonotonicTimeNanos)
                            .coerceAtLeast(0L)
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = FastMoveRecipientVisualArtifactMeasured(
                                damagedSide = damagedSide,
                                changedPixelFraction = measured.changedPixelFraction,
                                meanColorDistance = measured.meanColorDistance,
                                meanRed = measured.meanRed,
                                meanGreen = measured.meanGreen,
                                meanBlue = measured.meanBlue,
                                centroidX = measured.centroidX,
                                centroidY = measured.centroidY,
                                changedSampleCount = measured.changedSampleCount
                            ),
                            timestamp = sample.capturedAtWallTimeMillis - lagNanos / 1_000_000L,
                            confidence = measured.confidence,
                            evidenceReferences = emptyList(),
                            monotonicTimeNanos = measured.observedAtMonotonicTimeNanos
                        )
                    }
                } finally {
                    match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                }
            }
        }
    }

    override fun stop() {
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Fast move recipient visual artifact witness stopped")
        scope = null
        lastSpeciesName = null
        detector.reset()
    }

    companion object {
        fun player(frameHub: LiveActiveHpBarFrameHub) = LiveFastMoveRecipientVisualArtifactWitness(
            frameHub,
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerFastMoveRecipientVisualArtifact.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Fast Move Recipient Visual Artifact Witness"
        )

        fun opponent(frameHub: LiveActiveHpBarFrameHub) = LiveFastMoveRecipientVisualArtifactWitness(
            frameHub,
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentFastMoveRecipientVisualArtifact.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Fast Move Recipient Visual Artifact Witness"
        )
    }
}

/** Measures one signal: elapsed monotonic time between recipient artifacts. */
internal class LiveFastMoveRecipientVisualCadenceWitness(
    private val damagedSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    override val managesAvailability: Boolean = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var previousArticleId: String? = null
    private var previousMonotonicTimeNanos: Long? = null
    private var previousConfidence: Float? = null
    private var lastSpeciesName: String? = null

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        resetCadence()
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    match.articles.collect { article ->
                        when (val payload = article.payload) {
                            is ActivePokemonSpeciesWitnessed -> if (
                                payload.side == damagedSide && payload.speciesName != lastSpeciesName
                            ) {
                                resetCadence()
                                lastSpeciesName = payload.speciesName
                            }
                            is SpeciesCheckMeasured -> if (
                                payload.side == damagedSide &&
                                payload.status == "OPENED" &&
                                payload.reason in setOf("ENTRY", "EMPTY_HP")
                            ) {
                                resetCadence()
                                lastSpeciesName = null
                            }
                            is FastMoveRecipientVisualArtifactMeasured -> if (payload.damagedSide == damagedSide) {
                                val currentNanos = article.monotonicTimeNanos ?: return@collect
                                val priorNanos = previousMonotonicTimeNanos
                                val priorId = previousArticleId
                                val priorConfidence = previousConfidence
                                previousMonotonicTimeNanos = currentNanos
                                previousArticleId = article.id.value
                                previousConfidence = article.confidence
                                if (priorNanos == null || priorId == null || currentNanos <= priorNanos) return@collect
                                match.custody.submitTestimony(
                                    sourceId = sourceId,
                                    payload = FastMoveRecipientVisualCadenceMeasured(
                                        damagedSide = damagedSide,
                                        intervalNanos = currentNanos - priorNanos
                                    ),
                                    timestamp = article.perceivedAt,
                                    confidence = listOfNotNull(priorConfidence, article.confidence).minOrNull(),
                                    evidenceReferences = listOf(priorId, article.id.value),
                                    monotonicTimeNanos = currentNanos
                                )
                            }
                        }
                    }
                } finally {
                    match.custody.submitAvailability(sourceId, false, System.currentTimeMillis())
                }
            }
        }
    }

    override fun stop() {
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Fast move recipient visual cadence witness stopped")
        scope = null
        resetCadence()
        lastSpeciesName = null
    }

    private fun resetCadence() {
        previousArticleId = null
        previousMonotonicTimeNanos = null
        previousConfidence = null
    }

    companion object {
        fun player() = LiveFastMoveRecipientVisualCadenceWitness(
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerFastMoveRecipientVisualCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Fast Move Recipient Visual Cadence Witness"
        )

        fun opponent() = LiveFastMoveRecipientVisualCadenceWitness(
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentFastMoveRecipientVisualCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Fast Move Recipient Visual Cadence Witness"
        )
    }
}
