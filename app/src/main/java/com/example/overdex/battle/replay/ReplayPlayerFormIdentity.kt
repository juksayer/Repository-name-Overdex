package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.PlayerActiveIdentityInference
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle

/** Read-only form reconstruction for old recordings as well as new identity Articles. */
internal class ReplayPlayerFormIdentity(articles: List<ArchivedRealityArticle>) {
    private val identities = PlayerActiveIdentityInference()
    private val configuration = ArchivedPlayerTeamConfiguration(articles)
    private val recorded = java.util.TreeMap<Long, ArchivedRealityArticle>().apply {
        articles.sortedBy { it.recordedAt }.forEach { article ->
            (article.payload as? ArchivedPlayerActiveIdentityResolved)?.takeIf {
                configuration.acceptsDerivedEvidence(article)
            }?.let { this[it.appearanceStartedAtNanos] = article }
        }
    }
    init {
        articles.sortedBy { it.monotonicTimeNanos ?: Long.MIN_VALUE }.forEach { article ->
            val at = article.monotonicTimeNanos ?: return@forEach
            val payload = when (val p = article.payload) {
                is ArchivedPlayerTeamRosterSlotWitnessed -> PlayerTeamRosterSlotWitnessed(
                    p.slot, p.speciesName, p.speciesId, p.spriteVariant, p.distinctFormMargin)
                // Old/partial archives may omit empty slots. Preserve the reset
                // boundary and known slots without inventing members or crashing.
                is ArchivedPlayerTeamRosterConfirmation -> PlayerTeamRosterConfirmation(p.confirmed,
                    List(3) { p.speciesBySlot.getOrNull(it)?.takeIf(String::isNotBlank) })
                is ArchivedPlayerTeamSlotConfigured -> {
                    // Historical exports can contain an unfinished team editor.
                    // Its partial configuration cannot supply a confirmed move.
                    if (p.slot !in 1..3 || p.speciesName.isBlank() || p.fastMoveName.isBlank() ||
                        p.chargedMoveNames.size !in 1..2) return@forEach
                    PlayerTeamSlotConfigured(p.slot, p.speciesName, p.speciesId, p.fastMoveName, p.chargedMoveNames, p.ownedPokemon)
                }
                is ArchivedActivePokemonSpeciesWitnessed -> {
                    if (article.sourceId == "TEAM_SELECT_LEAD_INFERENCE" && configuredLeadContradicted(articles)) return@forEach
                    val side = when (article.sourceId) {
                        "ANNOUNCEMENT_SPECIES_ROSTER_ATTRIBUTION_WITNESS" -> archivedNamedMoveSide(p.speciesName, at, articles)
                        "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" -> archivedEntrySide(p.speciesName, at, articles)
                        else -> p.side
                    }
                    val knownSide = ActivePokemonSide.entries.firstOrNull { it.name == side } ?: return@forEach
                    ActivePokemonSpeciesWitnessed(knownSide, p.speciesName, p.speciesId, p.ocrText, p.ocrMatchBasis)
                }
                is ArchivedActivePokemonFaintedWitnessed -> ActivePokemonFaintedWitnessed(
                    ActivePokemonSide.valueOf(p.side), p.speciesName, p.speciesId, p.basis)
                is ArchivedPlayerChargeMoveLabelRead -> PlayerChargeMoveLabelRead(p.slot, p.text, p.basis)
                ArchivedObservationRestartedByUser -> ObservationRestartedByUser
                else -> return@forEach
            }
            identities.accept(RealityArticle(ArticleId(article.articleId), article.perceivedAt,
                article.recordedAt, SourceId(article.sourceId), payload, monotonicTimeNanos = at))
        }
    }

    fun refine(observation: ReplayIdentityObservation): ReplayIdentityObservation {
        if (observation.side != "PLAYER") return observation
        recorded.floorEntry(observation.atNanos)?.value?.let { article ->
            val p = article.payload as ArchivedPlayerActiveIdentityResolved
            if (p.teamSlot != null && TeamRosterSpeciesAttributor.speciesKey(p.observedSpeciesName) ==
                TeamRosterSpeciesAttributor.speciesKey(observation.speciesName)) return observation.copy(
                speciesName = p.speciesName, speciesId = p.speciesId, playerTeamSlot = p.teamSlot,
                basis = observation.basis + " / " + p.basis,
                evidenceArticleIds = (observation.evidenceArticleIds + article.articleId + article.predecessorIds).distinct())
        }
        val resolution = identities.lastIdentityAt(observation.atNanos) ?: return observation
        val p = resolution.payload
        if (TeamRosterSpeciesAttributor.speciesKey(p.observedSpeciesName) !=
            TeamRosterSpeciesAttributor.speciesKey(observation.speciesName)) return observation
        if (p.teamSlot == null) return observation
        return observation.copy(speciesName = p.speciesName, speciesId = p.speciesId,
            basis = observation.basis + " / " + p.basis,
            playerTeamSlot = p.teamSlot,
            evidenceArticleIds = (observation.evidenceArticleIds + resolution.evidence.map { it.value }).distinct())
    }

    fun observations(): List<ReplayIdentityObservation> = identities.resolutions().filter {
        it.payload.teamSlot != null &&
            (recorded[it.payload.appearanceStartedAtNanos]?.payload as? ArchivedPlayerActiveIdentityResolved)?.teamSlot == null
    }.map { r ->
        ReplayIdentityObservation("PLAYER", r.payload.speciesName, r.payload.speciesId,
            r.payload.appearanceStartedAtNanos, "RECONSTRUCTED PLAYER FORM / " + r.payload.basis,
            articleId = r.source.id.value, evidenceArticleIds = r.evidence.map { it.value },
            playerTeamSlot = r.payload.teamSlot)
    } + recorded.values.filter { article ->
        val p = article.payload as ArchivedPlayerActiveIdentityResolved
        p.teamSlot != null || identities.resolutions().none {
            it.payload.appearanceStartedAtNanos == p.appearanceStartedAtNanos && it.payload.teamSlot != null
        }
    }.map { article ->
        val p = article.payload as ArchivedPlayerActiveIdentityResolved
        ReplayIdentityObservation("PLAYER", p.speciesName, p.speciesId, p.appearanceStartedAtNanos,
            "RESOLVED PLAYER IDENTITY / ${p.basis}", articleId = article.articleId,
            evidenceArticleIds = article.predecessorIds, playerTeamSlot = p.teamSlot)
    }
}
