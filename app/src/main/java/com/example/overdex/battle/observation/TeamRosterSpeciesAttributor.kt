package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide

/** Uses positive roster membership immediately; absence requires a complete roster. */
object TeamRosterSpeciesAttributor {
    private const val REQUIRED_ROSTER_SLOTS = 3

    fun sideFor(speciesName: String, playerRoster: Collection<String>): ActivePokemonSide? {
        val normalizedRoster = playerRoster.asSequence().map(::normalize).filter(String::isNotBlank).toSet()
        if (normalize(speciesName) in normalizedRoster) return ActivePokemonSide.PLAYER
        return ActivePokemonSide.OPPONENT.takeIf { normalizedRoster.size >= REQUIRED_ROSTER_SLOTS }
    }

    private fun normalize(value: String): String =
        value.uppercase().filter(Char::isLetterOrDigit)
}

data class EntrySpeciesAttribution(
    val side: ActivePokemonSide,
    val confidence: Float,
    val basis: String
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
    private val sideBySpecies = linkedMapOf<String, ActivePokemonSide>()

    /**
     * Returns a side only when the announcement carries enough context to support it.
     *
     * A newly encountered `Go, <species>!` does not reveal which trainer sent
     * it out by itself. Keeping that species unsided is preferable to assigning
     * a late opponent switch to the player after earlier announcements were missed.
     */
    fun attribute(
        speciesName: String,
        playerRoster: Collection<String>
    ): EntrySpeciesAttribution? {
        val key = normalizeEntrySpecies(speciesName)
        val rosterKeys = playerRoster.mapTo(linkedSetOf(), ::normalizeEntrySpecies)
        val attribution = when {
            key in rosterKeys -> EntrySpeciesAttribution(ActivePokemonSide.PLAYER, 0.99f, "PLAYER_ROSTER")
            rosterKeys.size >= 3 -> EntrySpeciesAttribution(ActivePokemonSide.OPPONENT, 0.99f, "ABSENT_FROM_COMPLETE_PLAYER_ROSTER")
            sideBySpecies[key] != null -> EntrySpeciesAttribution(sideBySpecies.getValue(key), 0.90f, "PRIOR_ENTRY_ATTRIBUTION")
            else -> return null
        }
        sideBySpecies[key] = attribution.side
        return attribution
    }

    fun knownSide(speciesName: String, playerRoster: Collection<String>): ActivePokemonSide? {
        TeamRosterSpeciesAttributor.sideFor(speciesName, playerRoster)?.let { return it }
        val key = normalizeEntrySpecies(speciesName)
        if (key in playerRoster.map(::normalizeEntrySpecies)) return ActivePokemonSide.PLAYER
        return sideBySpecies[key]
    }
}

private fun normalizeEntrySpecies(value: String): String =
    value.uppercase().filter(Char::isLetterOrDigit)
