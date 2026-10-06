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

    @Test fun `witnessed lead attributes player before remaining slots are known`() {
        assertEquals(ActivePokemonSide.PLAYER, TeamRosterSpeciesAttributor.sideFor("Camerupt", setOf("Camerupt")))
    }

    @Test fun `entry order alone does not assign either opening combatant`() {
        val tracker = EntryAnnouncementSideTracker()

        assertEquals(null, tracker.attribute("Pikachu", emptySet()))
        assertEquals(null, tracker.attribute("Sneasel", emptySet()))
    }

    @Test fun `known entry keeps its side when it returns`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.attribute("Pikachu", setOf("Pikachu"))

        assertEquals(ActivePokemonSide.PLAYER, tracker.attribute("Pikachu", emptySet())?.side)
    }

    @Test fun `late first observed announcement is not mislabeled as player`() {
        val tracker = EntryAnnouncementSideTracker()

        assertEquals(null, tracker.attribute("Vaporeon", emptySet()))
    }

    @Test fun `complete roster still attributes a late announcement`() {
        val tracker = EntryAnnouncementSideTracker()

        assertEquals(
            ActivePokemonSide.OPPONENT,
            tracker.attribute("Vaporeon", roster)?.side
        )
    }

    @Test fun `clear player lead cry routes first opening cue to player and mirror to opponent`() {
        val gate = OpeningCrySideGate()
        val candidates = listOf(776 to 0.84f, 91 to 0.71f)

        assertEquals(listOf(ActivePokemonSide.PLAYER), gate.sidesFor(candidates, 776))
        assertEquals(listOf(ActivePokemonSide.OPPONENT), gate.sidesFor(candidates, 776))
    }

    @Test fun `ambiguous cry continues checking both sides`() {
        val gate = OpeningCrySideGate()

        assertEquals(
            ActivePokemonSide.entries,
            gate.sidesFor(listOf(776 to 0.70f, 91 to 0.69f), 776)
        )
    }
}
