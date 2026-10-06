package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Derives an opponent faint from the opponent team's visible Poké Ball count.
 *
 * Pokémon GO removes a depleted HP bar with its combatant, so an empty-bar
 * confirmation would usually ask for a frame that cannot exist. A stable drop
 * in the opponent Poké Ball count is the durable faint observation. The active
 * opponent immediately before that drop is the Pokémon that fainted. A recent
 * battle-cry candidate is cited when available, while the following species
 * identity naturally confirms which replacement entered the field.
 */
class OpponentPokemonFaintWitness(
    override val observerId: ObserverId = ObserverId(
        "OPPONENT_POKEMON_FAINT_WITNESS",
        ObservationSource.SCREEN_CAPTURE
    ),
    override val name: String = "Opponent Pokémon Faint Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        val tracker = OpponentFaintTracker()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val faint = tracker.accept(article) ?: return@collect
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = ActivePokemonFaintedWitnessed(
                            side = ActivePokemonSide.OPPONENT,
                            speciesName = faint.speciesName,
                            speciesId = faint.speciesId,
                            basis = faint.basis
                        ),
                        timestamp = article.perceivedAt,
                        confidence = if (faint.cryCorroborated) 0.99f else 0.96f,
                        evidenceReferences = faint.evidenceReferences,
                        monotonicTimeNanos = article.monotonicTimeNanos ?: System.nanoTime()
                    )
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Opponent faint witness stopped")
        scope = null
    }
}

internal data class OpponentFaintDecision(
    val speciesName: String?,
    val speciesId: Int?,
    val evidenceReferences: List<String>,
    val cryCorroborated: Boolean,
    val basis: String
)

/** Deterministic state machine kept separate so resource transitions are unit-testable. */
internal class OpponentFaintTracker {
    private data class ActiveOpponent(val name: String, val id: Int?, val appearanceArticleId: String)
    private data class BallCount(val count: Int, val articleId: String)
    private data class CryCandidate(val articleId: String, val atNanos: Long)

    private var activeOpponent: ActiveOpponent? = null
    private var opponentBeforeLatestEntry: ActiveOpponent? = null
    private var previousBallCount: BallCount? = null
    private var recentCry: CryCandidate? = null
    private var lastFaintedAppearanceId: String? = null

    fun accept(article: RealityArticle): OpponentFaintDecision? {
        when (val payload = article.payload) {
            is ActivePokemonSpeciesWitnessed -> if (payload.side == ActivePokemonSide.OPPONENT) {
                val current = activeOpponent
                if (current == null || !current.name.equals(payload.speciesName, ignoreCase = true)) {
                    opponentBeforeLatestEntry = current?.takeUnless {
                        it.appearanceArticleId == lastFaintedAppearanceId
                    }
                    activeOpponent = ActiveOpponent(payload.speciesName, payload.speciesId, article.id.value)
                    lastFaintedAppearanceId = null
                } else if (current.id == null && payload.speciesId != null) {
                    activeOpponent = current.copy(id = payload.speciesId)
                }
            }
            is BattleCryCandidatesMeasured -> if (payload.candidates.isNotEmpty()) {
                recentCry = CryCandidate(
                    articleId = article.id.value,
                    atNanos = article.monotonicTimeNanos ?: Long.MIN_VALUE
                )
            }
            is OpponentBattleResourceCountMeasured -> if (payload.resource == OpponentBattleResource.POKE_BALLS) {
                val earlier = previousBallCount
                val current = BallCount(payload.visibleCount, article.id.value)
                previousBallCount = current
                if (earlier == null) return null
                if (current.count >= earlier.count) {
                    // Two agreeing resource crops have now shown that the latest
                    // species change preserved the team count: it was a switch.
                    opponentBeforeLatestEntry = null
                    return null
                }

                // OCR for the replacement can occasionally arrive while the
                // two Poké Ball crops are still being processed. In that order,
                // the Pokémon active before the replacement is the one that
                // belongs to the observed count decrease.
                val active = opponentBeforeLatestEntry ?: activeOpponent
                opponentBeforeLatestEntry = null
                if (active != null && active.appearanceArticleId == lastFaintedAppearanceId) return null
                lastFaintedAppearanceId = active?.appearanceArticleId

                val now = article.monotonicTimeNanos ?: Long.MAX_VALUE
                val cry = recentCry?.takeIf { candidate ->
                    candidate.atNanos != Long.MIN_VALUE &&
                        now >= candidate.atNanos && now - candidate.atNanos <= CRY_CORROBORATION_WINDOW_NANOS
                }
                return OpponentFaintDecision(
                    speciesName = active?.name,
                    speciesId = active?.id,
                    evidenceReferences = listOfNotNull(
                        active?.appearanceArticleId,
                        earlier.articleId,
                        current.articleId,
                        cry?.articleId
                    ).distinct(),
                    cryCorroborated = cry != null,
                    basis = if (cry != null) {
                        "OPPONENT_POKE_BALL_COUNT_DECREASE_WITH_BATTLE_CRY"
                    } else {
                        "OPPONENT_POKE_BALL_COUNT_DECREASE"
                    }
                )
            }
        }
        return null
    }

    private companion object {
        const val CRY_CORROBORATION_WINDOW_NANOS = 5_000_000_000L
    }
}
