package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.overdex.diagnostics.DiagnosticLogger
import com.example.overdex.model.OwnedPokemonField
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.InstrumentTree
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.components.DirectoryTree
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DirectoryTreeNavigationDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sideButtonsOpenEnterAndCleanTheLevelWhenReturning() {
        val tree = InstrumentTree(listOf(
            ActionNode("Launch Droidball", InstrumentCommand.LaunchDroidball),
            DirectoryNode("BATTLE", listOf(
                DirectoryNode("Add Pokémon", listOf(
                    ActionNode("Species", InstrumentCommand.EditNewOwnedPokemon(OwnedPokemonField.SPECIES)),
                    ActionNode("Nickname", InstrumentCommand.EditNewOwnedPokemon(OwnedPokemonField.NICKNAME)),
                )),
                DirectoryNode("BINDERS", listOf(
                    ActionNode("All Owned", InstrumentCommand.OpenCollection),
                    ActionNode("Favorites", InstrumentCommand.OpenCollection),
                )),
                ActionNode("History", InstrumentCommand.OpenBattleHistory),
            )),
        ))
        val state = mutableStateOf(tree.getState())
        val commands = mutableListOf<InstrumentCommand>()
        fun update(action: () -> Unit) { action(); state.value = tree.getState() }
        compose.setContent {
            CompositionLocalProvider(DiagnosticLogger.LocalCurrentRoute provides "main_menu") {
                ODXFiShell(showBattleOverlay = false,
                    lcdContent = { Text("directory navigation") },
                    onUp = { update { tree.moveSelection(-1) } },
                    onDown = { update { tree.moveSelection(1) } },
                    onLeft = { update { tree.collapseOrParent() } },
                    onRight = { update { tree.openOrEnterSelected() } },
                    onA = { update { tree.executeSelected()?.let(commands::add) } },
                    onB = { update { tree.collapseOrParent() } },
                ) {
                    DirectoryTree(state.value.visibleNodes, state.value.selectedPath, onNodeSelected = {
                        update { tree.executePath(it.path)?.let(commands::add) }
                    })
                }
            }
        }
        press("Down"); press("Right"); press("Right"); press("Right")
        press("Down"); press("Down"); press("Down"); press("Right")
        press("Down"); press("Down"); press("Down")
        compose.runOnIdle {
            assertEquals("/BATTLE/History", state.value.selectedPath)
            assertEquals(setOf("/BATTLE", "/BATTLE/Add Pokémon", "/BATTLE/BINDERS"), state.value.expandedPaths)
        }
        press("Left")
        compose.runOnIdle {
            assertEquals("/BATTLE", state.value.selectedPath)
            assertEquals(setOf("/BATTLE"), state.value.expandedPaths)
            assertTrue(commands.isEmpty())
        }
        compose.onNodeWithText("species").assertDoesNotExist()
        compose.onNodeWithText("favorites").assertDoesNotExist()
        press("Right")
        compose.onNodeWithTag("directory-node:/BATTLE/Add Pokémon").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "closed folder"))
        press("A"); press("A")
        compose.onNodeWithText("species").performTouchInput { click() }
        compose.runOnIdle { assertTrue("CRT touches must remain blocked by GlassShield", commands.isEmpty()) }
        press("Right")
        compose.runOnIdle { assertTrue("Right must not execute a file", commands.isEmpty()) }
        press("A")
        compose.runOnIdle {
            assertEquals(listOf(InstrumentCommand.EditNewOwnedPokemon(OwnedPokemonField.SPECIES)), commands)
        }
        press("B")
        compose.runOnIdle { assertEquals("/BATTLE/Add Pokémon", state.value.selectedPath) }
        press("Left"); press("Left"); press("Left"); press("Left")
        compose.runOnIdle {
            assertEquals("/Launch Droidball", state.value.selectedPath)
            assertTrue(state.value.expandedPaths.isEmpty())
        }
    }

    @Test fun selectedWrappedRowStaysFullyVisibleAtLargeFontOnANarrowScreen() {
        val folder = DirectoryNode("BATTLE", (1..12).map {
            ActionNode("Entry $it", InstrumentCommand.OpenSearch)
        } + ActionNode("Replay Editor", InstrumentCommand.OpenReplayEditor,
            detail = "Review a recorded match and inspect its timeline."))
        val tree = InstrumentTree(listOf(folder))
        tree.openOrEnterSelected()
        val state = mutableStateOf(tree.getState())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                Box(Modifier.size(280.dp, 310.dp).testTag("directory-viewport")) {
                    DirectoryTree(state.value.visibleNodes, state.value.selectedPath, onNodeSelected = {})
                }
            }
        }
        compose.runOnIdle { tree.moveSelection(13); state.value = tree.getState() }
        compose.waitForIdle()
        val selected = compose.onNodeWithTag("directory-node:/BATTLE/Replay Editor")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("directory-viewport").getUnclippedBoundsInRoot()
        assertTrue("The complete wrapped selection must remain in view", selected.top >= viewport.top && selected.bottom <= viewport.bottom)
        assertTrue("Indentation must leave room for the file label", selected.left >= viewport.left && selected.right <= viewport.right)
        compose.onNodeWithText("replay editor").assertIsDisplayed()
    }

    private fun press(key: String) {
        val target = if (key in listOf("Up", "Down", "Left", "Right")) compose.onNodeWithContentDescription(key)
            else compose.onNodeWithText(key)
        target.performTouchInput { click() }
        compose.waitForIdle()
    }
}
