package com.example.overdex.battle.observation

import android.util.Log
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonTypesWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Reads one side's active Pokémon GO type badges only from verified crop artifacts. */
class PersistedActivePokemonTypeWitness(
    private val artifactStore: FileCropArtifactStore,
    private val side: ActivePokemonSide,
    private val crop: BattleCropContract,
    override val observerId: ObserverId,
    override val name: String,
    private val needsReading: (Long) -> Boolean = { true },
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val captured = article.payload as? CropCaptured ?: return@collect
                    if (captured.cropProvenance.cropName != crop.cropName) return@collect
                    val capturedAt = article.monotonicTimeNanos ?: return@collect
                    if (!needsReading(capturedAt)) return@collect
                    val bitmap = artifactStore.loadVerifiedPng(captured.artifact, captured.cropProvenance) ?: return@collect
                    try {
                        if (!needsReading(capturedAt)) return@collect
                        val matches = runCatching { PokemonGoTypeIconMatcher.matchCrop(bitmap) }
                            .getOrElse { error ->
                                // One malformed or undersized artifact means this
                                // witness had no usable reading. It must not cancel
                                // the observer scope or crash the field session.
                                Log.w(
                                    "TYPE_ICON",
                                    "Unable to inspect ${captured.cropProvenance.cropName} " +
                                        "(${bitmap.width}x${bitmap.height})",
                                    error
                                )
                                emptyList()
                            }
                        val reading = ActiveTypeIconReading.from(matches) ?: return@collect
                        match.custody.submitTestimony(
                            sourceId, ActivePokemonTypesWitnessed(side, reading.types, reading.similarity),
                            article.perceivedAt, reading.similarity, listOf(article.id.value),
                            capturedAt
                        )
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }

    companion object {
        fun player(store: FileCropArtifactStore, needsReading: (Long) -> Boolean = { true }) = PersistedActivePokemonTypeWitness(store, ActivePokemonSide.PLAYER, BattleCropContracts.playerActiveTypeIcons, ObserverId(BattleWitnessContracts.playerActiveTypeIcons.witnessId, ObservationSource.SCREEN_CAPTURE), "Player Active Type Icons", needsReading)
        fun opponent(store: FileCropArtifactStore, needsReading: (Long) -> Boolean = { true }) = PersistedActivePokemonTypeWitness(store, ActivePokemonSide.OPPONENT, BattleCropContracts.opponentActiveTypeIcons, ObserverId(BattleWitnessContracts.opponentActiveTypeIcons.witnessId, ObservationSource.SCREEN_CAPTURE), "Opponent Active Type Icons", needsReading)
    }
}
