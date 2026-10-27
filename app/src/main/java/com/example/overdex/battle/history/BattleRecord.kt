package com.example.overdex.battle.history

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import com.example.overdex.battle.team.OwnedPokemonBattleSnapshot
import com.example.overdex.model.PokemonSize
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable enum class BattleMetricKind { APPEARANCE, FAST_MOVE, CHARGED_MOVE, FAINT, KNOCKOUT }

/** A queryable projection, never a replacement for the source Timeline articles. */
@Serializable
data class BattleMetricEvent(
    val id: String,
    val kind: BattleMetricKind,
    val atNanos: Long,
    val speciesName: String,
    val pokemon: OwnedPokemonBattleSnapshot?,
    val moveName: String? = null,
    val opponentSpecies: String? = null,
    val articleIds: List<String>,
    val basis: String,
)

@Serializable
data class BattleRecord(
    val matchId: String,
    val recordedAt: Long,
    val matchStartNanos: Long?,
    val result: String?,
    val configuredCards: List<OwnedPokemonBattleSnapshot>,
    val events: List<BattleMetricEvent>,
    val uncreditedOpponentFaints: Int,
    val articleCount: Int,
)

data class BattleRecordQuery(
    val ownedPokemonId: String? = null,
    val speciesName: String? = null,
    val shadowOnly: Boolean = false,
    val size: PokemonSize? = null,
    val kind: BattleMetricKind? = null,
    val moveName: String? = null,
    val opponentSpecies: String? = null,
) {
    fun accepts(event: BattleMetricEvent): Boolean =
        (ownedPokemonId == null || event.pokemon?.ownedPokemonId == ownedPokemonId) &&
        (speciesName.isNullOrBlank() || key(event.speciesName) == key(speciesName)) &&
        (!shadowOnly || event.pokemon?.isShadow == true) &&
        (size == null || event.pokemon?.size == size) &&
        (kind == null || event.kind == kind) &&
        (moveName.isNullOrBlank() || event.moveName?.let(::key) == key(moveName)) &&
        (opponentSpecies.isNullOrBlank() || event.opponentSpecies?.let(::key) == key(opponentSpecies))
}

/**
 * Records witnessed uses only. Configuring Blast Burn does not mean using it;
 * cadence/energy estimates do not manufacture attacks or knockout credits.
 */
object BattleRecordProjector {
    fun project(archive: MatchArchive): BattleRecord {
        val articles = archive.articles.distinctBy { it.articleId }
            .filter { it.monotonicTimeNanos != null }
            .sortedWith(compareBy({ it.monotonicTimeNanos }, { it.sequenceNumber ?: 0L }))
        val start = articles.firstOrNull { it.payload is ArchivedMatchStarted }?.monotonicTimeNanos
        val end = articles.firstOrNull { it.payload is ArchivedMatchEnded }
        val teamConfiguration = ArchivedPlayerTeamConfiguration(articles)
        val configured = teamConfiguration.recordsAt().map { article ->
            article to (article.payload as ArchivedPlayerTeamSlotConfigured)
        }
        // A species read cannot distinguish two cards of the same species. Never
        // attach such an event to the first matching card or a current collection search.
        fun owner(species: String): Pair<ArchivedRealityArticle, ArchivedPlayerTeamSlotConfigured>? =
            configured.filter { key(it.second.speciesName) == key(species) }.singleOrNull()
                ?.takeIf { it.second.ownedPokemon?.speciesId == it.second.speciesId }

        val events = mutableListOf<BattleMetricEvent>()
        var player: Pair<String, String>? = null // species, identity article
        var opponent: Pair<String, String>? = null
        var playerFainted = false
        var lastPlayerHit: BattleMetricEvent? = null
        val seenUses = mutableSetOf<String>()
        val fainted = mutableSetOf<String>()
        var uncredited = 0
        fun event(article: ArchivedRealityArticle, kind: BattleMetricKind, species: String,
            move: String? = null, target: String? = opponent?.first, supporting: List<String> = emptyList(),
            basis: String = "RECORDED_TIMELINE") : BattleMetricEvent {
            val configuration = owner(species)
            return BattleMetricEvent("${article.articleId}:${kind.name}", kind, article.monotonicTimeNanos!!,
                species, configuration?.second?.ownedPokemon, move, target,
                (listOf(article.articleId) + supporting + listOfNotNull(configuration?.first?.articleId)).distinct(), basis)
        }

        articles.forEach { article ->
            val at = article.monotonicTimeNanos!!
            if (end != null && at > end.monotonicTimeNanos!!) return@forEach
            if (article.payload is ArchivedActivePokemonSpeciesWitnessed) {
                val identity = article.payload as ArchivedActivePokemonSpeciesWitnessed
                if (identity.side == "PLAYER") {
                    val changed = player?.first?.let(::key) != key(identity.speciesName)
                    if (changed) { player = identity.speciesName to article.articleId; playerFainted = false; lastPlayerHit = null }
                    if (start != null && at >= start && changed) events += event(article, BattleMetricKind.APPEARANCE, identity.speciesName)
                } else if (identity.side == "OPPONENT") {
                    if (opponent?.first?.let(::key) != key(identity.speciesName)) lastPlayerHit = null
                    opponent = identity.speciesName to article.articleId
                }
            }
            if (start == null || at < start) return@forEach
            when (val payload = article.payload) {
                ArchivedMatchStarted -> player?.let { events += event(article, BattleMetricKind.APPEARANCE, it.first, supporting = listOf(it.second)) }
                is ArchivedFastMoveUseObserved -> {
                    if (payload.attackingSide != "PLAYER" || payload.damagedSide != "OPPONENT" ||
                        playerFainted || !FastMoveUseEvidence.establishesUse(payload.evidenceKinds) || !seenUses.add(payload.useId)) return@forEach
                    val species = payload.attackerSpeciesName ?: player?.first ?: return@forEach
                    if (player?.first?.let(::key)?.let { it != key(species) } == true) return@forEach
                    val move = owner(species)?.second?.fastMoveName ?: articles.asSequence()
                        .filter { it.monotonicTimeNanos!! <= at && it.payload is ArchivedFastMoveIdentified &&
                            teamConfiguration.acceptsDerivedEvidence(it) }
                        .map { it.payload as ArchivedFastMoveIdentified }
                        .lastOrNull { it.side == "PLAYER" && key(it.speciesName) == key(species) }?.moveName
                    val use = event(article, BattleMetricKind.FAST_MOVE, species, move,
                        supporting = listOfNotNull(player?.second, opponent?.second) + article.predecessorIds,
                        basis = "SUPPORTED_FAST_MOVE_USE")
                    events += use
                    lastPlayerHit = use
                }
                is ArchivedChargedMoveEnergySpent -> {
                    if (payload.side != "PLAYER" || playerFainted) return@forEach
                    val species = payload.speciesName
                    if (player?.first?.let(::key)?.let { it != key(species) } == true) return@forEach
                    val duplicate = events.lastOrNull { it.kind == BattleMetricKind.CHARGED_MOVE }
                    if (duplicate != null && key(duplicate.speciesName) == key(species) &&
                        duplicate.moveName?.let(::key) == key(payload.moveName) && at - duplicate.atNanos <= 3_000_000_000L) return@forEach
                    val use = event(article, BattleMetricKind.CHARGED_MOVE, species, payload.moveName,
                        supporting = listOfNotNull(player?.second, opponent?.second) + article.predecessorIds,
                        basis = "NAMED_CHARGED_MOVE")
                    events += use
                    lastPlayerHit = use
                }
                is ArchivedActivePokemonFaintedWitnessed -> {
                    val active = if (payload.side == "PLAYER") player else opponent
                    val species = payload.speciesName ?: active?.first ?: return@forEach
                    if (!fainted.add("${payload.side}:${key(species)}")) return@forEach
                    if (payload.side == "PLAYER") {
                        events += event(article, BattleMetricKind.FAINT, species, supporting = listOfNotNull(active?.second), basis = payload.basis)
                        playerFainted = true
                        lastPlayerHit = null
                    } else if (payload.side == "OPPONENT") {
                        val hit = lastPlayerHit?.takeIf {
                            at - it.atNanos in 0..3_000_000_000L && it.opponentSpecies?.let(::key) == key(species) &&
                                player?.first?.let(::key) == key(it.speciesName) && !playerFainted
                        }
                        if (hit != null) events += event(article, BattleMetricKind.KNOCKOUT, hit.speciesName,
                            hit.moveName, species, hit.articleIds + listOfNotNull(active?.second),
                            "INFERRED_FROM_RECENT_HIT_AND_FAINT")
                        else uncredited++
                        lastPlayerHit = null
                    }
                }
                else -> Unit
            }
        }
        return BattleRecord(archive.matchId, articles.minOfOrNull { it.perceivedAt } ?: 0L, start,
            (end?.payload as? ArchivedMatchEnded)?.result,
            configured.mapNotNull { it.second.ownedPokemon }.distinctBy { it.ownedPokemonId },
            events, uncredited, archive.articles.size)
    }

}

private fun key(value: String): String = value.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)
