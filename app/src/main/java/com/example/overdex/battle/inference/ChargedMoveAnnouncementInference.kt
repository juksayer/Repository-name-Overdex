package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.ChargedMoveEnergySpent
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.PlayerChargeButtonTouchWitnessed
import com.example.overdex.battle.custody.PlayerChargeMoveLabelRead
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.observation.SpeciesTextResolver
import com.example.overdex.battle.observation.SpeciesOcrTypography
import com.example.overdex.battle.observation.MoveTextAliases
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Pokemon
import com.example.overdex.model.Move

data class ChargedMoveAnnouncementDerivation(
    val payload: ChargedMoveEnergySpent,
    val predecessorIds: List<ArticleId>,
    val observedArticle: RealityArticle
)

/**
 * Resolves a preserved “<Pokémon> used <move>!” announcement against immutable
 * Pokédex knowledge. It remains silent if the announcement, move, or performer
 * is ambiguous; the original raw announcement stays available either way.
 */
class ChargedMoveAnnouncementInference(
    private val pokemonKnowledge: PokemonKnowledge
) {
    private val activeSpeciesHistory = ArrayDeque<RealityArticle>()
    private var knownSpeciesNames: Set<String>? = null
    private data class Spend(val side: ActivePokemonSide, val species: String, val move: String, val at: Long)
    private val recentSpends = ArrayDeque<Spend>()
    private val controlEvidence = ArrayDeque<RealityArticle>()
    private val pendingAnnouncements = ArrayDeque<RealityArticle>()
    private val speciesByReading = linkedMapOf<String, String?>()
    private val pokemonByName = mutableMapOf<String, Pokemon?>()

    suspend fun accept(
        article: RealityArticle,
        rosterSideForSpecies: (String) -> ActivePokemonSide?
    ): ChargedMoveAnnouncementDerivation? {
        val updatesControls = article.payload is PlayerChargeButtonTouchWitnessed || article.payload is PlayerChargeMoveLabelRead ||
            article.payload is GetReadyWitnessed || article.payload is AttackIncoming || article.payload is ActivePokemonFaintedWitnessed
        val isAnnouncement = article.sourceId.id == ANNOUNCEMENT_SOURCE && article.payload is RawTestimony
        if (!updatesControls && !isAnnouncement && article.payload !is ActivePokemonSpeciesWitnessed) return null
        (article.payload as? ActivePokemonSpeciesWitnessed)?.let {
            activeSpeciesHistory.addLast(article)
            while (activeSpeciesHistory.size > 128) activeSpeciesHistory.removeFirst()
            return null
        }
        if (updatesControls) {
            controlEvidence.addLast(article)
            while (controlEvidence.size > 512) controlEvidence.removeFirst()
        }
        if (isAnnouncement && ((article.payload as RawTestimony).data as? String)?.let {
                USED_MOVE_PATTERN.matches(it.trim()) || COMPACT_USED_MOVE_PATTERN.matches(it.trim())
            } == true) {
            pendingAnnouncements.addLast(article)
            while (pendingAnnouncements.size > 32) pendingAnnouncements.removeFirst()
        }
        // Label and pointer OCR have independent latency. A later publication
        // may complete an earlier announcement, using original capture times.
        for (pending in pendingAnnouncements.toList()) {
            val result = resolveAnnouncement(pending, rosterSideForSpecies) ?: continue
            pendingAnnouncements.remove(pending)
            return result
        }
        return null
    }

    private suspend fun resolveAnnouncement(
        article: RealityArticle,
        rosterSideForSpecies: (String) -> ActivePokemonSide?
    ): ChargedMoveAnnouncementDerivation? {
        val text = ((article.payload as? RawTestimony)?.data as? String)?.trim() ?: return null
        val match = USED_MOVE_PATTERN.matchEntire(text) ?: COMPACT_USED_MOVE_PATTERN.matchEntire(text) ?: return null
        val at = article.monotonicTimeNanos ?: return null
        val activeAtUse = activeSpeciesHistory.filter { (it.monotonicTimeNanos ?: Long.MAX_VALUE) <= at }
            .groupBy { (it.payload as ActivePokemonSpeciesWitnessed).side }
            .mapValues { (_, appearances) -> appearances.maxBy { it.monotonicTimeNanos!! } }
        val activeSpeciesBySide = activeAtUse.mapValues { (_, appearance) ->
            (appearance.payload as ActivePokemonSpeciesWitnessed).speciesName
        }
        val performerText = match.groupValues[1]
        val performerKey = normalize(performerText)
        val speciesName = if (speciesByReading.containsKey(performerKey)) speciesByReading[performerKey] else {
            SpeciesTextResolver.resolve(performerText, knownSpeciesNames ?: pokemonKnowledge.getAllSpeciesNames()
                .also { knownSpeciesNames = it }).also {
                speciesByReading[performerKey] = it
                if (speciesByReading.size > 64) speciesByReading.remove(speciesByReading.keys.first())
            }
        }
        val moveName = normalize(match.groupValues[2])
        val pokemon: Pokemon
        val move: Move
        val side: ActivePokemonSide
        val supportingIds: List<ArticleId>
        val basis: String
        if (speciesName != null) {
            pokemon = pokemon(speciesName) ?: return null
            val activeSides = activeSpeciesBySide.filterValues { normalize(it) == normalize(speciesName) }.keys
            val selection = if (ActivePokemonSide.PLAYER in activeSides) {
                selectedPlayerMove(pokemon, moveName, at, requireReady = activeSides.size > 1)
            } else null
            side = when {
                activeSides.size == 1 -> activeSides.single()
                selection != null -> ActivePokemonSide.PLAYER
                activeSides.isNotEmpty() -> null // Mirrors still need independent direction evidence.
                else -> rosterSideForSpecies(speciesName)
            } ?: return null
            val exact = bestMoveMatches(moveName, pokemon.chargedMoves).singleOrNull()?.move
            move = exact ?: selection?.move ?: return null
            if (exact == null || activeSides.size > 1) {
                supportingIds = selection?.ids ?: return null
                basis = "ANNOUNCEMENT_AND_PLAYER_BUTTON_LABEL_AND_VISIBLE_TOUCH"
            } else {
                supportingIds = emptyList()
                basis = "ANNOUNCED_NAMED_CHARGED_MOVE_AND_REFERENCE_ENERGY_COST"
            }
        } else {
            // Pokémon GO shows player nicknames here. The observed move can resolve
            // the performer only when BOTH active combatants are known and exactly
            // one can use it. A missing opponent is not proof it cannot use the move.
            if (activeSpeciesBySide.keys != ActivePokemonSide.entries.toSet()) return null
            val candidates = mutableListOf<Triple<ActivePokemonSide, Pokemon, MoveMatch>>()
            for ((activeSide, name) in activeSpeciesBySide) {
                val active = pokemon(name) ?: return null
                bestMoveMatches(moveName, active.chargedMoves).forEach {
                    candidates += Triple(activeSide, active, it)
                }
            }
            val bestDistance = candidates.minOfOrNull { it.third.edits } ?: return null
            val candidate = candidates.filter { it.third.edits == bestDistance }.singleOrNull() ?: return null
            side = candidate.first; pokemon = candidate.second; move = candidate.third.move
            supportingIds = activeAtUse.values.map { it.id }
            basis = "ANNOUNCED_CHARGED_MOVE_AND_UNIQUE_ACTIVE_MOVEPOOL"
        }
        // OCR can publish the same on-screen announcement with and without
        // punctuation. Preserve both raw readings, but debit this use only once.
        val spend = Spend(side, normalize(pokemon.name), normalize(move.name), at)
        if (recentSpends.any {
                it.side == spend.side && it.species == spend.species && it.move == spend.move &&
                    kotlin.math.abs(it.at - at) <= ANNOUNCEMENT_EPISODE_NANOS
            }) return null
        recentSpends.addLast(spend)
        while (recentSpends.size > 64) recentSpends.removeFirst()
        return ChargedMoveAnnouncementDerivation(
            payload = ChargedMoveEnergySpent(
                side = side,
                speciesName = pokemon.name,
                moveName = move.name,
                energyCost = move.energy,
                basis = basis + when {
                    basis.contains("VISIBLE_TOUCH") -> ""
                    moveName == normalize(move.name) -> ""
                    MoveTextAliases.matches(moveName, move.name) -> "_VISIBLE_MOVE_NAME_ALIAS"
                    MoveTextAliases.normalizedNames(move.name).any { SpeciesOcrTypography.glyphRepairCost(moveName, it) != null } -> "_OCR_LEGAL_GLYPH_REPAIR"
                    else -> "_OCR_SINGLE_CHARACTER_REPAIR"
                }
            ),
            predecessorIds = listOf(article.id) + supportingIds,
            observedArticle = article
        )
    }

    private fun normalize(value: String): String = SpeciesOcrTypography.normalizeLabel(value)

    /** Contact updates must not turn one unresolved announcement into repeated Room queries. */
    private suspend fun pokemon(name: String): Pokemon? {
        if (!pokemonByName.containsKey(name)) pokemonByName[name] = pokemonKnowledge.getPokemonByName(name)
        return pokemonByName[name]
    }

    private data class Selection(val move: Move, val ids: List<ArticleId>)

    /**
     * A contact is intent only. A visible used-move announcement is still
     * required before spending energy or animating a completed charge move.
     */
    private fun selectedPlayerMove(pokemon: Pokemon, reading: String, at: Long, requireReady: Boolean): Selection? {
        if (reading.length < 3) return null
        val lastSpend = recentSpends.filter { it.side == ActivePokemonSide.PLAYER && it.at < at - ANNOUNCEMENT_EPISODE_NANOS }
            .maxOfOrNull { it.at } ?: Long.MIN_VALUE
        val contacts = controlEvidence.filter { it.payload is PlayerChargeButtonTouchWitnessed &&
            it.monotonicTimeNanos?.let { time -> time in (at - 15_000_000_000L)..at && time > lastSpend } == true }
            .sortedByDescending { it.monotonicTimeNanos }
        for (contact in contacts) {
            val contactAt = contact.monotonicTimeNanos!!
            val touch = contact.payload as PlayerChargeButtonTouchWitnessed
            val active = activeSpeciesHistory.filter { (it.payload as ActivePokemonSpeciesWitnessed).side == ActivePokemonSide.PLAYER &&
                (it.monotonicTimeNanos ?: Long.MAX_VALUE) <= contactAt }.maxByOrNull { it.monotonicTimeNanos!! }
                ?: continue
            if (normalize((active.payload as ActivePokemonSpeciesWitnessed).speciesName) != normalize(pokemon.name)) continue
            if (activeSpeciesHistory.any { appearance ->
                    val value = appearance.payload as ActivePokemonSpeciesWitnessed
                    value.side == ActivePokemonSide.PLAYER && normalize(value.speciesName) != normalize(pokemon.name) &&
                        appearance.monotonicTimeNanos?.let { it in contactAt..at } == true
                }) continue
            if (controlEvidence.any { evidence ->
                    val faint = evidence.payload as? ActivePokemonFaintedWitnessed
                    faint?.side == ActivePokemonSide.PLAYER && evidence.monotonicTimeNanos?.let { it in contactAt..at } == true
                }) continue
            val labelArticle = controlEvidence.filter {
                (it.payload as? PlayerChargeMoveLabelRead)?.slot == touch.slot &&
                    it.monotonicTimeNanos?.let { t -> t in (contactAt - 2_000_000_000L)..contactAt } == true
            }.maxByOrNull { it.monotonicTimeNanos!! } ?: continue
            // The latest blank/ambiguous read deliberately blocks stale labels during QTE.
            val label = labelArticle.payload as PlayerChargeMoveLabelRead
            val move = bestMoveMatches(normalize(label.text), pokemon.chargedMoves).singleOrNull()?.move ?: continue
            if (MoveTextAliases.normalizedNames(move.name).none { reading == it || it.startsWith(reading) } &&
                bestMoveMatches(reading, listOf(move)).isEmpty()) continue
            val ready = controlEvidence.filter { it.payload is GetReadyWitnessed &&
                it.monotonicTimeNanos?.let { t -> t in contactAt..minOf(at, contactAt + 2_000_000_000L) } == true
            }.minByOrNull { it.monotonicTimeNanos!! }
            if (requireReady && ready == null) continue
            if (requireReady && controlEvidence.any { it.payload is AttackIncoming &&
                    it.monotonicTimeNanos?.let { t -> t in contactAt..at } == true }) continue
            return Selection(move, listOf(active.id, contact.id, labelArticle.id) + listOfNotNull(ready?.id))
        }
        return null
    }

    private data class MoveMatch(val move: Move, val edits: Int)

    /** Compare the entire reading only with legal moves; clipped prefixes never select a move. */
    private fun bestMoveMatches(reading: String, moves: List<Move>): List<MoveMatch> {
        val matches = moves.distinctBy { normalize(it.name) }.mapNotNull { move ->
            MoveTextAliases.normalizedNames(move.name).mapNotNull { name ->
                when {
                    reading == name -> MoveMatch(move, 0)
                    reading.length < 6 || name.length < 6 -> null
                    reading.startsWith(name) || name.startsWith(reading) -> null
                    SpeciesOcrTypography.glyphRepairCost(reading, name) != null -> MoveMatch(move, 1)
                    singleCharacterDifference(reading, name) -> MoveMatch(move, 1)
                    else -> null
                }
            }.minByOrNull { it.edits }
        }
        val best = matches.minOfOrNull { it.edits } ?: return emptyList()
        return matches.filter { it.edits == best }
    }

    private fun singleCharacterDifference(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        var a = 0; var b = 0; var edits = 0
        while (a < left.length && b < right.length) {
            if (left[a] == right[b]) { a++; b++; continue }
            if (++edits > 1) return false
            when {
                left.length > right.length -> a++
                right.length > left.length -> b++
                else -> { a++; b++ }
            }
        }
        return edits + (left.length - a) + (right.length - b) == 1
    }

    private companion object {
        const val ANNOUNCEMENT_SOURCE = "ANNOUNCEMENT_WITNESS"
        const val ANNOUNCEMENT_EPISODE_NANOS = 4_000_000_000L
        val USED_MOVE_PATTERN = Regex("^\\s*(?:(.+?)\\s+)?used\\s+(.+?)[!.]*\\s*$", RegexOption.IGNORE_CASE)
        // Some small crops lose both word gaps: the October 13 record read "salusedDischargo".
        val COMPACT_USED_MOVE_PATTERN = Regex("^\\s*(\\p{L}{2,24})used([\\p{L}\\p{N}|$@€!]{6,})[!.]*\\s*$", RegexOption.IGNORE_CASE)
    }
}
