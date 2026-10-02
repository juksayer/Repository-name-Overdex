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

    @Test fun `entry order attributes both opening combatants without a configured team`() {
        val tracker = EntryAnnouncementSideTracker()

        assertEquals(ActivePokemonSide.PLAYER, tracker.attribute("Turtonator", emptySet()).side)
        assertEquals(ActivePokemonSide.OPPONENT, tracker.attribute("Sneasel", emptySet()).side)
        assertEquals(ActivePokemonSide.PLAYER, tracker.attribute("Camerupt", emptySet()).side)
    }

    @Test fun `known entry keeps its side when it returns`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.attribute("Turtonator", emptySet())
        tracker.attribute("Sneasel", emptySet())

        assertEquals(ActivePokemonSide.OPPONENT, tracker.attribute("Sneasel", emptySet()).side)
    }
}
