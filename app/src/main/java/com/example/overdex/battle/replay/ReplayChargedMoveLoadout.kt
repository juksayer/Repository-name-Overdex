package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.observation.MoveTextAliases
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType

/** Equipped moves learned from this recording, not the species' possible movepool. */
data class ReplayChargedMoveSlot(
    val slot: Int,
    val moveName: String,
    val type: PokemonType?,
    val energyCost: Int?,
    val evidenceArticleIds: Set<String>,
)

/** Build once from the completed archive so later readings also survive backward scrubs. */
internal class ReplayChargedMoveLoadout(
    articles: List<ArchivedRealityArticle>,
    movesBySpecies: Map<String, List<Move>>,
    combatantAt: (String, Long) -> ReplayCombatant?,
) {
    private val references = movesBySpecies.entries.associate { (species, moves) ->
        key(species) to moves.filterNot(Move::isFast)
    }
    private val slotsBySpecies = mutableMapOf<String, List<ReplayChargedMoveSlot>>()
    private val formsByBase = (movesBySpecies.keys + articles.mapNotNull {
        (it.payload as? ArchivedPlayerTeamSlotConfigured)?.speciesName
    }).distinct().groupBy(TeamRosterSpeciesAttributor::speciesKey)
    private val baseByKey = formsByBase.flatMap { (base, forms) -> forms.map { key(it) to base } }.toMap()

    init {
        val configured = ArchivedPlayerTeamConfiguration(articles).recordsAt()
        configured.groupBy { key((it.payload as ArchivedPlayerTeamSlotConfigured).speciesName) }
            .forEach { (species, records) ->
                // A species alone cannot select between different owned copies.
                if (records.map { (it.payload as ArchivedPlayerTeamSlotConfigured).chargedMoveNames }.distinct().size == 1) {
                    val record = records.last()
                    val team = record.payload as ArchivedPlayerTeamSlotConfigured
                    slotsBySpecies[species] = team.chargedMoveNames.filter(String::isNotBlank).distinct().take(2)
                        .mapIndexed { index, name -> resolve(species, index + 1, name, setOf(record.articleId)) }
                }
            }

        val readings = articles.mapNotNull { article ->
            val label = article.payload as? ArchivedPlayerChargeMoveLabelRead ?: return@mapNotNull null
            if (label.slot !in 1..2 || label.basis != "OCR") return@mapNotNull null
            val at = article.monotonicTimeNanos ?: return@mapNotNull null
            val species = combatantAt("PLAYER", at)?.speciesName?.let(::key) ?: return@mapNotNull null
            val move = references[species].orEmpty().singleOrNull { MoveTextAliases.matchesButtonLabel(label.text, it.name) } ?: return@mapNotNull null
            Triple(species, resolve(species, label.slot, move.name, setOf(article.articleId)), article.articleId)
        }
        readings.groupBy { it.first }.forEach { (species, samples) ->
            val configuredSlots = slotsBySpecies[species].orEmpty()
            val slots = samples.groupBy { it.second.slot }.mapNotNull { (_, slotSamples) ->
                val candidates = slotSamples.groupBy { it.second.moveName }
                val most = candidates.values.maxOf { it.size }
                val best = candidates.values.singleOrNull { it.size == most } ?: return@mapNotNull null
                val chosen = best.first().second
                val old = configuredSlots.firstOrNull { it.slot == chosen.slot }
                // A single noisy frame must not displace explicit equipment. Fresh
                // repeated OCR can correct it; cached pixels are not another vote.
                if (old != null && old.moveName != chosen.moveName && best.size < 2) return@mapNotNull null
                chosen.copy(evidenceArticleIds = best.map { it.third }.toSet())
            }
            // The same label read in both regions cannot establish two equipped moves.
            val unique = slots.filter { slot -> slots.count { it.moveName == slot.moveName } == 1 }
            slotsBySpecies[species] = (configuredSlots.filter { old -> unique.none { it.slot == old.slot || it.moveName == old.moveName } } + unique)
                .sortedBy { it.slot }
        }

        articles.forEach { article ->
            val spent = article.payload as? ArchivedChargedMoveEnergySpent ?: return@forEach
            if (spent.side != "PLAYER" || spent.moveName.isBlank()) return@forEach
            val active = article.monotonicTimeNanos?.let { combatantAt("PLAYER", it)?.speciesName }
            val species = key(active?.takeIf {
                '(' !in spent.speciesName && TeamRosterSpeciesAttributor.speciesKey(it) ==
                    TeamRosterSpeciesAttributor.speciesKey(spent.speciesName)
            } ?: spent.speciesName)
            val slots = slotsBySpecies[species].orEmpty()
            if (slots.any { MoveTextAliases.matches(spent.moveName, it.moveName) } || slots.size >= 2) return@forEach
            val slot = (1..2).first { number -> slots.none { it.slot == number } }
            val known = resolve(species, slot, spent.moveName, setOf(article.articleId))
            slotsBySpecies[species] = slots + known.copy(energyCost = known.energyCost ?: spent.energyCost.takeIf { it > 0 })
        }
    }

    fun movesFor(combatant: ReplayCombatant?): List<ReplayChargedMoveSlot> {
        combatant ?: return emptyList()
        val key = key(combatant.speciesName)
        val candidates = slotsBySpecies.keys.filter { (baseByKey[it] ?: it) == (baseByKey[key] ?: key) }
        if ('(' !in combatant.speciesName && combatant.playerTeamSlot == null && candidates.size > 1) return emptyList()
        return (slotsBySpecies[key] ?: candidates.singleOrNull()?.let(slotsBySpecies::get)).orEmpty()
            .sortedBy(ReplayChargedMoveSlot::slot)
    }

    private fun reference(species: String, name: String) = (references[species] ?: references[baseByKey[species]]).orEmpty()
        .singleOrNull { MoveTextAliases.matches(name, it.name) }

    private fun resolve(species: String, slot: Int, name: String, evidence: Set<String>): ReplayChargedMoveSlot {
        val move = reference(species, name)
        return ReplayChargedMoveSlot(slot, move?.name ?: name, move?.type,
            move?.energy?.takeIf { it > 0 }, evidence)
    }

    private fun key(species: String) = species.uppercase().filter(Char::isLetterOrDigit)
}
