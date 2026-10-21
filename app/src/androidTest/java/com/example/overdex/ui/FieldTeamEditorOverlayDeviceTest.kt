package com.example.overdex.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.overdex.battle.team.*
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.*
import com.example.overdex.ui.components.FieldTeamEditorOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FieldTeamEditorOverlayDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pokemon = Pokemon(26, "Raichu", listOf(PokemonType.ELECTRIC), "Kanto",
        fastMoves = listOf(Move("Volt Switch", PokemonType.ELECTRIC, 12, 16, true)),
        chargedMoves = listOf("Thunder Punch", "Wild Charge").map { Move(it, PokemonType.ELECTRIC, 50, 40, false) })
    private val knowledge = object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String) = pokemon
        override suspend fun getPokemonById(id: Int) = pokemon
        override suspend fun getAllSpeciesNames() = setOf("Raichu")
    }
    private val saved = CurrentBattleTeam((1..3).map {
        CurrentBattleTeamMember(it, 26, "Raichu", "Volt Switch", listOf("Thunder Punch"), "card-$it")
    })
    @After fun close() { scope.cancel() }

    @Test fun scannedTeamCanChooseMovesInAllThreeSlotsThenApply() {
        var result: CurrentBattleTeam? = null
        val editor = FieldTeamEditor(knowledge, scope, { true }) { result = it }
        compose.runOnIdle { editor.openScan(List(3) { "Raichu" }) }
        compose.setContent { FieldTeamEditorOverlay(editor, emptyList(), 330.dp) {} }
        compose.onNodeWithText("USE THIS TEAM").assertIsDisplayed().assertIsNotEnabled()
        repeat(3) { index ->
            compose.onNodeWithText("${index + 1}").performClick()
            compose.onNodeWithText("○ Volt Switch").performScrollTo().performClick()
            compose.onNodeWithText("○ Thunder Punch").performScrollTo().performClick()
        }
        compose.onNodeWithText("3/3 ready").assertIsDisplayed()
        compose.onNodeWithText("USE THIS TEAM").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("Volt Switch", "Volt Switch", "Volt Switch"), result!!.members.map { it.fastMoveName })
            assertTrue(result!!.members.all { it.ownedPokemonId == null })
        }
    }

    @Test fun savedLineupCanBeLoadedAndRescanRemainsAvailable() {
        var rescanned = false
        val editor = FieldTeamEditor(knowledge, scope, { true }) {}
        compose.runOnIdle { editor.openScan(List(3) { null }) }
        compose.setContent { FieldTeamEditorOverlay(editor, listOf(SavedBattleTeam("saved", "Electric team", saved)),
            330.dp) { rescanned = true } }
        compose.onNodeWithText("SAVED TEAMS").performClick()
        compose.onNodeWithText("Electric team").performClick()
        compose.onNodeWithText("3/3 ready").assertIsDisplayed()
        compose.runOnIdle { assertEquals("card-1", editor.draft.value!!.slots[0].ownedPokemonId) }
        compose.onNodeWithText("RESCAN").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(rescanned); assertNull(editor.draft.value) }
    }
}
