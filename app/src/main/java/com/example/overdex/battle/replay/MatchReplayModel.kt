package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedBattleCryCandidatesMeasured
import com.example.overdex.battle.archive.ArchivedActivePokemonSpeciesWitnessed
import com.example.overdex.battle.archive.ArchivedMatchEnded
import com.example.overdex.battle.archive.ArchivedFastMoveEnergyDerived
import com.example.overdex.battle.archive.ArchivedFastMoveIdentified
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.archive.ArchivedPokemonIdentified
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedPlayerTeamRosterSlotWitnessed
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.model.PokemonType

/** Pure projection of archive evidence into replay-safe scene state. */
class MatchReplayModel(
    private val archive: MatchArchive,
    speciesIdsByName: Map<String, Int> = emptyMap(),
    archivedCropIdentities: List<ReplayIdentityObservation> = emptyList(),
    private val fastMoveTypesByName: Map<String, PokemonType> = emptyMap()
) {
    val timedArticles: List<ArchivedRealityArticle> = archive.articles
        .filter { it.monotonicTimeNanos != null }
        .sortedBy { it.monotonicTimeNanos }

    val startNanos: Long = timedArticles.firstOrNull()?.monotonicTimeNanos ?: 0L
    val endNanos: Long = timedArticles.lastOrNull()?.monotonicTimeNanos ?: startNanos

    // A replay receives a completed archive, not a live stream. Build the identity
    // tracks once so every frame can use facts learned anywhere in that archive.
    private val canonicalSpecies = speciesIdsByName.entries.associateBy { normalizeSpeciesName(it.key) }
    private val reconstructedTracks = reconstructAnnouncementTracks()
    private val cropTracks = archivedCropIdentities
        .sortedBy { it.atNanos }
        .partition { it.side == "PLAYER" }
    private val playerIdentityTrack = ReplayIdentityTrack.from(
        timedArticles, "PLAYER", cropTracks.first.ifEmpty { reconstructedTracks.first }
    )
    private val opponentIdentityTrack = ReplayIdentityTrack.from(
        timedArticles, "OPPONENT", cropTracks.second.ifEmpty { reconstructedTracks.second }
    )

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

        val playerGeneratedEnergy = articles.sumOf { article ->
            (article.payload as? ArchivedFastMoveEnergyDerived)
                ?.takeIf { it.side == "PLAYER" }
                ?.totalEnergyGenerated ?: 0
        }
        val opponentGeneratedEnergy = articles.sumOf { article ->
            (article.payload as? ArchivedFastMoveEnergyDerived)
                ?.takeIf { it.side == "OPPONENT" }
                ?.totalEnergyGenerated ?: 0
        }
        val playerSpentEnergy = articles.sumOf { article ->
            (article.payload as? ArchivedChargedMoveEnergySpent)
                ?.takeIf { it.side == "PLAYER" }
                ?.energyCost ?: 0
        }
        val opponentSpentEnergy = articles.sumOf { article ->
            (article.payload as? ArchivedChargedMoveEnergySpent)
                ?.takeIf { it.side == "OPPONENT" }
                ?.energyCost ?: 0
        }
        // Identification describes a move, not an extra attack. Only individually
        // timed uses can drive animation; a batch total cannot locate each hit.
        val fastMoveActions = articles.mapNotNull { article ->
            val use = article.payload as? ArchivedFastMoveEnergyDerived ?: return@mapNotNull null
            if (use.observedCompletedUses != 1 || use.side !in setOf("PLAYER", "OPPONENT")) return@mapNotNull null
            val ageNanos = monotonicTimeNanos - article.monotonicTimeNanos!!
            if (ageNanos !in 0 until FAST_MOVE_VISUAL_NANOS) return@mapNotNull null
            val type = fastMoveTypesByName[normalizeSpeciesName(use.moveName)] ?: return@mapNotNull null
            ReplayFastMoveAction(use.side, use.moveName, type, article.monotonicTimeNanos,
                ageNanos.toFloat() / FAST_MOVE_VISUAL_NANOS)
        }

        return ReplayScene(
            player = player,
            opponent = opponent,
            observedSpeciesNames = speciesNames,
            candidateSpeciesId = candidate?.candidates?.maxByOrNull { it.similarity }?.speciesId,
            candidateSimilarity = candidate?.candidates?.maxByOrNull { it.similarity }?.similarity,
            outcome = (articles.lastOrNull { it.payload is ArchivedMatchEnded }?.payload as? ArchivedMatchEnded)?.result,
            playerGeneratedEnergy = playerGeneratedEnergy,
            opponentGeneratedEnergy = opponentGeneratedEnergy,
            playerSpentEnergy = playerSpentEnergy,
            opponentSpentEnergy = opponentSpentEnergy,
            fastMoveActions = fastMoveActions,
            latestEvidenceLabel = latest?.payload?.let(::payloadLabel) ?: "Awaiting first timed evidence"
        )
    }


    private fun payloadLabel(payload: Any): String = when (payload) {
        is ArchivedPokemonIdentified -> "SPECIES: ${payload.species}"
        is ArchivedBattleCryCandidatesMeasured -> "BATTLE CRY CANDIDATE"
        is ArchivedMatchEnded -> "MATCH ENDED: ${payload.result}"
        is ArchivedFastMoveIdentified -> "FAST MOVE: ${payload.moveName}"
        is ArchivedFastMoveEnergyDerived -> "FAST ENERGY: ${payload.moveName}"
        is ArchivedChargedMoveEnergySpent -> "CHARGED ENERGY: ${payload.moveName} -${payload.energyCost}"
        else -> payload::class.simpleName.orEmpty().replace("Archived", "")
    }

    /**
     * Older or interrupted recordings can contain exact announcement text even
     * when the typed species witness failed. A completed replay may use that
     * preserved text without changing the archive or pretending it was live OCR.
     */
    private fun reconstructAnnouncementTracks(): Pair<List<ReplayIdentityObservation>, List<ReplayIdentityObservation>> {
        if (canonicalSpecies.isEmpty()) return emptyList<ReplayIdentityObservation>() to emptyList()
        val roster = timedArticles.mapNotNull {
            (it.payload as? ArchivedPlayerTeamRosterSlotWitnessed)?.speciesName
        }.map(::normalizeSpeciesName).toSet()
        val player = mutableListOf<ReplayIdentityObservation>()
        val opponent = mutableListOf<ReplayIdentityObservation>()

        timedArticles.forEach { article ->
            if (article.sourceId != "ANNOUNCEMENT_WITNESS") return@forEach
            val text = (article.payload as? ArchivedRawText)?.value?.trim() ?: return@forEach
            val announced = ENTRY_PATTERN.matchEntire(text)?.groupValues?.get(1)?.trim()
            val moveUser = MOVE_PATTERN.matchEntire(text)?.groupValues?.get(1)?.trim()
            val candidate = announced ?: moveUser ?: return@forEach
            val species = canonicalSpecies[normalizeSpeciesName(candidate)] ?: return@forEach
            val atNanos = article.monotonicTimeNanos ?: return@forEach
            val isPlayer = when {
                normalizeSpeciesName(species.key) in roster -> true
                roster.isNotEmpty() -> false
                announced != null -> false
                else -> opponent.lastOrNull { it.atNanos <= atNanos }?.speciesName
                    ?.let(::normalizeSpeciesName) != normalizeSpeciesName(species.key)
            }
            val observation = ReplayIdentityObservation(
                side = if (isPlayer) "PLAYER" else "OPPONENT",
                speciesName = species.key,
                speciesId = species.value,
                atNanos = atNanos,
                basis = "RECONSTRUCTED FROM ARCHIVED ANNOUNCEMENT"
            )
            val destination = if (isPlayer) player else opponent
            if (destination.lastOrNull()?.speciesName != observation.speciesName) destination += observation
        }
        return player to opponent
    }

    companion object {
        private val ENTRY_PATTERN = Regex("^Go,\\s*(.+?)!*$", RegexOption.IGNORE_CASE)
        private val MOVE_PATTERN = Regex("^(.+?)\\s+used\\s+.+!*$", RegexOption.IGNORE_CASE)

        fun referencedSpeciesNames(archive: MatchArchive): Set<String> = buildSet {
            archive.articles.forEach { article ->
                when (val payload = article.payload) {
                    is ArchivedActivePokemonSpeciesWitnessed -> add(payload.speciesName)
                    is ArchivedPokemonIdentified -> add(payload.species)
                    is ArchivedPlayerTeamRosterSlotWitnessed -> add(payload.speciesName)
                    is ArchivedRawText -> if (article.sourceId == "ANNOUNCEMENT_WITNESS") {
                        ENTRY_PATTERN.matchEntire(payload.value.trim())?.groupValues?.get(1)?.trim()?.let(::add)
                        MOVE_PATTERN.matchEntire(payload.value.trim())?.groupValues?.get(1)?.trim()?.let(::add)
                    }
                    else -> Unit
                }
            }
        }

        private fun normalizeSpeciesName(value: String): String =
            value.uppercase().filter(Char::isLetterOrDigit)
    }
}

private class ReplayIdentityTrack private constructor(
    private val observations: List<ReplayIdentityObservation>
) {
    fun combatantAt(cursorNanos: Long): ReplayCombatant? {
        // The first known combatant is available for the opening interval, but
        // its later establishment stays visible in the replay UI. Each later
        // identity article begins the next known interval.
        val observation = observations.lastOrNull { it.atNanos <= cursorNanos }
            ?: observations.firstOrNull()
            ?: return null
        return ReplayCombatant(
            observation.speciesName,
            observation.speciesId,
            observation.atNanos,
            observation.basis
        )
    }

    companion object {
        fun from(
            articles: List<ArchivedRealityArticle>,
            side: String,
            reconstructed: List<ReplayIdentityObservation>
        ): ReplayIdentityTrack {
            val observed = articles.mapNotNull { article ->
                val species = article.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return@mapNotNull null
                if (species.side != side) return@mapNotNull null
                ReplayIdentityObservation(
                    side,
                    species.speciesName,
                    species.speciesId,
                    article.monotonicTimeNanos ?: return@mapNotNull null,
                    "OBSERVED SPECIES"
                )
            }
            return ReplayIdentityTrack(if (observed.isNotEmpty()) observed else reconstructed)
        }
    }
}

data class ReplayIdentityObservation(
    val side: String,
    val speciesName: String,
    val speciesId: Int?,
    val atNanos: Long,
    val basis: String
)

data class ReplayScene(
    /** The fixed replay coordinate system: PLAYER is left; OPPONENT is right. */
    val player: ReplayCombatant?,
    val opponent: ReplayCombatant?,
    val observedSpeciesNames: List<String>,
    val candidateSpeciesId: Int?,
    val candidateSimilarity: Float?,
    val outcome: String?,
    /** Energy generated from observed, identified fast-move cadence. */
    val playerGeneratedEnergy: Int,
    /** Energy generated from observed, identified fast-move cadence. */
    val opponentGeneratedEnergy: Int,
    /** Cost of named charged moves confirmed by preserved announcements. */
    val playerSpentEnergy: Int,
    /** Cost of named charged moves confirmed by preserved announcements. */
    val opponentSpentEnergy: Int,
    val fastMoveActions: List<ReplayFastMoveAction>,
    val latestEvidenceLabel: String
)

data class ReplayFastMoveAction(
    val side: String,
    val moveName: String,
    val type: PokemonType,
    val startedAtNanos: Long,
    val progress: Float
)

data class ReplayCombatant(
    val speciesName: String,
    val speciesId: Int?,
    val identityEstablishedAtNanos: Long,
    val identityBasis: String = "OBSERVED SPECIES"
)

private const val FAST_MOVE_VISUAL_NANOS = 450_000_000L
