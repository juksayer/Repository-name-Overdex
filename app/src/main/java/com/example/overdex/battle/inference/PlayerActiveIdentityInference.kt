package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MoveTextAliases
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonFormReference
import java.util.TreeMap

/**
 * A bare badge is not a form. Bind it to one player appearance using the observed
 * lead, an unambiguous roster member, or two independent charge-button readings.
 * Opponent identities never consult this roster. All lookups use capture time.
 */
internal class PlayerActiveIdentityInference {
    data class Resolution(
        val payload: PlayerActiveIdentityResolved,
        val source: RealityArticle,
        val evidence: List<ArticleId>,
    ) {
        fun speciesProjection(article: RealityArticle) = article.copy(payload = ActivePokemonSpeciesWitnessed(
            ActivePokemonSide.PLAYER, payload.speciesName, payload.speciesId,
            playerTeamSlot = payload.teamSlot,
            appearanceStartedAtNanos = payload.appearanceStartedAtNanos,
        ))
    }

    private data class Member(val slot: Int, val name: String, val id: Int?, val article: ArticleId)
    private data class Appearance(
        val source: RealityArticle,
        val observed: ActivePokemonSpeciesWitnessed,
        val start: Long,
        val candidates: List<Member>,
        var resolution: Resolution,
        var end: Long = Long.MAX_VALUE,
        var entryAt: Long? = null,
        val labels: MutableList<RealityArticle> = mutableListOf(),
    )
    private val scanned = mutableMapOf<Int, Member>()
    private val configurationScope = PlayerTeamConfigurationScope()
    private val appearances = TreeMap<Long, Appearance>()
    private val faintedSlots = mutableSetOf<Int>()
    private var lastFaint: Long? = null

    fun accept(article: RealityArticle): List<Resolution> {
        val at = article.monotonicTimeNanos ?: return emptyList()
        if (configurationScope.accept(article)) {
            val previous = scanned.toMap()
            scanned.clear()
            (article.payload as PlayerTeamRosterConfirmation).speciesBySlot.forEachIndexed { index, name ->
                if (!name.isNullOrBlank()) scanned[index + 1] = Member(index + 1, name,
                    previous[index + 1]?.takeIf { key(it.name) == key(name) }?.id, article.id)
            }
            appearances.clear(); faintedSlots.clear(); lastFaint = null
            return emptyList()
        }
        when (val p = article.payload) {
            ObservationRestartedByUser -> {
                scanned.clear(); configurationScope.clearConfigurations()
                appearances.clear(); faintedSlots.clear(); lastFaint = null
            }
            is PlayerTeamRosterSlotWitnessed -> {
                if (configurationScope.latestConfirmationNanos?.let { at <= it } == true) return emptyList()
                if (appearances.isNotEmpty()) return emptyList()
                val previous = scanned[p.slot]
                val name = PokemonFormReference.mergeRosterName(previous?.name, p.speciesName, p.spriteVariant != null)
                scanned[p.slot] = Member(p.slot, name, p.speciesId,
                    if (name != p.speciesName && previous != null) previous.article else article.id)
            }
            is PlayerTeamSlotConfigured -> Unit // Source-time configuration is retained by the scope above.
            is ActivePokemonSpeciesWitnessed -> {
                if (p.side != ActivePokemonSide.PLAYER) return emptyList()
                if (configurationScope.latestConfirmationNanos?.let { at <= it } == true) return emptyList()
                val previous = appearances.lastEntry()?.value
                if (previous != null && at < previous.start) return emptyList()
                val isEntry = article.sourceId.id == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"
                val sameBase = previous != null && base(previous.observed.speciesName) == base(p.speciesName)
                val newEntry = isEntry && previous != null &&
                    at - (previous.entryAt ?: previous.start) > ENTRY_EPISODE_NANOS
                val explicitChange = '(' in p.speciesName && previous != null &&
                    key(previous.resolution.payload.speciesName) != key(p.speciesName)
                if (sameBase && !newEntry && !explicitChange && previous!!.end == Long.MAX_VALUE) {
                    if (isEntry) previous.entryAt = at
                    return emptyList()
                }
                // A lingering badge after a KO is not an appearance of the other
                // Raichu. Wait for its entry announcement or a different species.
                if (sameBase && previous!!.end != Long.MAX_VALUE && !isEntry) return emptyList()
                val matching = members().filter { base(it.name) == base(p.speciesName) }
                if (matching.isNotEmpty() && matching.all { it.slot in faintedSlots }) return emptyList()
                previous?.let { if (at < it.end) it.end = at }
                val roster = matching.filter { it.slot !in faintedSlots }
                val candidates = if ('(' in p.speciesName) roster.filter { key(it.name) == key(p.speciesName) } else roster
                val lead = scanned[1]?.takeIf {
                    appearances.isEmpty() && lastFaint == null && it in candidates &&
                        base(it.name) == base(p.speciesName)
                }
                val member = lead ?: candidates.singleOrNull()
                val basis = when {
                    lead != null -> "OBSERVED_TEAM_SELECT_LEAD_AND_PLAYER_SPECIES"
                    member != null -> "PLAYER_SPECIES_AND_UNIQUE_ROSTER_SLOT"
                    else -> "PLAYER_SPECIES_WITH_UNRESOLVED_ROSTER_SLOT"
                }
                val resolved = resolution(article, p, at, member, basis,
                    listOf(article.id) + listOfNotNull(member?.article))
                appearances[at] = Appearance(article, p, at, candidates, resolved, entryAt = at.takeIf { isEntry })
                return listOf(resolved)
            }
            is ActivePokemonFaintedWitnessed -> if (p.side == ActivePokemonSide.PLAYER) {
                val incoming = appearanceAt(at) ?: return emptyList()
                val outgoing = appearances.lowerEntry(incoming.start)?.value
                // Resource counts can arrive just after the incoming entry. A
                // same-name entry is still a new actor, not the actor that fainted.
                val delayedOutgoing = outgoing?.takeIf {
                    incoming.entryAt != null && at - incoming.start in 0..ENTRY_EPISODE_NANOS &&
                        p.speciesName != null && base(p.speciesName) == base(it.observed.speciesName)
                }
                val active = delayedOutgoing ?: incoming
                if (p.speciesName != null && base(p.speciesName) != base(active.observed.speciesName)) return emptyList()
                active.end = minOf(active.end, at)
                active.resolution.payload.teamSlot?.let(faintedSlots::add)
                lastFaint = maxOf(at, lastFaint ?: at)
                if (delayedOutgoing != null && incoming.resolution.payload.teamSlot == null) {
                    val member = incoming.candidates.filter { it.slot !in faintedSlots }.singleOrNull()
                        ?: return emptyList()
                    val result = resolution(incoming.source, incoming.observed, incoming.start, member,
                        "PLAYER_ENTRY_AND_OUTGOING_FAINT", incoming.resolution.evidence + article.id)
                    incoming.resolution = result
                    return listOf(result)
                }
            }
            is PlayerChargeMoveLabelRead -> {
                if (p.slot !in 1..2 || p.basis != "OCR") return emptyList()
                val active = appearanceAt(at) ?: return emptyList()
                if (active.labels.any { it.id == article.id }) return emptyList()
                active.labels += article
                if (active.labels.size > 64) active.labels.removeAt(0)
                val recent = active.labels.filter { at - (it.monotonicTimeNanos ?: at) in 0..LABEL_WINDOW_NANOS }
                // Both buttons, or the same distinct label in two source frames.
                // Cached text and duplicate delivery are not independent votes.
                if (recent.map { it.monotonicTimeNanos }.distinct().size < 2) return emptyList()
                val candidates = active.candidates.filter { member ->
                    recent.all { sample ->
                        val config = configurationFor(member, sample.monotonicTimeNanos!!)?.first ?: return@all false
                        config.chargedMoveNames.any { move ->
                            MoveTextAliases.matchesButtonLabel((sample.payload as PlayerChargeMoveLabelRead).text, move)
                        }
                    }
                }
                val member = candidates.singleOrNull() ?: return emptyList()
                if (active.resolution.payload.teamSlot == member.slot) return emptyList()
                val result = resolution(active.source, active.observed, active.start, member,
                    "PLAYER_SPECIES_AND_REPEATED_CHARGE_BUTTON_LABELS", listOf(active.source.id, member.article) +
                        recent.mapNotNull { configurationFor(member, it.monotonicTimeNanos!!)?.second?.id } + recent.map { it.id })
                active.resolution = result
                return listOf(result)
            }
            else -> Unit
        }
        return emptyList()
    }

    fun at(time: Long): Resolution? = appearanceAt(time)?.resolution
    fun lastIdentityAt(time: Long): Resolution? = appearances.floorEntry(time)?.value?.resolution

    /** Reuse a fresh badge as confirmation without losing the resolved form/slot or starting a new appearance. */
    fun projectSpeciesReconfirmation(article: RealityArticle): RealityArticle? {
        val species = article.payload as? ActivePokemonSpeciesWitnessed ?: return null
        if (species.side != ActivePokemonSide.PLAYER) return null
        val resolution = article.monotonicTimeNanos?.let(::at) ?: return null
        if (base(species.speciesName) != base(resolution.payload.speciesName) ||
            ('(' in species.speciesName && key(species.speciesName) != key(resolution.payload.speciesName))) return null
        return resolution.speciesProjection(article).copy(
            predecessorIds = (article.predecessorIds + resolution.evidence).filter { it != article.id }.distinct(),
        )
    }

    fun projectFaint(article: RealityArticle): RealityArticle {
        val p = article.payload as? ActivePokemonFaintedWitnessed ?: return article
        if (p.side != ActivePokemonSide.PLAYER) return article
        val at = article.monotonicTimeNanos ?: return article
        val current = appearances.floorEntry(at)?.value ?: return article
        val outgoing = appearances.lowerEntry(current.start)?.value
        val a = outgoing?.takeIf {
            current.entryAt != null && at - current.start in 0..ENTRY_EPISODE_NANOS &&
                p.speciesName != null && base(p.speciesName) == base(it.observed.speciesName)
        } ?: current.takeIf { at <= it.end } ?: return article
        if (p.speciesName != null && base(p.speciesName) != base(a.observed.speciesName)) return article
        return article.copy(payload = p.copy(speciesName = a.resolution.payload.speciesName,
            speciesId = a.resolution.payload.speciesId))
    }

    fun resolutions(): List<Resolution> = appearances.values.map { it.resolution }

    private fun appearanceAt(at: Long) = appearances.floorEntry(at)?.value?.takeIf { at < it.end }
    private fun members(): List<Member> {
        val configured = configurationScope.current()
        return (scanned.keys + configured.keys).sorted().mapNotNull { slot ->
            scanned[slot] ?: configured[slot]?.let { article ->
                val p = article.payload as PlayerTeamSlotConfigured
                Member(slot, p.speciesName, p.speciesId, article.id)
            }
        }
    }
    private fun configurationFor(member: Member, at: Long): Pair<PlayerTeamSlotConfigured, RealityArticle>? =
        configurationScope.at(at).values.map { (it.payload as PlayerTeamSlotConfigured) to it }
            .filter { key(it.first.speciesName) == key(member.name) }.singleOrNull()
    private fun resolution(source: RealityArticle, observed: ActivePokemonSpeciesWitnessed, at: Long,
                           member: Member?, basis: String, evidence: List<ArticleId>) = Resolution(
        PlayerActiveIdentityResolved(observed.speciesName, member?.name ?: observed.speciesName,
            member?.id ?: observed.speciesId, member?.slot, at, basis), source, evidence.distinct())

    private fun base(name: String) = TeamRosterSpeciesAttributor.speciesKey(name)
    private fun key(name: String) = name.uppercase().filter(Char::isLetterOrDigit)
    private companion object {
        const val ENTRY_EPISODE_NANOS = 2_000_000_000L
        const val LABEL_WINDOW_NANOS = 6_000_000_000L
    }
}
