package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.overdex.battle.team.CurrentBattleTeam
import com.example.overdex.battle.team.CurrentBattleTeamMember
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.components.HandheldNavigationController
import com.example.overdex.ui.screens.CurrentTeamOverview
import com.example.overdex.ui.screens.MoveSelectionStep
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Uses small CRT viewports so a selected row can really cross the visible edge. */
class MoveSelectionNavigationDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val moves = (1..11).map {
        Move("Move $it", PokemonType.WATER, damage = 40, energy = 35, isFast = false)
    }

    @Test fun moveHighlightStaysFullyVisibleWhileBrowsingBothWays() {
        val nav = HandheldNavigationController(itemCount = { moves.size })
        compose.setContent {
            Box(Modifier.size(340.dp, 190.dp)) {
                MoveSelectionStep("SELECT FAST MOVE", moves, emptySet(), nav.selectedIndex) { }
            }
        }
        assertSelectedRowFullyVisible()
        repeat(moves.lastIndex) {
            compose.runOnIdle { nav.moveDown() }
            assertSelectedRowFullyVisible()
        }
        repeat(moves.lastIndex) {
            compose.runOnIdle { nav.moveUp() }
            assertSelectedRowFullyVisible()
        }
    }

    @Test fun reopeningAnEquippedMoveAndReachingSaveRevealsTheirRows() {
        val nav = HandheldNavigationController(initialIndex = 8, itemCount = { moves.size + 1 })
        compose.setContent {
            Box(Modifier.size(340.dp, 210.dp)) {
                MoveSelectionStep("SELECT CHARGED MOVES (1-2)", moves, setOf("Move 9"),
                    nav.selectedIndex, showNext = true, nextLabel = "SAVE MOVES") { }
            }
        }
        compose.onNodeWithText("Move 9").assertIsDisplayed()
        assertSelectedRowFullyVisible()
        repeat(3) { compose.runOnIdle { nav.moveDown() } }
        compose.onNodeWithText("▶ SAVE MOVES").assertIsDisplayed()
        assertSelectedRowFullyVisible()
        compose.runOnIdle { nav.setIndex(0) }
        compose.onNodeWithText("Move 1").assertIsDisplayed()
        assertSelectedRowFullyVisible()
    }

    @Test fun currentTeamRevealsTheLowerActionsAndReturnsToTheFirstMember() {
        val selected = mutableIntStateOf(0)
        val team = CurrentBattleTeam((1..3).map {
            CurrentBattleTeamMember(it, it, "Pokémon $it", "Fast move", listOf("Charged one", "Charged two"))
        })
        compose.setContent {
            Box(Modifier.size(340.dp, 150.dp)) {
                CurrentTeamOverview(team, selected.intValue, showLaunch = true, showLibrary = true)
            }
        }
        compose.runOnIdle { selected.intValue = 5 }
        assertTextFullyVisible("SAVED TEAMS / SAVE THIS TEAM")
        compose.runOnIdle { selected.intValue = 0 }
        assertTextFullyVisible("1. Pokémon 1")
    }

    @Test fun loadingCurrentTeamRevealsTheMemberWithoutAnotherNavigationTap() {
        val team = mutableStateOf(CurrentBattleTeam())
        compose.setContent {
            Box(Modifier.size(340.dp, 75.dp)) {
                CurrentTeamOverview(team.value, selectedIndex = 0, showLibrary = true)
            }
        }
        assertTextFullyVisible("ADD POKEMON")
        compose.runOnIdle {
            team.value = CurrentBattleTeam(listOf(
                CurrentBattleTeamMember(1, 1, "Pokémon 1", "Fast move", listOf("Charged one")),
            ))
        }
        assertTextFullyVisible("1. Pokémon 1")
    }

    private fun assertSelectedRowFullyVisible() {
        val row = compose.onNode(isSelected()).assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        assertTrue("Selected row starts above the visible list: $row in $viewport", row.top >= viewport.top - 1.dp)
        assertTrue("Selected row ends below the visible list: $row in $viewport", row.bottom <= viewport.bottom + 1.dp)
    }

    private fun assertTextFullyVisible(text: String) {
        val row = compose.onNodeWithText(text).assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        assertTrue("Action starts above the visible list: $row in $viewport", row.top >= viewport.top - 1.dp)
        assertTrue("Action ends below the visible list: $row in $viewport", row.bottom <= viewport.bottom + 1.dp)
    }
}
