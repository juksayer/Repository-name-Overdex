package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.HpBarBorderPulse
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
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

/** Reports state changes from the colored-border-pulse aperture around one active HP bar. */
class PersistedActiveHpBarBorderPulseWitness(
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
                        // colored outline may fail the neutral-outline locator.
                        val geometry = tracker.measure(bitmap)?.also { lastGeometry = it }
                            ?: lastGeometry ?: continue
                        val appearance = ActiveHpBarBorderAppearanceMeasurer.measure(bitmap, geometry) ?: continue
                        val transition = detector.acceptTransition(article.id.value, monotonic, appearance) ?: continue
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
                            timestamp = article.perceivedAt,
                            confidence = pulse?.confidence ?: 1f,
                            evidenceReferences = listOf(article.id.value),
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
        scope?.cancel("Active HP border pulse witness stopped")
        scope = null
        resetForNextCombatant()
    }

    private fun resetForNextCombatant() {
        tracker.resetForNextCombatant()
        detector.reset()
        lastGeometry = null
    }

    companion object {
        fun player(store: FileCropArtifactStore) = PersistedActiveHpBarBorderPulseWitness(
            store,
            BattleCropContracts.playerHpEvidence,
            ActivePokemonSide.PLAYER,
            ObserverId(BattleWitnessContracts.playerActiveHpBarBorderPulse.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Player Active HP Bar Border Pulse Witness"
        )

        fun opponent(store: FileCropArtifactStore) = PersistedActiveHpBarBorderPulseWitness(
            store,
            BattleCropContracts.opponentHpEvidence,
            ActivePokemonSide.OPPONENT,
            ObserverId(BattleWitnessContracts.opponentActiveHpBarBorderPulse.witnessId, ObserverSource.SCREEN_CAPTURE),
            "Opponent Active HP Bar Border Pulse Witness"
        )
    }
}
