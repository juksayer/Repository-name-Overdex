package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActiveHpBarBorderCadenceMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ApertureStatus
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.max

/** Measures only the white-to-orange outline pulse around one damaged active HP bar. */
class PersistedActiveHpBarBorderCadenceWitness(
    private val artifactStore: FileCropArtifactStore,
    private val crop: BattleCropContract,
    private val damagedBarSide: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastSpeciesName: String? = null
    private val tracker = ActiveHpBarTracker()
    private val detector = ActiveHpBarBorderPulseDetector()
    private var lastGeometry: ActiveHpBarMeasurement? = null

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { witnessScope ->
            val crops = Channel<RealityArticle>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            witnessScope.launch {
                match.articles.collect { article ->
                    val species = article.payload as? ActivePokemonSpeciesWitnessed ?: return@collect
                    if (species.side == damagedBarSide && species.speciesName != lastSpeciesName) {
                        resetForNextCombatant()
                        lastSpeciesName = species.speciesName
                    }
                }
            }
            witnessScope.launch {
                match.activeHpCropArticles.collect { article ->
                    val captured = article.payload as? CropCaptured ?: return@collect
                    if (captured.cropProvenance.cropName == crop.cropName) crops.trySend(article)
                }
            }
            witnessScope.launch {
                match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                for (article in crops) {
                    val captured = article.payload as? CropCaptured ?: continue
                    val monotonic = article.monotonicTimeNanos ?: continue
                    val bitmap = artifactStore.loadVerifiedPng(captured.artifact, captured.cropProvenance) ?: continue
                    try {
                        // Keep sampling the last lock during the colored frame: a
                        // colored outline may intentionally fail the neutral-outline locator.
                        val geometry = tracker.measure(bitmap)?.also { lastGeometry = it } ?: lastGeometry ?: continue
                        val appearance = ActiveHpBarBorderAppearanceMeasurer.measure(bitmap, geometry) ?: continue
                        val cadence = detector.accept(article.id.value, monotonic, appearance) ?: continue
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
                            timestamp = article.perceivedAt,
                            confidence = cadence.confidence,
                            evidenceReferences = cadence.evidenceArticleIds,
                            monotonicTimeNanos = monotonic
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() {
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Active HP border cadence witness stopped")
        scope = null
        resetForNextCombatant()
    }

    private fun resetForNextCombatant() {
        tracker.resetForNextCombatant()
        detector.reset()
        lastGeometry = null
    }

    companion object {
        fun player(store: FileCropArtifactStore) = PersistedActiveHpBarBorderCadenceWitness(
            store,
            BattleCropContracts.playerHpEvidence,
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerActiveHpBarBorderCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Active HP Bar Border Cadence Witness"
        )

        fun opponent(store: FileCropArtifactStore) = PersistedActiveHpBarBorderCadenceWitness(
            store,
            BattleCropContracts.opponentHpEvidence,
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentActiveHpBarBorderCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Active HP Bar Border Cadence Witness"
        )
    }
}

internal data class ActiveHpBarBorderAppearance(
    val red: Float,
    val green: Float,
    val blue: Float,
    val brightness: Float,
    val orangeFraction: Float,
    val whiteFraction: Float
)

internal object ActiveHpBarBorderAppearanceMeasurer {
    fun measure(bitmap: Bitmap, bar: ActiveHpBarMeasurement): ActiveHpBarBorderAppearance? {
        val thickness = max(1, (bar.bottom - bar.top) / 12)
        var red = 0L
        var green = 0L
        var blue = 0L
        var orange = 0
        var white = 0
        var count = 0
        fun sample(x: Int, y: Int) {
            if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return
            val color = bitmap.getPixel(x, y)
            red += color ushr 16 and 0xff
            green += color ushr 8 and 0xff
            blue += color and 0xff
            if (isOrangeBorder(color)) orange++
            if (isWhiteBorder(color)) white++
            count++
        }
        for (x in bar.left until bar.right) {
            for (offset in 0..thickness) {
                sample(x, bar.top + offset)
                sample(x, bar.bottom - offset)
            }
        }
        for (y in bar.top until bar.bottom) {
            for (offset in 0..thickness) {
                sample(bar.left + offset, y)
                sample(bar.right - 1 - offset, y)
            }
        }
        if (count == 0) return null
        val r = red.toFloat() / count / 255f
        val g = green.toFloat() / count / 255f
        val b = blue.toFloat() / count / 255f
        return ActiveHpBarBorderAppearance(
            red = r,
            green = g,
            blue = b,
            brightness = maxOf(r, g, b),
            orangeFraction = orange.toFloat() / count,
            whiteFraction = white.toFloat() / count
        )
    }

    private fun isOrangeBorder(color: Int): Boolean {
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        return red >= 155 && green >= 65 && green <= 210 && blue <= 135 &&
            red - green >= 22 && green - blue >= 12
    }

    private fun isWhiteBorder(color: Int): Boolean {
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        return minOf(red, green, blue) >= 165 && maxOf(red, green, blue) - minOf(red, green, blue) <= 55
    }
}

internal data class ActiveHpBarBorderCadenceMeasurement(
    val intervalNanos: Long,
    val colorDistanceAtOnset: Float,
    val baselineSampleCount: Int,
    val confidence: Float,
    val evidenceArticleIds: List<String>,
    val orangeFractionAtOnset: Float,
    val whiteFractionBeforePulse: Float
)

internal data class ActiveHpBarBorderPulseMeasurement(
    val articleId: String,
    val monotonicTimeNanos: Long,
    val colorDistanceAtOnset: Float,
    val baselineSampleCount: Int,
    val confidence: Float,
    val previousPulseNanos: Long?,
    val previousPulseArticleId: String?,
    val orangeFractionAtOnset: Float,
    val whiteFractionBeforePulse: Float
)

internal data class ActiveHpBarBorderPulseTransition(
    val status: ApertureStatus,
    val pulse: ActiveHpBarBorderPulseMeasurement? = null
)

/** Adaptive rising-edge detector for the border flash surrounding a received hit. */
internal class ActiveHpBarBorderPulseDetector {
    private var baseline: ActiveHpBarBorderAppearance? = null
    private var baselineSamples = 0
    private var pulsing = false
    private var previousPulseNanos: Long? = null
    private var previousPulseArticleId: String? = null

    @Synchronized
    fun reset() {
        baseline = null
        baselineSamples = 0
        pulsing = false
        previousPulseNanos = null
        previousPulseArticleId = null
    }

    @Synchronized
    fun accept(
        articleId: String,
        monotonicTimeNanos: Long,
        appearance: ActiveHpBarBorderAppearance
    ): ActiveHpBarBorderCadenceMeasurement? {
        val pulse = acceptPulse(articleId, monotonicTimeNanos, appearance) ?: return null
        val previousNanos = pulse.previousPulseNanos ?: return null
        val previousArticle = pulse.previousPulseArticleId ?: return null
        val interval = monotonicTimeNanos - previousNanos
        if (interval !in MIN_CADENCE_NANOS..MAX_CADENCE_NANOS) return null
        return ActiveHpBarBorderCadenceMeasurement(
            intervalNanos = interval,
            colorDistanceAtOnset = pulse.colorDistanceAtOnset,
            baselineSampleCount = pulse.baselineSampleCount,
            confidence = pulse.confidence,
            evidenceArticleIds = listOf(previousArticle, articleId),
            orangeFractionAtOnset = pulse.orangeFractionAtOnset,
            whiteFractionBeforePulse = pulse.whiteFractionBeforePulse
        )
    }

    /** Reports only state changes: initial ABSENT, rising PRESENT, and falling ABSENT. */
    @Synchronized
    fun acceptTransition(
        articleId: String,
        monotonicTimeNanos: Long,
        appearance: ActiveHpBarBorderAppearance
    ): ActiveHpBarBorderPulseTransition? {
        val hadBaseline = baseline != null
        val wasPulsing = pulsing
        val pulse = acceptPulse(articleId, monotonicTimeNanos, appearance)
        return when {
            pulse != null -> ActiveHpBarBorderPulseTransition(ApertureStatus.PRESENT, pulse)
            wasPulsing && !pulsing -> ActiveHpBarBorderPulseTransition(ApertureStatus.ABSENT)
            !hadBaseline && baseline != null -> ActiveHpBarBorderPulseTransition(ApertureStatus.ABSENT)
            else -> null
        }
    }

    /** Returns every rising edge, including the first edge with no interval yet. */
    @Synchronized
    fun acceptPulse(
        articleId: String,
        monotonicTimeNanos: Long,
        appearance: ActiveHpBarBorderAppearance
    ): ActiveHpBarBorderPulseMeasurement? {
        val currentBaseline = baseline
        if (currentBaseline == null) {
            if (appearance.whiteFraction >= MIN_WHITE_BASELINE_FRACTION) {
                baseline = appearance
                baselineSamples = 1
            }
            return null
        }
        val signal = (appearance.orangeFraction - currentBaseline.orangeFraction).coerceAtLeast(0f)

        if (pulsing) {
            if (appearance.orangeFraction <= RETURN_ORANGE_FRACTION ||
                (appearance.whiteFraction >= MIN_WHITE_BASELINE_FRACTION &&
                    appearance.orangeFraction < MIN_ORANGE_FRACTION)
            ) {
                pulsing = false
                updateBaseline(appearance)
            }
            return null
        }

        if (baselineSamples >= REQUIRED_BASELINE_SAMPLES &&
            appearance.orangeFraction >= MIN_ORANGE_FRACTION &&
            signal >= ONSET_ORANGE_DELTA
        ) {
            pulsing = true
            val previousNanos = previousPulseNanos
            val previousArticle = previousPulseArticleId
            previousPulseNanos = monotonicTimeNanos
            previousPulseArticleId = articleId
            return ActiveHpBarBorderPulseMeasurement(
                articleId = articleId,
                monotonicTimeNanos = monotonicTimeNanos,
                colorDistanceAtOnset = signal,
                baselineSampleCount = baselineSamples,
                confidence = (0.55f + appearance.orangeFraction * 0.35f +
                    currentBaseline.whiteFraction * 0.10f).coerceIn(0f, 1f),
                previousPulseNanos = previousNanos,
                previousPulseArticleId = previousArticle,
                orangeFractionAtOnset = appearance.orangeFraction,
                whiteFractionBeforePulse = currentBaseline.whiteFraction
            )
        }

        updateBaseline(appearance)
        return null
    }

    private fun updateBaseline(appearance: ActiveHpBarBorderAppearance) {
        val old = baseline ?: appearance
        baseline = ActiveHpBarBorderAppearance(
            red = old.red * 0.9f + appearance.red * 0.1f,
            green = old.green * 0.9f + appearance.green * 0.1f,
            blue = old.blue * 0.9f + appearance.blue * 0.1f,
            brightness = old.brightness * 0.9f + appearance.brightness * 0.1f,
            orangeFraction = old.orangeFraction * 0.9f + appearance.orangeFraction * 0.1f,
            whiteFraction = old.whiteFraction * 0.9f + appearance.whiteFraction * 0.1f
        )
        baselineSamples++
    }

    private companion object {
        const val REQUIRED_BASELINE_SAMPLES = 3
        const val MIN_WHITE_BASELINE_FRACTION = 0.12f
        const val MIN_ORANGE_FRACTION = 0.08f
        const val ONSET_ORANGE_DELTA = 0.055f
        const val RETURN_ORANGE_FRACTION = 0.035f
        const val MIN_CADENCE_NANOS = 350_000_000L
        const val MAX_CADENCE_NANOS = 3_250_000_000L
    }
}
