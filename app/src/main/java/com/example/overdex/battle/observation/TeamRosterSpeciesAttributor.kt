package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide

/** Derives a side only after a complete, independently witnessed player roster exists. */
object TeamRosterSpeciesAttributor {
    private const val REQUIRED_ROSTER_SLOTS = 3

    fun sideFor(speciesName: String, playerRoster: Collection<String>): ActivePokemonSide? {
        if (playerRoster.size < REQUIRED_ROSTER_SLOTS) return null
        return if (normalize(speciesName) in playerRoster.asSequence().map(::normalize)) {
            ActivePokemonSide.PLAYER
        } else {
            ActivePokemonSide.OPPONENT
        }
    }

    private fun normalize(value: String): String =
        value.uppercase().filter(Char::isLetterOrDigit)
}

data class EntrySpeciesAttribution(
    val side: ActivePokemonSide,
    val confidence: Float,
    val basis: String
)

/**
 * Attributes the visible `Go, <species>!` sequence even when onboarding was
 * skipped and no player roster exists. Pokémon GO presents the player's lead
 * first and the opponent's lead second. Later unseen names are provisional and
 * alternate from the last entry until badge or roster evidence can refute them.
 */
class EntryAnnouncementSideTracker {
    private val sideBySpecies = linkedMapOf<String, ActivePokemonSide>()
    private var lastSide: ActivePokemonSide? = null

    fun attribute(speciesName: String, playerRoster: Collection<String>): EntrySpeciesAttribution {
        val key = normalizeEntrySpecies(speciesName)
        val rosterKeys = playerRoster.mapTo(linkedSetOf(), ::normalizeEntrySpecies)
        val attribution = when {
            key in rosterKeys -> EntrySpeciesAttribution(ActivePokemonSide.PLAYER, 0.99f, "PLAYER_ROSTER")
            rosterKeys.size >= 3 -> EntrySpeciesAttribution(ActivePokemonSide.OPPONENT, 0.99f, "ABSENT_FROM_COMPLETE_PLAYER_ROSTER")
            sideBySpecies[key] != null -> EntrySpeciesAttribution(sideBySpecies.getValue(key), 0.90f, "PRIOR_ENTRY_ATTRIBUTION")
            ActivePokemonSide.PLAYER !in sideBySpecies.values -> EntrySpeciesAttribution(ActivePokemonSide.PLAYER, 0.92f, "OPENING_ENTRY_ORDER")
            ActivePokemonSide.OPPONENT !in sideBySpecies.values -> EntrySpeciesAttribution(ActivePokemonSide.OPPONENT, 0.92f, "OPENING_ENTRY_ORDER")
            lastSide == ActivePokemonSide.OPPONENT -> EntrySpeciesAttribution(ActivePokemonSide.PLAYER, 0.55f, "PROVISIONAL_ENTRY_SEQUENCE")
            else -> EntrySpeciesAttribution(ActivePokemonSide.OPPONENT, 0.55f, "PROVISIONAL_ENTRY_SEQUENCE")
        }
        sideBySpecies[key] = attribution.side
        lastSide = attribution.side
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
