package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamRosterSpeciesAttributorTest {
    private val roster = setOf("Chandelure", "Camerupt", "Gourgeist")

    @Test fun `member of a complete player roster is player`() {
        assertEquals(ActivePokemonSide.PLAYER, TeamRosterSpeciesAttributor.sideFor("Camerupt", roster))
    }

    @Test fun `species absent from a complete player roster is opponent`() {
        assertEquals(ActivePokemonSide.OPPONENT, TeamRosterSpeciesAttributor.sideFor("Turtonator", roster))
    }

    @Test fun `incomplete roster does not attribute a side`() {
        assertEquals(null, TeamRosterSpeciesAttributor.sideFor("Turtonator", setOf("Camerupt", "Gourgeist")))
    }
}
