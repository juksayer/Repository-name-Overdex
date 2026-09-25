package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedBattleCryCandidatesMeasured
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedPokemonIdentified
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.MatchArchive

/** Pure projection of archive evidence into replay-safe scene state. */
class MatchReplayModel(private val archive: MatchArchive) {
    val timedArticles: List<ArchivedRealityArticle> = archive.articles
        .filter { it.monotonicTimeNanos != null }
        .sortedBy { it.monotonicTimeNanos }

    val startNanos: Long = timedArticles.firstOrNull()?.monotonicTimeNanos ?: 0L
    val endNanos: Long = timedArticles.lastOrNull()?.monotonicTimeNanos ?: startNanos

    // A replay receives a completed archive, not a live stream. Build the identity
    // tracks once so every frame can use facts learned anywhere in that archive.
    private val playerIdentityTrack = ReplayIdentityTrack.from(timedArticles, "PLAYER")
    private val opponentIdentityTrack = ReplayIdentityTrack.from(timedArticles, "OPPONENT")

    fun crossedArticleBoundary(fromNanos: Long, toNanos: Long): Boolean {
        if (fromNanos == toNanos) return false
        val lower = minOf(fromNanos, toNanos)
        val upper = maxOf(fromNanos, toNanos)
        return timedArticles.any { it.monotonicTimeNanos!! > lower && it.monotonicTimeNanos!! <= upper }
    }

    fun sceneAt(monotonicTimeNanos: Long): ReplayScene {
        val articles = timedArticles.takeWhile { it.monotonicTimeNanos!! <= monotonicTimeNanos }
        val speciesNames = articles.mapNotNull { (it.payload as? ArchivedPokemonIdentified)?.species }.distinct()
        val candidateArticle = articles.lastOrNull { it.payload is ArchivedBattleCryCandidatesMeasured }
        val candidate = candidateArticle?.payload as? ArchivedBattleCryCandidatesMeasured
        val latest = articles.lastOrNull()
        // The prebuilt tracks choose the correct known identity interval.
        val player = playerIdentityTrack.combatantAt(monotonicTimeNanos)
        val opponent = opponentIdentityTrack.combatantAt(monotonicTimeNanos)
        // A ranked cry candidate stays measurable evidence only. It cannot
        // create a replay combatant or sprite without an identity article.

        return ReplayScene(
            player = player,
            opponent = opponent,
            observedSpeciesNames = speciesNames,
            candidateSpeciesId = candidate?.candidates?.maxByOrNull { it.similarity }?.speciesId,
            candidateSimilarity = candidate?.candidates?.maxByOrNull { it.similarity }?.similarity,
            outcome = (articles.lastOrNull { it.payload is ArchivedMatchEnded }?.payload as? ArchivedMatchEnded)?.result,
            latestEvidenceLabel = latest?.payload?.let(::payloadLabel) ?: "Awaiting first timed evidence"
        )
    }


    private fun payloadLabel(payload: Any): String = when (payload) {
        is ArchivedPokemonIdentified -> "SPECIES: ${payload.species}"
        is ArchivedBattleCryCandidatesMeasured -> "BATTLE CRY CANDIDATE"
        is ArchivedMatchEnded -> "MATCH ENDED: ${payload.result}"
        else -> payload::class.simpleName.orEmpty().replace("Archived", "")
    }
}

private class ReplayIdentityTrack private constructor(
    private val observations: List<ArchivedRealityArticle>
) {
    fun combatantAt(cursorNanos: Long): ReplayCombatant? {
        // The first known combatant is available for the opening interval, but
        // its later establishment stays visible in the replay UI. Each later
        // identity article begins the next known interval.
        val article = observations.lastOrNull { it.monotonicTimeNanos!! <= cursorNanos }
            ?: observations.firstOrNull()
            ?: return null
        val species = article.payload as ArchivedActivePokemonSpeciesWitnessed
        return ReplayCombatant(species.speciesName, species.speciesId, article.monotonicTimeNanos!!)
    }

    companion object {
        fun from(articles: List<ArchivedRealityArticle>, side: String) = ReplayIdentityTrack(
            articles.filter { (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.side == side }
        )
    }
}

data class ReplayScene(
    /** The fixed replay coordinate system: PLAYER is left; OPPONENT is right. */
    val player: ReplayCombatant?,
    val opponent: ReplayCombatant?,
    val observedSpeciesNames: List<String>,
    val candidateSpeciesId: Int?,
    val candidateSimilarity: Float?,
    val outcome: String?,
    val latestEvidenceLabel: String
)

data class ReplayCombatant(
    val speciesName: String,
    val speciesId: Int?,
    val identityEstablishedAtNanos: Long,
    val identityBasis: String = "OBSERVED SPECIES"
)
