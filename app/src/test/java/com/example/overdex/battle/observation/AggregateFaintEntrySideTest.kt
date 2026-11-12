package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.*
import org.junit.Test

class AggregateFaintEntrySideTest {
    @Test fun `historical aggregate faint does not assign an unrelated incoming species to that side`() {
        for (side in ActivePokemonSide.entries) {
            val tracker = EntryAnnouncementSideTracker()
            tracker.observeBadge(side, "Camerupt", "current", 20 * SECOND)
            for (name in listOf("Turtonator", "Gourgeist")) tracker.observeFaint(
                side, name, 22 * SECOND, "aggregate-$name", "${side.name}_POKE_BALL_COUNT_DECREASE_AGGREGATE")
            assertNull("An unknown entry may belong to the other trainer; no fresh vacancy was observed",
                tracker.attribute("Pelipper", emptyList(), entryAtNanos = 23 * SECOND))
            assertEquals("The independent current badge still establishes its own side", side,
                tracker.attribute("Camerupt", emptyList(), entryAtNanos = 23 * SECOND)?.side)
        }
    }

    @Test fun `aggregate history cannot replace an independently timed other-side vacancy`() {
        val tracker = EntryAnnouncementSideTracker()
        tracker.observeFaint(ActivePokemonSide.OPPONENT, "Pelipper", 21 * SECOND, "individual",
            "OPPONENT_POKE_BALL_COUNT_DECREASE")
        tracker.observeFaint(ActivePokemonSide.PLAYER, "Turtonator", 22 * SECOND, "aggregate-one",
            "PLAYER_POKE_BALL_COUNT_DECREASE_AGGREGATE")
        tracker.observeFaint(ActivePokemonSide.PLAYER, "Gourgeist", 22 * SECOND, "aggregate-two",
            "PLAYER_POKE_BALL_COUNT_DECREASE_AGGREGATE")
        val entry = tracker.attribute("Stunfisk", emptyList(), entryAtNanos = 23 * SECOND)
        assertEquals(ActivePokemonSide.OPPONENT, entry?.side)
        assertEquals("ENTRY_AFTER_SINGLE_SIDE_FAINT", entry?.basis)
        assertEquals(listOf("individual"), entry?.evidenceArticleIds)
    }

    private companion object { const val SECOND = 1_000_000_000L }
}
