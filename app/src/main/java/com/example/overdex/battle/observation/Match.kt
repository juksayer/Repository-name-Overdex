package com.example.overdex.battle.observation

import com.example.overdex.BattleMemory
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.TestimonyCustody
import android.util.Log
import com.example.overdex.battle.interpretation.BattleInterpreter
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.reality.RealityTimeline
import com.example.overdex.data.PokemonKnowledge
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
    val pokemonKnowledge: PokemonKnowledge,
    val battleMemory: BattleMemory = BattleMemory()
) {
    private val interpreter = BattleInterpreter(pokemonKnowledge)

    private val matchScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** The total number of frames processed during this Match. */
    var frameCount: Long = 0
        private set

    init {
        matchScope.launch {
            custody.testimonyFlow.collect { testimony ->
                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "Match received TestimonyRecord: type=${testimony.payload::class.simpleName}, sourceId=${testimony.sourceId.id}, confidence=${testimony.confidence}, sequence=${testimony.sequenceNumber}, refs=${testimony.evidenceReferences}")
                }
                if (testimony.payload is PokemonIdentified) {
                    val p = testimony.payload as PokemonIdentified
                    Log.d("SPECIES_SLICE", "Match received TestimonyRecord: type=${p::class.simpleName}, species=${p.species}, sourceId=${testimony.sourceId.id}, confidence=${testimony.confidence}, sequence=${testimony.sequenceNumber}, refs=${testimony.evidenceReferences}")
                }

                val article = RealityArticle(
                    id = ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = testimony.timestamp,
                    recordedAt = System.currentTimeMillis(),
                    sourceId = testimony.sourceId,
                    payload = testimony.payload,
                    confidence = testimony.confidence,
                    sequenceNumber = testimony.sequenceNumber,
                    evidenceReferences = testimony.evidenceReferences,
                    matchId = MatchId(matchId)
                )

                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${article.payload::class.simpleName}, sourceId=${article.sourceId.id}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }
                if (testimony.payload is PokemonIdentified) {
                    val p = article.payload as PokemonIdentified
                    Log.d("SPECIES_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${p::class.simpleName}, species=${p.species}, sourceId=${article.sourceId.id}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }

                realityTimeline.append(article)

                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "RealityTimeline append confirmed: articleId=${article.id.value}")
                }
                if (testimony.payload is PokemonIdentified) {
                    Log.d("SPECIES_SLICE", "RealityTimeline append confirmed: articleId=${article.id.value}")
                }

                battleMemory.timeline.record(article)

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
