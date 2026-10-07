package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Color
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillCadenceMeasured
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillIncreased
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Watches only the two player charged-move controls and reports a confirmed
 * upward fill step. It does not decide which Fast Move caused that energy.
 */
internal class LivePlayerChargeMoveEnergyFillWitness(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    private val isEnabled: () -> Boolean,
    override val observerId: ObserverId = ObserverId(
        BattleWitnessContracts.playerChargeMoveEnergyFill.witnessId,
        ObserverSource.SCREEN_CAPTURE
    ),
    override val name: String = "Player Charge Move Energy Fill Witness"
) : Observer {
    override val managesAvailability: Boolean = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastEnabled: Boolean? = null
    private var lastSpeciesName: String? = null
    private val stateLock = Any()
    private val detector = PlayerChargeMoveEnergyFillDetector()

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastEnabled = null
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    if (article.payload is MatchStarted) {
                        // Countdown shading is deliberately retained in the
                        // Timeline. The first live frame after GO becomes the
                        // new comparison baseline for actual energy gain.
                        synchronized(stateLock) { detector.reset() }
                        return@collect
                    }
                    val species = article.payload as? ActivePokemonSpeciesWitnessed ?: return@collect
                    if (species.side == ActivePokemonSide.PLAYER && species.speciesName != lastSpeciesName) {
                        synchronized(stateLock) { detector.reset() }
                        lastSpeciesName = species.speciesName
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
                            if (!enabled) synchronized(stateLock) { detector.reset() }
                        }
                        if (!enabled) return@supplyFrames
                        val crop = BattleCropContracts.playerChargeMoveControls
                            .resolve(calibration, frame.bitmap) ?: return@supplyFrames
                        try {
                            val levels = PlayerChargeMoveEnergyFillSampler.measure(crop.bitmap)
                            val measurement = synchronized(stateLock) {
                                detector.accept(levels, frame.capturedAtMonotonicTimeNanos)
                            } ?: return@supplyFrames
                            val lagNanos = (frame.capturedAtMonotonicTimeNanos - measurement.observedAtNanos)
                                .coerceAtLeast(0L)
                            match.custody.submitTestimony(
                                sourceId = sourceId,
                                payload = PlayerChargeMoveEnergyFillIncreased(
                                    beforeBySlot = measurement.beforeBySlot,
                                    afterBySlot = measurement.afterBySlot,
                                    changedSlots = measurement.changedSlots
                                ),
                                timestamp = frame.capturedAtWallTimeMillis - lagNanos / 1_000_000L,
                                confidence = measurement.confidence,
                                evidenceReferences = emptyList(),
                                monotonicTimeNanos = measurement.observedAtNanos
                            )
                        } finally {
                            crop.bitmap.recycle()
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
        scope?.cancel("Player charge-move energy fill witness stopped")
        scope = null
        synchronized(stateLock) { detector.reset() }
        lastSpeciesName = null
        lastEnabled = false
    }
}

/** Measures the horizontal fill boundary inside each of the two circular controls. */
internal object PlayerChargeMoveEnergyFillSampler {
    fun measure(bitmap: Bitmap): List<Float> = listOf(
        measureSlot(bitmap, 0.235f),
        measureSlot(bitmap, 0.735f)
    )

    private fun measureSlot(bitmap: Bitmap, centerXFraction: Float): Float {
        val radius = min(bitmap.width / 2f, bitmap.height.toFloat()) * 0.24f
        if (radius < 4f) return 0f
        val centerX = bitmap.width * centerXFraction
        val centerY = bitmap.height * 0.39f
        val startY = ceil(centerY - radius * 0.75f).toInt().coerceIn(0, bitmap.height - 1)
        val endY = floor(centerY + radius * 0.75f).toInt().coerceIn(startY, bitmap.height - 1)
        val halfStripe = max(2, (radius * 0.20f).toInt())
        val startX = (centerX.toInt() - halfStripe).coerceIn(0, bitmap.width - 1)
        val endX = (centerX.toInt() + halfStripe).coerceIn(startX, bitmap.width - 1)
        val rowColors = (startY..endY).map { y ->
            var red = 0f
            var green = 0f
            var blue = 0f
            var count = 0
            var x = startX
            while (x <= endX) {
                val color = bitmap.getPixel(x, y)
                red += Color.red(color) / 255f
                green += Color.green(color) / 255f
                blue += Color.blue(color) / 255f
                count++
                x += 2
            }
            floatArrayOf(red / count, green / count, blue / count)
        }
        if (rowColors.size < 3) return 0f
        val differences = List(rowColors.lastIndex) { index ->
            val first = rowColors[index]
            val second = rowColors[index + 1]
            sqrt(
                (second[0] - first[0]) * (second[0] - first[0]) +
                    (second[1] - first[1]) * (second[1] - first[1]) +
                    (second[2] - first[2]) * (second[2] - first[2])
            )
        }
        val smoothed = differences.indices.map { index ->
            val from = max(0, index - 1)
            val to = min(differences.lastIndex, index + 1)
            differences.subList(from, to + 1).average().toFloat()
        }
        val boundary = smoothed.indices.maxByOrNull { smoothed[it] } ?: return 0f
        if (smoothed[boundary] < MIN_BOUNDARY_COLOR_DISTANCE) return 0f
        return (1f - (boundary + 1f) / rowColors.size).coerceIn(0f, 1f)
    }

    private const val MIN_BOUNDARY_COLOR_DISTANCE = 0.045f
}

internal data class PlayerChargeMoveEnergyFillMeasurement(
    val beforeBySlot: List<Float>,
    val afterBySlot: List<Float>,
    val changedSlots: List<Int>,
    val observedAtNanos: Long,
    val confidence: Float
)

/** Requires a fill step to persist into the following frame before reporting it. */
internal class PlayerChargeMoveEnergyFillDetector {
    private data class Candidate(
        val before: List<Float>,
        val after: List<Float>,
        val changedSlots: List<Int>,
        val observedAtNanos: Long
    )

    private var stable: List<Float>? = null
    private var candidate: Candidate? = null
    private var lastEmissionNanos: Long? = null

    @Synchronized
    fun reset() {
        stable = null
        candidate = null
        lastEmissionNanos = null
    }

    @Synchronized
    fun accept(levels: List<Float>, monotonicTimeNanos: Long): PlayerChargeMoveEnergyFillMeasurement? {
        require(levels.size == 2 && levels.all { it in 0f..1f })
        val baseline = stable
        if (baseline == null) {
            stable = levels
            return null
        }

        val pending = candidate
        if (pending != null) {
            val confirmed = pending.changedSlots.all { abs(levels[it] - pending.after[it]) <= CONFIRMATION_TOLERANCE }
            candidate = null
            if (confirmed) {
                stable = levels
                val last = lastEmissionNanos
                if (last == null || pending.observedAtNanos - last >= MIN_EVENT_SPACING_NANOS) {
                    lastEmissionNanos = pending.observedAtNanos
                    val maximumDelta = pending.changedSlots.maxOf { pending.after[it] - pending.before[it] }
                    return PlayerChargeMoveEnergyFillMeasurement(
                        pending.before,
                        pending.after,
                        pending.changedSlots,
                        pending.observedAtNanos,
                        (0.68f + maximumDelta.coerceAtMost(0.25f)).coerceAtMost(0.9f)
                    )
                }
            }
        }

        val currentBaseline = stable ?: levels
        val deltas = levels.indices.map { levels[it] - currentBaseline[it] }
        if (deltas.any { it < -RESET_DROP }) {
            stable = levels
            candidate = null
            return null
        }
        if (deltas.any { it > MAX_PLAUSIBLE_STEP }) {
            // A battle-screen transition, shield prompt, or charged-move QTE is
            // not an energy increment. Re-anchor without creating testimony.
            stable = levels
            candidate = null
            return null
        }
        val changed = deltas.indices.filter { deltas[it] >= MIN_FILL_STEP }
        if (changed.isNotEmpty()) {
            candidate = Candidate(currentBaseline, levels, changed, monotonicTimeNanos)
            return null
        }
        stable = levels.indices.map { index -> currentBaseline[index] * 0.9f + levels[index] * 0.1f }
        return null
    }

    private companion object {
        const val MIN_FILL_STEP = 0.045f
        const val MAX_PLAUSIBLE_STEP = 0.42f
        const val RESET_DROP = 0.04f
        const val CONFIRMATION_TOLERANCE = 0.035f
        const val MIN_EVENT_SPACING_NANOS = 300_000_000L
    }
}

/** Measures only the interval between confirmed player energy-fill steps. */
internal class PersistedPlayerChargeMoveEnergyFillCadenceWitness(
    override val observerId: ObserverId = ObserverId(
        BattleWitnessContracts.playerChargeMoveEnergyFillCadence.witnessId,
        ObserverSource.SCREEN_CAPTURE
    ),
    override val name: String = "Player Charge Move Energy Fill Cadence Witness"
) : Observer {
    override val managesAvailability: Boolean = true
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var previousArticleId: String? = null
    private var previousNanos: Long? = null

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        reset()
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                try {
                    match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                    match.articles.collect { article ->
                        val species = article.payload as? ActivePokemonSpeciesWitnessed
                        if (species?.side == ActivePokemonSide.PLAYER) {
                            reset()
                            return@collect
                        }
                        val fill = article.payload as? PlayerChargeMoveEnergyFillIncreased ?: return@collect
                        val now = article.monotonicTimeNanos ?: return@collect
                        val beforeNanos = previousNanos
                        val beforeId = previousArticleId
                        previousNanos = now
                        previousArticleId = article.id.value
                        if (beforeNanos == null || beforeId == null || now <= beforeNanos) return@collect
                        val interval = now - beforeNanos
                        if (interval !in MIN_INTERVAL_NANOS..MAX_INTERVAL_NANOS) return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = PlayerChargeMoveEnergyFillCadenceMeasured(interval, fill.changedSlots),
                            timestamp = article.perceivedAt,
                            confidence = article.confidence,
                            evidenceReferences = listOf(beforeId, article.id.value),
                            monotonicTimeNanos = now
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
        scope?.cancel("Player charge-move energy cadence witness stopped")
        scope = null
        reset()
    }

    private fun reset() {
        previousArticleId = null
        previousNanos = null
    }

    private companion object {
        const val MIN_INTERVAL_NANOS = 350_000_000L
        const val MAX_INTERVAL_NANOS = 3_250_000_000L
    }
}
