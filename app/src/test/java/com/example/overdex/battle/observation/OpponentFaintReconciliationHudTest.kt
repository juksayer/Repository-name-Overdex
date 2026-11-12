package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.FastMoveIdentified
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.model.PokemonType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpponentFaintReconciliationHudTest {
    private val presentation = DroidballOverlayPresentation

    @Before fun resetBefore() = presentation.reset()
    @After fun resetAfter() = presentation.reset()

    @Test fun `corrected faint restores active moves and transfers the faded cell without changing resources`() {
        species("Talonflame", 663, 100)
        species("Carbink", 703, 200)
        presentation.recordOpponentFastMove(FastMoveIdentified(
            ActivePokemonSide.OPPONENT, "Carbink", "Rock Throw", 1_000_000_000L,
            null, 0, "AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE",
        ))
        presentation.recordOpponentChargedMove("Carbink", "Rock Slide")
        presentation.recordOpponentResources(OpponentBattleResourceCountMeasured(
            OpponentBattleResource.POKE_BALLS, 2, 3), 210)
        presentation.recordOpponentResources(OpponentBattleResourceCountMeasured(
            OpponentBattleResource.SHIELDS, 1, 2), 210)
        presentation.showBattleHud(userRequested = true)
        val activeMoves = presentation.activeOpponentMovePossibilities.value
        val resources = presentation.opponentResources.value
        val identities = presentation.opponentSpecies.value

        presentation.reconcileOpponentFaints(setOf("Carbink"), 220)
        assertTrue(cell("Carbink").isFainted)
        assertNull(presentation.activeOpponentMovePossibilities.value)

        presentation.reconcileOpponentFaints(setOf("Talonflame"), 190)
        assertTrue(cell("Talonflame").isFainted)
        assertFalse(cell("Carbink").isFainted)
        assertEquals(activeMoves, presentation.activeOpponentMovePossibilities.value)
        assertEquals(resources, presentation.opponentResources.value)
        assertEquals(identities, presentation.opponentSpecies.value.map { it.copy(isFainted = false) })
        assertTrue(presentation.battleBadgeSeen.value)
        assertTrue(presentation.expanded.value)
        assertEquals(DroidballOverlayMode.BATTLE_HUD, presentation.mode.value)
    }

    @Test fun `late historical faint cannot select the historical opponent or regress badge source time`() {
        species("Carbink", 703, 100)
        species("Quagsire", 195, 300)
        val current = presentation.activeOpponentMovePossibilities.value
        val before = presentation.opponentBadgeContent.value

        presentation.reconcileOpponentFaints(setOf("Carbink"), 200)

        assertTrue(cell("Carbink").isFainted)
        assertFalse(cell("Quagsire").isFainted)
        assertEquals(current, presentation.activeOpponentMovePossibilities.value)
        assertEquals("Quagsire", presentation.activeOpponentMovePossibilities.value?.speciesName)
        assertEquals(before.revision + 1, presentation.opponentBadgeContent.value.revision)
        assertEquals(300L, presentation.opponentBadgeContent.value.changedAtNanos)
    }

    @Test fun `equivalent complete snapshots do not repeatedly invalidate the retained badge`() {
        species("Carbink", 703, 100)
        presentation.reconcileOpponentFaints(setOf("CARBINK"), 110)
        val content = presentation.opponentBadgeContent.value
        val roster = presentation.opponentSpecies.value

        presentation.reconcileOpponentFaints(setOf(" Carbink ", "", "carbink"), 120)

        assertEquals(content, presentation.opponentBadgeContent.value)
        assertEquals(roster, presentation.opponentSpecies.value)
        assertNull(presentation.activeOpponentMovePossibilities.value)
    }

    @Test fun `faint resolution before identity remains applied until a revised complete snapshot clears it`() {
        presentation.reconcileOpponentFaints(setOf("Carbink"), 100)
        species("Carbink", 703, 110)
        assertTrue(cell("Carbink").isFainted)
        assertNull(presentation.activeOpponentMovePossibilities.value)
        // Another badge read alone is not a revised faint resolution.
        species("Carbink", 703, 120)
        assertTrue(cell("Carbink").isFainted)

        presentation.reconcileOpponentFaints(emptySet(), 90)
        assertFalse(cell("Carbink").isFainted)
        assertEquals("Carbink", presentation.activeOpponentMovePossibilities.value?.speciesName)
    }

    @Test fun `reconciliation can correct compatibility marks and reset forgets the previous match`() {
        species("Carbink", 703, 100)
        presentation.markOpponentFainted("Carbink", 110)
        presentation.reconcileOpponentFaints(emptySet(), 120)
        assertFalse(cell("Carbink").isFainted)
        assertEquals("Carbink", presentation.activeOpponentMovePossibilities.value?.speciesName)

        presentation.reconcileOpponentFaints(setOf("Carbink"), 130)
        presentation.reset()
        species("Carbink", 703, 10)
        assertFalse(cell("Carbink").isFainted)
        assertEquals("Carbink", presentation.activeOpponentMovePossibilities.value?.speciesName)
    }

    @Test fun `bare and form-qualified faint identities share membership without losing the displayed form`() {
        species("Gourgeist (Average)", 711, 100)
        presentation.reconcileOpponentFaints(setOf("Gourgeist"), 110)
        assertTrue(cell("Gourgeist (Average)").isFainted)
        assertNull(presentation.activeOpponentMovePossibilities.value)
        val content = presentation.opponentBadgeContent.value

        presentation.reconcileOpponentFaints(setOf("Gourgeist (Average)"), 120)
        assertEquals(content, presentation.opponentBadgeContent.value)
        presentation.reconcileOpponentFaints(emptySet(), 130)
        assertFalse(cell("Gourgeist (Average)").isFainted)
        assertEquals("Gourgeist (Average)", presentation.activeOpponentMovePossibilities.value?.speciesName)

        presentation.markOpponentFainted("Gourgeist", 140)
        assertTrue(cell("Gourgeist (Average)").isFainted)
        assertNull(presentation.activeOpponentMovePossibilities.value)
    }

    private fun species(name: String, id: Int, at: Long) = presentation.recordOpponentSpecies(
        name, id,
        listOf("Rock Throw" to PokemonType.ROCK),
        listOf("Rock Slide" to PokemonType.ROCK),
        observedAtNanos = at,
        moveEnergy = mapOf("Rock Throw" to 5, "Rock Slide" to 45),
    )

    private fun cell(name: String) = presentation.opponentSpecies.value.single { it.speciesName == name }
}
