package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MoveTextAliases
import com.example.overdex.battle.reality.*
import com.example.overdex.data.PokemonKnowledge
import java.util.TreeMap

internal data class PlayerInformationDerivation(
    val payload: PlayerBattleInformationChecked,
    val observedArticle: RealityArticle,
    val predecessorIds: List<ArticleId>,
)

/** Compare independent observations with reference input; never edit an owned card. */
internal class PlayerBattleInformationInference(private val knowledge: PokemonKnowledge) {
    private val configurationScope = PlayerTeamConfigurationScope()
    private val appearances = TreeMap<Long, RealityArticle?>()
    private val labels = mutableListOf<RealityArticle>()
    private val chargedReferences = mutableMapOf<String, List<com.example.overdex.model.Move>>()
    private val emitted = mutableSetOf<PlayerBattleInformationChecked>()

    suspend fun accept(article: RealityArticle): List<PlayerInformationDerivation> {
        val invalidated = configurationScope.accept(article)
        if (invalidated) { labels.clear(); appearances.clear(); emitted.clear() }
        val configured = configurationScope.at(article.monotonicTimeNanos ?: Long.MAX_VALUE)
        val output = mutableListOf<PlayerInformationDerivation>()
        fun check(species: String, field: String, expected: String?, observed: String,
                  confirmed: Boolean, basis: String, evidence: List<ArticleId>, source: RealityArticle = article,
                  statusOverride: String? = null) {
            val payload = PlayerBattleInformationChecked(species, field, expected, observed,
                statusOverride ?: if (expected == null) "OBSERVED" else if (confirmed) "CONFIRMED" else "DIFFERS", basis)
            if (emitted.add(payload)) output += PlayerInformationDerivation(payload, source, evidence.distinct())
        }
        suspend fun configFor(species: String, speciesId: Int? = null, at: Long? = article.monotonicTimeNanos): RealityArticle? {
            val configured = configurationScope.at(at ?: Long.MAX_VALUE)
            val active = at?.let { appearances.floorEntry(it)?.value?.payload as? ActivePokemonSpeciesWitnessed }
            active?.playerTeamSlot?.let { slot ->
                configured[slot]?.takeIf {
                    key((it.payload as PlayerTeamSlotConfigured).speciesName) == key(species)
                }?.let { return it }
            }
            return configured.values.filter {
                matchesSpecies(it.payload as PlayerTeamSlotConfigured, species, speciesId)
            }.singleOrNull() // Multiple owned copies cannot be distinguished by their name alone.
        }
        when (val p = article.payload) {
            is PlayerTeamSlotConfigured -> Unit
            is PlayerTeamRosterSlotWitnessed -> {
                val config = configured[p.slot]
                val expected = (config?.payload as? PlayerTeamSlotConfigured)?.speciesName
                val matches = (config?.payload as? PlayerTeamSlotConfigured)?.let {
                    matchesSpecies(it, p.speciesName, p.speciesId)
                } == true
                check(p.speciesName, "Team slot ${p.slot}", expected, p.speciesName,
                    matches, "TEAM_SELECT", listOfNotNull(article.id, config?.id),
                    statusOverride = if (matches && expected?.let(::key) != key(p.speciesName)) "COMPATIBLE" else null)
            }
            is ActivePokemonFaintedWitnessed -> if (p.side == ActivePokemonSide.PLAYER) {
                article.monotonicTimeNanos?.let { at ->
                    val active = appearances.floorEntry(at)?.value?.payload as? ActivePokemonSpeciesWitnessed
                    if (p.speciesName == null || active?.speciesName?.let(::key) == key(p.speciesName)) appearances[at] = null
                }
            }
            is ActivePokemonSpeciesWitnessed -> if (p.side == ActivePokemonSide.PLAYER) {
                article.monotonicTimeNanos?.let { appearances[it] = article }
                val config = configFor(p.speciesName, p.speciesId)
                val matching = configured.values.filter {
                    matchesSpecies(it.payload as PlayerTeamSlotConfigured, p.speciesName, p.speciesId)
                }
                val expected = if (configured.isEmpty()) null else configured.values
                    .map { (it.payload as PlayerTeamSlotConfigured).speciesName }.joinToString(" / ")
                check(p.speciesName, "Species", expected, p.speciesName,
                    matching.isNotEmpty(), "ACTIVE_SPECIES_OBSERVATION", listOfNotNull(article.id, config?.id),
                    statusOverride = if (matching.isNotEmpty() && matching.none {
                        key((it.payload as PlayerTeamSlotConfigured).speciesName) == key(p.speciesName)
                    }) "COMPATIBLE" else null)
            }
            is PlayerChargeMoveLabelRead -> if (p.slot in 1..2 && p.basis == "OCR") {
                labels += article
                if (labels.size > 256) labels.removeAt(0)
            }
            is FastMoveIdentified -> if (p.side == ActivePokemonSide.PLAYER &&
                !p.basis.contains("TIE_BREAK_ASSUMPTION") &&
                (p.cadenceSampleCount >= 2 || p.basis.contains("EFFECTIVENESS_TEXT") || p.basis.startsWith("AUDIO_REFERENCE"))) {
                val config = configFor(p.speciesName)
                val expected = (config?.payload as? PlayerTeamSlotConfigured)?.fastMoveName
                check(p.speciesName, "Fast Move", expected, p.moveName,
                    expected?.let { MoveTextAliases.matches(p.moveName, it) } == true,
                    p.basis, listOfNotNull(article.id, config?.id), statusOverride =
                        if (expected != null && p.basis.startsWith("CONFIGURED_PLAYER_TEAM")) "COMPATIBLE" else null)
            }
            else -> return emptyList()
        }
        // Source time, not arrival order, assigns delayed button OCR to its combatant.
        // Two separate OCR frames must agree; a cached pixel comparison is not another vote.
        if (article.payload is PlayerChargeMoveLabelRead || article.payload is ActivePokemonSpeciesWitnessed) {
            data class Reading(val species: String, val slot: Int, val name: String,
                               val source: RealityArticle, val identity: RealityArticle)
            val readings = labels.mapNotNull { sample ->
                val at = sample.monotonicTimeNanos ?: return@mapNotNull null
                val identity = appearances.floorEntry(at)?.value ?: return@mapNotNull null
                val species = (identity.payload as ActivePokemonSpeciesWitnessed).speciesName
                val label = sample.payload as PlayerChargeMoveLabelRead
                val moves = chargedReferences.getOrPut(key(species)) {
                    knowledge.getPokemonByName(species)?.chargedMoves.orEmpty().filterNot { it.isFast }
                }
                val move = moves.singleOrNull { MoveTextAliases.matchesButtonLabel(label.text, it.name) }
                    ?: return@mapNotNull null
                Reading(species, label.slot, move.name, sample, identity)
            }
            readings.groupBy { key(it.species) to it.slot }.values.forEach { slotReadings ->
                val candidates = slotReadings.groupBy { it.name }
                val most = candidates.values.maxOf { it.size }
                val best = candidates.values.singleOrNull { it.size == most && it.size >= 2 } ?: return@forEach
                val reading = best.maxBy { it.source.monotonicTimeNanos!! }
                val config = configFor(reading.species, at = reading.source.monotonicTimeNanos)
                val expected = (config?.payload as? PlayerTeamSlotConfigured)?.chargedMoveNames
                check(reading.species, "Charged move ${reading.slot}", expected?.joinToString(" / "), reading.name,
                    expected?.any { MoveTextAliases.matches(reading.name, it) } == true, "REPEATED_BUTTON_LABEL_OCR",
                    best.flatMap { listOf(it.source.id, it.identity.id) } + listOfNotNull(config?.id), reading.source)
            }
        }
        return output
    }

    /** A bare badge name can support its saved form, but cannot prove that form or size. */
    private suspend fun matchesSpecies(config: PlayerTeamSlotConfigured, name: String, speciesId: Int?): Boolean {
        if (key(config.speciesName) == key(name)) return true
        if ('(' in name || key(config.speciesName.substringBefore('(')) != key(name)) return false
        val observedId = speciesId ?: knowledge.getPokemonByName(name)?.id ?: return false
        return config.speciesId == observedId
    }

    private fun key(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
}
