package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertEquals
import org.junit.Test

class TeamRosterSpeciesAttributorTest {
    private val roster = setOf("Chandelure", "Camerupt", "Gourgeist")

    @Test fun `Obstagoon badge fills Lucario vacancy before opposing Camerupt enters`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.observeFaint(ActivePokemonSide.PLAYER, "Lucario", 123_152_000_000L, "lucario-ko")
        tracker.observeBadge(ActivePokemonSide.PLAYER, "Obstagoon", "obstagoon-badge", 123_330_000_000L)
        assertEquals(null, tracker.attribute("Camerupt", listOf("Greninja", "Lucario", "Obstagoon"),
            entryAtNanos = 125_791_000_000L))
        tracker.observeBadge(ActivePokemonSide.OPPONENT, "Camerupt", "camerupt-badge", 126_839_000_000L)
        assertEquals(ActivePokemonSide.OPPONENT, tracker.attribute("Camerupt", emptyList())?.side)
    }

    @Test fun `delayed faint delivery cannot reopen a vacancy already filled in source time`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.observeBadge(ActivePokemonSide.PLAYER, "Obstagoon", "obstagoon-badge", 123_330_000_000L)
        tracker.observeFaint(ActivePokemonSide.PLAYER, "Lucario", 123_152_000_000L, "lucario-ko")
        assertEquals(null, tracker.attribute("Camerupt", emptyList(), entryAtNanos = 125_791_000_000L))
    }

    @Test fun `old badge or lingering fainted species does not fill a new vacancy`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.observeBadge(ActivePokemonSide.OPPONENT, "Vaporeon", "earlier-appearance", 5_000_000_000L)
        tracker.observeFaint(ActivePokemonSide.OPPONENT, "Sealeo", 10_000_000_000L, "sealeo-ko")
        tracker.observeBadge(ActivePokemonSide.OPPONENT, "Sealeo", "lingering-badge", 10_100_000_000L)
        val entry = tracker.attribute("Sneasel", emptyList(), entryAtNanos = 11_000_000_000L)
        assertEquals(ActivePokemonSide.OPPONENT, entry?.side)
        assertEquals("ENTRY_AFTER_SINGLE_SIDE_FAINT", entry?.basis)
    }

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
        tracker.observeBadge(ActivePokemonSide.PLAYER, "Abomasnow", "player-badge", 0L)
        val resolved = tracker.attribute("Abomasnow", stale)
        assertEquals(ActivePokemonSide.PLAYER, resolved?.side)
        assertEquals(listOf("player-badge"), resolved?.evidenceArticleIds)
    }

    @Test fun `independent badges beat even a complete roster exclusion and preserve mirrors`() {
        val tracker = EntryAnnouncementSideTracker()
        val stale = listOf("Samurott", "Ceruledge", "Machamp")
        tracker.observeBadge(ActivePokemonSide.PLAYER, "Gourgeist", "player", 0L)
        assertEquals(ActivePokemonSide.PLAYER, tracker.attribute("Gourgeist", stale, true)?.side)
        tracker.observeBadge(ActivePokemonSide.OPPONENT, "Gourgeist", "opponent", 0L)
        assertEquals(null, tracker.attribute("Gourgeist", stale, true))
    }

    @Test fun `opponent badge beats a saved player member without erasing the badge on reset of assumptions`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.attribute("Gourgeist", roster)
        tracker.observeBadge(ActivePokemonSide.OPPONENT, "Gourgeist", "opponent", 0L)
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
