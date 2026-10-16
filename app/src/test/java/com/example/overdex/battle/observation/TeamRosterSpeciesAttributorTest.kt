package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamRosterSpeciesAttributorTest {
    private val roster = setOf("Chandelure", "Camerupt", "Gourgeist")

    @Test fun `Vaporeon entry following Sealeo faint uses that opponent vacancy without roster exclusion`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.observeFaint(ActivePokemonSide.OPPONENT, "Sealeo", 76_560_000_000L, "sealeo-ko")
        val entry = tracker.attribute("Vaporeon", roster, entryAtNanos = 76_560_000_000L)!!
        assertEquals(ActivePokemonSide.OPPONENT, entry.side)
        assertEquals("ENTRY_AFTER_SINGLE_SIDE_FAINT", entry.basis)
        assertEquals(listOf("sealeo-ko"), entry.evidenceArticleIds)
        assertEquals(null, tracker.attribute("Sealeo", roster, entryAtNanos = 76_560_000_000L))
    }

    @Test fun `old or simultaneous faint contexts do not assign unknown entries`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.observeFaint(ActivePokemonSide.OPPONENT, "Sealeo", 10_000_000_000L, "opponent-ko")
        assertEquals(null, tracker.attribute("Vaporeon", roster, entryAtNanos = 9_000_000_000L))
        assertEquals(null, tracker.attribute("Vaporeon", roster, entryAtNanos = 19_000_000_000L))
        assertEquals(null, tracker.attribute("Vaporeon", roster)) // unsided charged text is not an entry
        tracker.observeFaint(ActivePokemonSide.PLAYER, "Camerupt", 10_200_000_000L, "player-ko")
        assertEquals(null, tracker.attribute("Vaporeon", roster, entryAtNanos = 10_500_000_000L))
        tracker.reset()
        assertEquals(null, tracker.attribute("Vaporeon", roster, entryAtNanos = 10_500_000_000L))
    }

    @Test fun `explicit player membership still wins over an opposing faint`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.observeFaint(ActivePokemonSide.OPPONENT, "Sealeo", 10_000_000_000L, "opponent-ko")
        assertEquals(ActivePokemonSide.PLAYER,
            tracker.attribute("Camerupt", roster, entryAtNanos = 10_500_000_000L)?.side)
    }

    @Test fun `unverified saved team cannot turn an unknown entry into an opponent`() {
        val tracker = EntryAnnouncementSideTracker()
        val stale = listOf("Samurott", "Ceruledge", "Machamp")
        assertEquals(null, tracker.attribute("Abomasnow", stale))
        tracker.observeBadge(ActivePokemonSide.PLAYER, "Abomasnow", "player-badge")
        val resolved = tracker.attribute("Abomasnow", stale)
        assertEquals(ActivePokemonSide.PLAYER, resolved?.side)
        assertEquals(listOf("player-badge"), resolved?.evidenceArticleIds)
    }

    @Test fun `independent badges beat even a complete roster exclusion and preserve mirrors`() {
        val tracker = EntryAnnouncementSideTracker()
        val stale = listOf("Samurott", "Ceruledge", "Machamp")
        tracker.observeBadge(ActivePokemonSide.PLAYER, "Gourgeist", "player")
        assertEquals(ActivePokemonSide.PLAYER, tracker.attribute("Gourgeist", stale, true)?.side)
        tracker.observeBadge(ActivePokemonSide.OPPONENT, "Gourgeist", "opponent")
        assertEquals(null, tracker.attribute("Gourgeist", stale, true))
    }

    @Test fun `opponent badge beats a saved player member without erasing the badge on reset of assumptions`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.attribute("Gourgeist", roster)
        tracker.observeBadge(ActivePokemonSide.OPPONENT, "Gourgeist", "opponent")
        tracker.discardRosterAssumptions()
        assertEquals(ActivePokemonSide.OPPONENT, tracker.attribute("Gourgeist", roster)?.side)
        tracker.reset()
        assertEquals(null, tracker.attribute("Gourgeist", emptyList()))
    }

    @Test fun `member of a complete player roster is player`() {
        assertEquals(ActivePokemonSide.PLAYER, TeamRosterSpeciesAttributor.sideFor("Camerupt", roster))
    }

    @Test fun `species absent from a complete player roster is opponent`() {
        assertEquals(ActivePokemonSide.OPPONENT, TeamRosterSpeciesAttributor.sideFor("Turtonator", roster))
    }

    @Test fun `GO name without catalogue form label still belongs to the player`() {
        val configured = listOf("Turtonator", "Camerupt", "Gourgeist (Average)")
        assertEquals(ActivePokemonSide.PLAYER, TeamRosterSpeciesAttributor.sideFor("Gourgeist", configured))
        assertEquals(ActivePokemonSide.PLAYER, EntryAnnouncementSideTracker().attribute("Gourgeist", configured)?.side)
        assertEquals(ActivePokemonSide.OPPONENT, TeamRosterSpeciesAttributor.sideFor("Sealeo", configured))
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
            tracker.attribute("Vaporeon", roster, allowRosterExclusion = true)?.side
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
