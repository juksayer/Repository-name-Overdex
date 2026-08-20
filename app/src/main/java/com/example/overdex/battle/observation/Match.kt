package com.example.overdex.battle.observation

import com.example.overdex.BattleMemory
import com.example.overdex.battle.custody.TestimonyCustody
import com.example.overdex.battle.interpretation.BattleInterpreter
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.reality.RealityTimeline
import com.example.overdex.data.PokemonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Represents one live Pokémon GO battle.
 * 
 * The Match manages the lifecycle of observations and owns a [BattleWorkspace]
 * where evidence is collected. Once the match is complete, its observations are
 * typically reconciled into the Battle Timeline.
 * 
 * @property matchId A unique identifier for the battle.
 * @property state The current lifecycle phase of the match.
 * @property workspace The mutable storage area where incoming observations are collected.
 * @property custody The briefcase for preserving accepted testimony.
 * @property realityTimeline The foundational ledger for preserving the objective history.
 */
class Match(
    @Suppress("unused") val matchId: String,
    var state: MatchState = MatchState.CREATED,
    val workspace: BattleWorkspace = BattleWorkspace(),
    val custody: TestimonyCustody,
    val realityTimeline: RealityTimeline,
    val pokemonRepository: PokemonRepository,
    val battleMemory: BattleMemory = BattleMemory()
) {
    private val interpreter = BattleInterpreter(pokemonRepository)

    private val matchScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** The total number of frames processed during this Match. */
    var frameCount: Long = 0
        private set

    init {
        matchScope.launch {
            custody.testimonyFlow.collect { testimony ->
                val article = RealityArticle(
                    id = ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = testimony.timestamp,
                    recordedAt = System.currentTimeMillis(),
                    sourceId = testimony.sourceId,
                    payload = testimony.payload
                )
                realityTimeline.append(article)

                interpreter.interpret(article)?.let { event ->
                    battleMemory.recordEvent(event)
                }
            }
        }
    }


    /**
     * Submits a transient observation to the match workspace.
     */
    fun submit(observation: Observation) {
        workspace.add(observation)
    }

    /**
     * Increments the frame count.
     */
    fun incrementFrameCount() {
        frameCount++
    }

    /**
     * Releases resources and cancels active subscriptions.
     */
    fun release() {
        matchScope.cancel("Match released")
    }
}
