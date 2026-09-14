package com.example.overdex.battle.observation

import com.example.overdex.BattleMemory
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SourceAvailabilityRecord
import com.example.overdex.battle.custody.TestimonyCustody
import com.example.overdex.battle.custody.WitnessOperating
import android.util.Log
import com.example.overdex.battle.interpretation.BattleInterpreter
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.reality.RealityTimeline
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.BattleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** Owned by this match and baselined by the enclosing Droidball session at GO. */
    val clock = MatchClock()

    private val _matchStarted = MutableSharedFlow<RealityArticle>(replay = 1, extraBufferCapacity = 1)
    /** The derived GO boundary for the owning Droidball session to act upon. */
    val matchStarted = _matchStarted.asSharedFlow()

    private val _articles = MutableSharedFlow<RealityArticle>(replay = 64, extraBufferCapacity = 64)
    /** Accepted Timeline articles for downstream recognizers; the Timeline remains authoritative. */
    val articles = _articles.asSharedFlow()

    private val matchScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** The total number of frames processed during this Match. */
    var frameCount: Long = 0
        private set

    init {
        matchScope.launch {
            custody.custodyRecordFlow.collect { record ->
                val availability = record as? SourceAvailabilityRecord ?: return@collect
                val article = RealityArticle(
                    id = ArticleId(UUID.randomUUID().toString()),
                    perceivedAt = availability.timestamp,
                    recordedAt = System.currentTimeMillis(),
                    sourceId = availability.sourceId,
                    payload = WitnessOperating(availability.available),
                    sequenceNumber = availability.sequenceNumber,
                    matchId = MatchId(matchId),
                    monotonicTimeNanos = availability.monotonicTimeNanos
                )
                realityTimeline.append(article)
                _articles.tryEmit(article)
                battleMemory.timeline.record(article)
            }
        }
        matchScope.launch {
            var matchStartRecorded = false
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
                    matchId = MatchId(matchId),
                    monotonicTimeNanos = testimony.monotonicTimeNanos
                )

                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${article.payload::class.simpleName}, sourceId=${article.sourceId.id}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }
                if (testimony.payload is PokemonIdentified) {
                    val p = article.payload as PokemonIdentified
                    Log.d("SPECIES_SLICE", "Match created RealityArticle: articleId=${article.id.value}, matchId=${article.matchId?.value}, type=${p::class.simpleName}, species=${p.species}, sourceId=${article.sourceId.id}, confidence=${article.confidence}, sequence=${article.sequenceNumber}, refs=${article.evidenceReferences}")
                }

                realityTimeline.append(article)
                _articles.tryEmit(article)

                if (testimony.payload is AttackIncoming) {
                    Log.d("ATTACK_SLICE", "RealityTimeline append confirmed: articleId=${article.id.value}")
                }
                if (testimony.payload is PokemonIdentified) {
                    Log.d("SPECIES_SLICE", "RealityTimeline append confirmed: articleId=${article.id.value}")
                }

                battleMemory.timeline.record(article)

                if (!matchStartRecorded) {
                    interpreter.interpretMatchStart(article)?.let { derivedArticle ->
                        realityTimeline.append(derivedArticle)
                        battleMemory.timeline.record(derivedArticle)
                        matchStartRecorded = true
                        _matchStarted.tryEmit(derivedArticle)
                        Log.d("MATCH_START", "Derived MatchStarted article appended: articleId=${derivedArticle.id.value}, predecessor=${article.id.value}")
                    }
                }

                interpreter.interpretAttackIncoming(article)?.let { derivedArticle ->
                    realityTimeline.append(derivedArticle)
                    battleMemory.timeline.record(derivedArticle)
                    Log.d("ATTACK_SLICE", "Derived AttackIncoming article appended: articleId=${derivedArticle.id.value}, predecessor=${derivedArticle.predecessorIds.firstOrNull()?.value}")
                }

                (article.payload as? CountdownGlyphWitnessed)?.let { glyph ->
                    DroidballService.emitSignal(DroidballSignal.CountdownWitnessed(glyph.glyph))
                    Log.d("COUNTDOWN_SLICE", "Countdown glyph article received: articleId=${article.id.value}, value=${glyph.glyph}")
                }

                interpreter.interpret(article)?.let { event ->
                    battleMemory.recordEvent(event)
                    if (event.type == BattleEventType.BATTLE_ENDED && event.result != null) {
                        val derivedArticle = RealityArticle(
                            id = ArticleId(UUID.randomUUID().toString()),
                            perceivedAt = article.perceivedAt,
                            recordedAt = System.currentTimeMillis(),
                            sourceId = SourceId("BATTLE_INTERPRETER"),
                            payload = MatchEnded(event.result),
                            predecessorIds = listOf(article.id),
                            matchId = MatchId(matchId),
                            monotonicTimeNanos = article.monotonicTimeNanos
                        )
                        realityTimeline.append(derivedArticle)
                        _articles.tryEmit(derivedArticle)
                        battleMemory.timeline.record(derivedArticle)
                    }
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
