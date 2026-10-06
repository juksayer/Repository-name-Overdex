package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Converts the already-preserved position history of one active HP bar into a
 * single signal: the interval between completed vertical excursions.
 */
class PersistedActiveHpBarCadenceWitness(
    private val side: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastSpeciesName: String? = null
    private val detector = ActiveHpBarCadenceDetector()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                match.articles.collect { article ->
                    val species = article.payload as? ActivePokemonSpeciesWitnessed
                    if (species?.side == side) {
                        if (species.speciesName != lastSpeciesName) detector.reset()
                        lastSpeciesName = species.speciesName
                        return@collect
                    }
                    val hp = article.payload as? ActiveHpBarMeasured ?: return@collect
                    if (hp.side != side) return@collect
                    val monotonic = article.monotonicTimeNanos ?: return@collect
                    val cadence = detector.accept(article, hp, monotonic) ?: return@collect
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = ActiveHpBarMotionCadenceMeasured(
                            movingSide = side,
                            intervalNanos = cadence.intervalNanos,
                            verticalExcursionPixels = cadence.verticalExcursionPixels,
                            sampleCount = cadence.sampleCount
                        ),
                        timestamp = article.perceivedAt,
                        confidence = cadence.confidence,
                        evidenceReferences = cadence.evidenceArticleIds,
                        monotonicTimeNanos = monotonic
                    )
                }
            }
        }
    }

    override fun stop() {
        activeMatch?.custody?.submitAvailability(SourceId(observerId.id), false, System.currentTimeMillis())
        activeMatch = null
        scope?.cancel("Active HP cadence witness stopped")
        scope = null
        detector.reset()
    }

    companion object {
        fun player() = PersistedActiveHpBarCadenceWitness(
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerActiveHpBarCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Active HP Bar Cadence Witness"
        )

        fun opponent() = PersistedActiveHpBarCadenceWitness(
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentActiveHpBarCadence.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Active HP Bar Cadence Witness"
        )
    }
}

internal data class ActiveHpBarCadenceMeasurement(
    val intervalNanos: Long,
    val verticalExcursionPixels: Float,
    val sampleCount: Int,
    val confidence: Float,
    val evidenceArticleIds: List<String>
)

/**
 * Requires a departure and a return before accepting an excursion. Repeated
 * partial animation while the bar remains away from rest stays one excursion.
 */
internal class ActiveHpBarCadenceDetector {
    private var baselineY: Float? = null
    private var baselineHeight: Float = 0f
    private var excursion: Excursion? = null
    private var previousCompletionNanos: Long? = null

    private data class Excursion(
        val startedNanos: Long,
        val originArticleId: String,
        var maximumDistance: Float,
        var sampleCount: Int,
        var strongestConfidence: Float
    )

    @Synchronized
    fun reset() {
        baselineY = null
        baselineHeight = 0f
        excursion = null
        previousCompletionNanos = null
    }

    @Synchronized
    fun accept(
        article: RealityArticle,
        hp: ActiveHpBarMeasured,
        monotonicTimeNanos: Long
    ): ActiveHpBarCadenceMeasurement? {
        val centerY = (hp.barTop + hp.barBottom) / 2f
        val barHeight = (hp.barBottom - hp.barTop).toFloat()
        val baseline = baselineY
        if (baseline == null) {
            baselineY = centerY
            baselineHeight = barHeight
            return null
        }

        // The tracked bar itself can be 20-30 px tall while a legitimate small
        // Pokémon hop moves it only 5-8 px. Requiring most of a bar-height hid
        // those moves entirely; the return requirement still rejects one-way
        // tracker drift and incomplete animation stutters.
        val departureThreshold = max(MIN_DEPARTURE_PIXELS, max(baselineHeight, barHeight) * 0.22f)
        val returnThreshold = max(MIN_RETURN_PIXELS, departureThreshold * 0.40f)
        val distance = abs(centerY - baseline)
        val active = excursion

        if (active == null) {
            if (distance >= departureThreshold) {
                excursion = Excursion(
                    startedNanos = monotonicTimeNanos,
                    originArticleId = article.id.value,
                    maximumDistance = distance,
                    sampleCount = 1,
                    strongestConfidence = article.confidence ?: 0.5f
                )
            } else {
                // Slowly follow harmless layout drift without following a jump.
                baselineY = baseline * 0.9f + centerY * 0.1f
                baselineHeight = baselineHeight * 0.9f + barHeight * 0.1f
            }
            return null
        }

        active.sampleCount += 1
        active.maximumDistance = max(active.maximumDistance, distance)
        active.strongestConfidence = max(active.strongestConfidence, article.confidence ?: 0.5f)

        if (monotonicTimeNanos - active.startedNanos > ABANDONED_EXCURSION_NANOS) {
            // A camera/layout relocation is not a fast move. Re-anchor without testimony.
            baselineY = centerY
            baselineHeight = barHeight
            excursion = null
            previousCompletionNanos = null
            return null
        }
        if (distance > returnThreshold) return null

        excursion = null
        baselineY = centerY
        baselineHeight = barHeight
        val previous = previousCompletionNanos
        previousCompletionNanos = monotonicTimeNanos
        if (previous == null) return null
        val interval = monotonicTimeNanos - previous
        if (interval !in MIN_CADENCE_NANOS..MAX_CADENCE_NANOS) return null

        return ActiveHpBarCadenceMeasurement(
            intervalNanos = interval,
            verticalExcursionPixels = active.maximumDistance,
            sampleCount = active.sampleCount + 1,
            confidence = active.strongestConfidence,
            evidenceArticleIds = listOf(active.originArticleId, article.id.value)
        )
    }

    private companion object {
        const val MIN_DEPARTURE_PIXELS = 4f
        const val MIN_RETURN_PIXELS = 2f
        const val MIN_CADENCE_NANOS = 350_000_000L
        const val MAX_CADENCE_NANOS = 3_250_000_000L
        const val ABANDONED_EXCURSION_NANOS = 3_500_000_000L
    }
}
