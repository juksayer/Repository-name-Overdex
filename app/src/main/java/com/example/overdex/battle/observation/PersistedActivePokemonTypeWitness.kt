package com.example.overdex.battle.observation

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
    override val name: String
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
                    val bitmap = artifactStore.loadVerifiedPng(captured.artifact) ?: return@collect
                    try {
                        val matches = PokemonGoTypeIconMatcher.matchCrop(bitmap)
                        val types = matches.mapNotNull { it.type }
                        if (types.size !in 1..2 || types.distinct().size != types.size) return@collect
                        match.custody.submitTestimony(
                            sourceId, ActivePokemonTypesWitnessed(side, types, matches.minOf { it.similarity }),
                            article.perceivedAt, matches.minOf { it.similarity }, listOf(article.id.value),
                            article.monotonicTimeNanos ?: return@collect
                        )
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }

    companion object {
        fun player(store: FileCropArtifactStore) = PersistedActivePokemonTypeWitness(store, ActivePokemonSide.PLAYER, BattleCropContracts.playerActiveTypeIcons, ObserverId(BattleWitnessContracts.playerActiveTypeIcons.witnessId, ObservationSource.SCREEN_CAPTURE), "Player Active Type Icons Witness")
        fun opponent(store: FileCropArtifactStore) = PersistedActivePokemonTypeWitness(store, ActivePokemonSide.OPPONENT, BattleCropContracts.opponentActiveTypeIcons, ObserverId(BattleWitnessContracts.opponentActiveTypeIcons.witnessId, ObservationSource.SCREEN_CAPTURE), "Opponent Active Type Icons Witness")
    }
}
