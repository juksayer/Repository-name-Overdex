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
