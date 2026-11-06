package com.example.overdex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.InstrumentTree
import com.example.overdex.ui.components.DirectorySelectionPreviewLcd
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The normal LCD describes the selected directory entry, not an unrelated saved battle. */
class DirectorySelectionPreviewDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun collapsedFolderPreviewsActualChildrenWithoutExpandingTheCrt() {
        val tree = InstrumentTree(listOf(DirectoryNode("BATTLE", listOf(
            ActionNode("Current Team", InstrumentCommand.OpenCurrentTeam),
            DirectoryNode("BINDERS", listOf(ActionNode("All Owned", InstrumentCommand.OpenCollection))),
            ActionNode("Match History", InstrumentCommand.OpenBattleHistory),
        ))))
        val state = tree.getState()
        assertTrue(state.expandedPaths.isEmpty())
        compose.setContent {
            Box(Modifier.size(320.dp, 220.dp)) {
                DirectorySelectionPreviewLcd(state.visibleNodes, state.selectedPath)
            }
        }
        compose.onNodeWithTag("directory-preview-title").assertTextContains("battle", substring = true)
        compose.onNodeWithTag("directory-preview-entry:0").assertTextContains("current team", substring = true)
        compose.onNodeWithTag("directory-preview-entry:1").assertTextContains("binders", substring = true)
        compose.onNodeWithTag("directory-preview-entry:2").assertTextContains("match history", substring = true)
        compose.onNodeWithTag("directory-preview-entry:3").assertDoesNotExist()
        compose.onNodeWithTag("directory-crop-preview").assertDoesNotExist()
        assertTrue("Previewing children must not expand their directory", tree.getState().expandedPaths.isEmpty())
    }

    @Test fun oneWorkflowAndLeafUsePurposeInsteadOfAnEmptyOrRedundantList() {
        val tree = InstrumentTree(listOf(
            DirectoryNode("Match", listOf(ActionNode("Replay Editor", InstrumentCommand.OpenReplayEditor))),
            ActionNode("A utility", InstrumentCommand.OpenCapture, detail = "Inspect a saved specimen image."),
        ))
        val state = mutableStateOf(tree.getState())
        compose.setContent {
            Box(Modifier.size(320.dp, 220.dp)) {
                DirectorySelectionPreviewLcd(state.value.visibleNodes, state.value.selectedPath)
            }
        }
        compose.onNodeWithTag("directory-preview-title").assertTextContains("match", substring = true)
        compose.onNodeWithTag("directory-preview-purpose").assertTextContains("replay", substring = true)
        compose.onNodeWithTag("directory-preview-entry:0").assertDoesNotExist()
        compose.onNodeWithTag("directory-preview-how-to").assertExists()
        compose.runOnIdle { tree.moveSelection(1); state.value = tree.getState() }
        compose.onNodeWithTag("directory-preview-purpose").assertTextContains("Inspect a saved specimen image.", substring = true)
        compose.onNodeWithTag("directory-preview-entry:0").assertDoesNotExist()
        compose.onNodeWithTag("directory-crop-preview").assertDoesNotExist()
    }

    @Test fun smallLcdCanScrollContentsAndChangingSelectionReturnsToTheNewHeading() {
        val tree = InstrumentTree(listOf(
            DirectoryNode("Library", (1..14).map { ActionNode("Entry $it", InstrumentCommand.OpenSearch) }),
            ActionNode("PROFILE", InstrumentCommand.OpenProfile),
        ))
        val state = mutableStateOf(tree.getState())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                Box(Modifier.size(220.dp, 100.dp).testTag("preview-viewport")) {
                    DirectorySelectionPreviewLcd(state.value.visibleNodes, state.value.selectedPath)
                }
            }
        }
        compose.onNodeWithTag("directory-preview-entry:13").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { tree.moveSelection(1); state.value = tree.getState() }
        compose.onNodeWithTag("directory-preview-title").assertIsDisplayed().assertTextContains("profile", substring = true)
        val heading = compose.onNodeWithTag("directory-preview-title").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("preview-viewport").getUnclippedBoundsInRoot()
        assertTrue("A new selection must start at its heading", heading.top >= viewport.top && heading.bottom <= viewport.bottom)
    }
}
