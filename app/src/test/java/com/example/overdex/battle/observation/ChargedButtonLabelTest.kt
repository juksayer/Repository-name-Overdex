package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class ChargedButtonLabelTest {
    @Test fun `matchup hint in padding does not obscure the move or manufacture a move`() {
        assertTrue(MoveTextAliases.matchesButtonLabel("WEATHER BALL\nNOT VERY EFFECTIVE", "Weather Ball (Ice)"))
        assertTrue(MoveTextAliases.matchesButtonLabel("SEED BOMB\nSUPER EFFECTIVE", "Seed Bomb"))
        assertTrue(MoveTextAliases.matchesButtonLabel("SEED BOMB\nCIDER EFFECTIVE", "Seed Bomb"))
        assertTrue(MoveTextAliases.matchesButtonLabel("WEATHER BALL\nNOT VERY EFFECTIIVE", "Weather Ball (Ice)"))
        assertTrue(MoveTextAliases.matchesButtonLabel("FARTH POWER", "Earth Power"))
        assertTrue(MoveTextAliases.matchesButtonLabel("5EED B0MB", "Seed Bomb"))
        assertTrue(MoveTextAliases.matchesButtonLabel("SUPERPOWER", "Superpower"))
        assertFalse(MoveTextAliases.matchesButtonLabel("SUPER EFFECTIVE", "Superpower"))
        assertFalse(MoveTextAliases.matchesButtonLabel("EAR", "Earthquake"))
        assertFalse(MoveTextAliases.matchesButtonLabel("EAR\nSUPER EFFECTIVE", "Earthquake"))
        assertFalse(MoveTextAliases.matchesButtonLabel("EARTH POWE", "Earth Power"))
        assertFalse(MoveTextAliases.matchesButtonLabel("FARTH POWER", "Earthquake"))
        assertFalse(MoveTextAliases.matchesButtonLabel("ZARTH POWER", "Earth Power"))
    }
}
