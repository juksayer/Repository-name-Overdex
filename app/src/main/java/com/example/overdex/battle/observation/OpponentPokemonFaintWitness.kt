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
 * Derives one side's faint from its own visible Poké Ball count.
 *
 * Pokémon GO removes a depleted HP bar with its combatant, so an empty-bar
 * confirmation would usually ask for a frame that cannot exist. A stable drop
 * in the side's Poké Ball count is the durable faint observation. The active
 * combatant immediately before that drop is the Pokémon that fainted. A recent
 * battle-cry candidate is cited when available, while the following species
 * identity naturally confirms which replacement entered the field.
 */
class PokemonFaintWitness(
    private val side: ActivePokemonSide = ActivePokemonSide.OPPONENT,
    override val observerId: ObserverId = ObserverId(
        "${side.name}_POKEMON_FAINT_WITNESS",
        ObservationSource.SCREEN_CAPTURE
    ),
    override val name: String = "${side.name} Pokémon Faint"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        val tracker = TeamFaintTracker(side)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val faint = tracker.accept(article) ?: return@collect
                    match.custody.submitTestimony(
                        sourceId = sourceId,
                        payload = ActivePokemonFaintedWitnessed(
                            side = side,
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
        scope?.cancel("${side.name} faint witness stopped")
        scope = null
    }
}

internal data class TeamFaintDecision(
    val speciesName: String?,
    val speciesId: Int?,
    val evidenceReferences: List<String>,
    val cryCorroborated: Boolean,
    val basis: String
)

/** Deterministic state machine kept separate so resource transitions are unit-testable. */
internal class TeamFaintTracker(private val side: ActivePokemonSide = ActivePokemonSide.OPPONENT) {
    private data class ActiveCombatant(val name: String, val id: Int?, val appearanceArticleId: String, val atNanos: Long)
    private data class BallCount(val count: Int, val articleId: String, val atNanos: Long)
    private data class CryCandidate(val articleId: String, val atNanos: Long)

    private var activeCombatant: ActiveCombatant? = null
    private var combatantBeforeLatestEntry: ActiveCombatant? = null
    private var previousBallCount: BallCount? = null
    private var recentCry: CryCandidate? = null
    private var lastFaintedAppearanceId: String? = null

    fun accept(article: RealityArticle): TeamFaintDecision? {
        when (val payload = article.payload) {
            is ActivePokemonSpeciesWitnessed -> if (payload.side == side) {
                val current = activeCombatant
                val at = article.monotonicTimeNanos ?: return null
                if (current != null && at < current.atNanos) return null
                if (current == null || !current.name.equals(payload.speciesName, ignoreCase = true)) {
                    combatantBeforeLatestEntry = current?.takeUnless {
                        it.appearanceArticleId == lastFaintedAppearanceId
                    }
                    activeCombatant = ActiveCombatant(payload.speciesName, payload.speciesId, article.id.value, at)
                    lastFaintedAppearanceId = null
                } else if (current.id == null && payload.speciesId != null) {
                    activeCombatant = current.copy(id = payload.speciesId)
                }
            }
            is BattleCryCandidatesMeasured -> if (payload.candidates.isNotEmpty() && payload.cueKind != "FAST_MOVE_IMPACT") {
                recentCry = CryCandidate(
                    articleId = article.id.value,
                    atNanos = article.monotonicTimeNanos ?: Long.MIN_VALUE
                )
            }
            is OpponentBattleResourceCountMeasured, is com.example.overdex.battle.custody.PlayerPokeBallCountMeasured -> {
                val count = when (payload) {
                    is OpponentBattleResourceCountMeasured -> payload.visibleCount.takeIf {
                        side == ActivePokemonSide.OPPONENT && payload.resource == OpponentBattleResource.POKE_BALLS
                    }
                    is com.example.overdex.battle.custody.PlayerPokeBallCountMeasured -> payload.visibleCount.takeIf { side == ActivePokemonSide.PLAYER }
                    else -> null
                } ?: return null
                val at = article.monotonicTimeNanos ?: return null
                val earlier = previousBallCount
                if (earlier != null && at <= earlier.atNanos) return null
                // Team members cannot revive within a Match. A brighter later
                // crop must not manufacture a second decrease/faint afterwards.
                if (earlier != null && count > earlier.count) return null
                val current = BallCount(count, article.id.value, at)
                previousBallCount = current
                if (earlier == null) return null
                if (current.count >= earlier.count) {
                    // Two agreeing resource crops have now shown that the latest
                    // species change preserved the team count: it was a switch.
                    // A delayed count from before the new entry cannot prove
                    // the replacement kept the same number of survivors.
                    if (at >= (activeCombatant?.atNanos ?: Long.MIN_VALUE)) combatantBeforeLatestEntry = null
                    return null
                }

                // OCR for the replacement can occasionally arrive while the
                // two Poké Ball crops are still being processed. In that order,
                // the Pokémon active before the replacement is the one that
                // belongs to the observed count decrease.
                val active = combatantBeforeLatestEntry ?: activeCombatant
                combatantBeforeLatestEntry = null
                if (active != null && active.appearanceArticleId == lastFaintedAppearanceId) return null
                lastFaintedAppearanceId = active?.appearanceArticleId

                val now = article.monotonicTimeNanos ?: Long.MAX_VALUE
                val cry = recentCry?.takeIf { candidate ->
                    candidate.atNanos != Long.MIN_VALUE &&
                        now >= candidate.atNanos && now - candidate.atNanos <= CRY_CORROBORATION_WINDOW_NANOS
                }
                return TeamFaintDecision(
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
                        "${side.name}_POKE_BALL_COUNT_DECREASE_WITH_BATTLE_CRY"
                    } else {
                        "${side.name}_POKE_BALL_COUNT_DECREASE"
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
