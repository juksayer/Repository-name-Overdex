package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide

/** Uses positive roster membership immediately; absence requires a complete roster. */
object TeamRosterSpeciesAttributor {
    private const val REQUIRED_ROSTER_SLOTS = 3

    fun sideFor(speciesName: String, playerRoster: Collection<String>): ActivePokemonSide? {
        val normalizedRoster = playerRoster.asSequence().map(::speciesKey).filter(String::isNotBlank).toSet()
        if (speciesKey(speciesName) in normalizedRoster) return ActivePokemonSide.PLAYER
        return ActivePokemonSide.OPPONENT.takeIf { normalizedRoster.size >= REQUIRED_ROSTER_SLOTS }
    }

    /** GO's announcement omits catalogue form labels such as Gourgeist (Average). */
    fun speciesKey(value: String): String =
        value.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").uppercase().filter(Char::isLetterOrDigit)
}

data class EntrySpeciesAttribution(
    val side: ActivePokemonSide,
    val confidence: Float,
    val basis: String,
    val evidenceArticleIds: List<String> = emptyList(),
)

/** Routes only a clear opening cry match; it never turns audio into identity. */
class OpeningCrySideGate {
    private var matchedLeadCryCount = 0

    fun reset() {
        matchedLeadCryCount = 0
    }

    fun sidesFor(
        rankedCandidates: List<Pair<Int, Float>>,
        playerLeadSpeciesId: Int?
    ): List<ActivePokemonSide> {
        val leadId = playerLeadSpeciesId ?: return ActivePokemonSide.entries
        val first = rankedCandidates.firstOrNull() ?: return ActivePokemonSide.entries
        val secondScore = rankedCandidates.getOrNull(1)?.second ?: 0f
        val clearLeadMatch = first.first == leadId &&
            first.second >= MINIMUM_SIMILARITY &&
            first.second - secondScore >= MINIMUM_MARGIN
        if (!clearLeadMatch) return ActivePokemonSide.entries

        matchedLeadCryCount += 1
        return when (matchedLeadCryCount) {
            1 -> listOf(ActivePokemonSide.PLAYER)
            2 -> listOf(ActivePokemonSide.OPPONENT) // opening mirror match
            else -> ActivePokemonSide.entries
        }
    }

    private companion object {
        const val MINIMUM_SIMILARITY = 0.60f
        const val MINIMUM_MARGIN = 0.03f
    }
}

/**
 * Attributes a visible `Go, <species>!` from independent roster or prior-side
 * evidence. Capture can begin after an earlier announcement has already left
 * the screen, so observed arrival order is never safe side evidence.
 */
class EntryAnnouncementSideTracker {
    private data class Vacancy(val species: String?, val atNanos: Long, val articleId: String)
    private val sideBySpecies = linkedMapOf<String, ActivePokemonSide>()
    private val witnessedSides = linkedMapOf<String, MutableMap<ActivePokemonSide, String>>()
    private val vacancies = linkedMapOf<ActivePokemonSide, Vacancy>()

    @Synchronized
    fun observeFaint(side: ActivePokemonSide, speciesName: String?, atNanos: Long, articleId: String) {
        if (atNanos >= (vacancies[side]?.atNanos ?: Long.MIN_VALUE)) {
            vacancies[side] = Vacancy(speciesName?.let(::normalizeEntrySpecies), atNanos, articleId)
        }
    }

    /** Only independent, side-located badge readings belong here, never our own inferences. */
    @Synchronized
    fun observeBadge(side: ActivePokemonSide, speciesName: String, articleId: String) {
        witnessedSides.getOrPut(normalizeEntrySpecies(speciesName)) { linkedMapOf() }[side] = articleId
    }

    @Synchronized
    fun reset() {
        sideBySpecies.clear()
        witnessedSides.clear()
        vacancies.clear()
    }

    @Synchronized
    fun discardRosterAssumptions() { sideBySpecies.clear() }

    /**
     * Returns a side only when the announcement carries enough context to support it.
     *
     * A newly encountered `Go, <species>!` does not reveal which trainer sent
     * it out by itself. Keeping that species unsided is preferable to assigning
     * a late opponent switch to the player after earlier announcements were missed.
     */
    @Synchronized
    fun attribute(
        speciesName: String,
        playerRoster: Collection<String>,
        allowRosterExclusion: Boolean = false,
        entryAtNanos: Long? = null,
    ): EntrySpeciesAttribution? {
        val key = normalizeEntrySpecies(speciesName)
        val sides = witnessedSides[key].orEmpty()
        // Both trainers can own the same species. A shared announcement has no
        // direction, even when that species is also in the saved player team.
        if (sides.size > 1) return null
        sides.entries.singleOrNull()?.let { (side, id) ->
            return EntrySpeciesAttribution(side, 0.99f, "SIDE_LOCATED_SPECIES_BADGE", listOf(id))
        }
        val rosterKeys = playerRoster.mapTo(linkedSetOf(), ::normalizeEntrySpecies)
        val attribution = when {
            key in rosterKeys -> EntrySpeciesAttribution(ActivePokemonSide.PLAYER, 0.99f, "PLAYER_ROSTER")
            allowRosterExclusion && rosterKeys.size >= 3 -> EntrySpeciesAttribution(ActivePokemonSide.OPPONENT, 0.99f, "ABSENT_FROM_WITNESSED_PLAYER_ROSTER")
            sideBySpecies[key] != null -> EntrySpeciesAttribution(sideBySpecies.getValue(key), 0.90f, "PRIOR_ENTRY_ATTRIBUTION")
            else -> {
                // A clean new name immediately following one side's faint can
                // fill that vacancy without treating an unverified saved roster
                // as complete. Keep the faint as an explicit inference basis.
                // Do not guess after a double KO or resurrect the same species.
                val at = entryAtNanos ?: return null
                val recent = vacancies.filterValues { at - it.atNanos in -500_000_000L..8_000_000_000L }
                val (side, vacancy) = recent.entries.singleOrNull() ?: return null
                if (key == vacancy.species) return null
                EntrySpeciesAttribution(side, 0.90f, "ENTRY_AFTER_SINGLE_SIDE_FAINT", listOf(vacancy.articleId))
            }
        }
        sideBySpecies[key] = attribution.side
        return attribution
    }

    fun knownSide(speciesName: String, playerRoster: Collection<String>): ActivePokemonSide? =
        attribute(speciesName, playerRoster)?.side
}

/** These witnesses read a particular trainer's badge, independent of saved teams. */
internal fun isSideLocatedSpeciesSource(sourceId: String): Boolean = sourceId in setOf(
    "PLAYER_SPECIES_OVERLAY_PIPELINE", "OPPONENT_SPECIES_OVERLAY_PIPELINE",
    "PLAYER_SPECIES_WITNESS", "SPECIES_WITNESS",
)

private fun normalizeEntrySpecies(value: String): String =
    TeamRosterSpeciesAttributor.speciesKey(value)
