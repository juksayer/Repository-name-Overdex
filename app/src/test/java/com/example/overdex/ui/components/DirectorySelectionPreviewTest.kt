package com.example.overdex.ui.components

import com.example.overdex.model.OwnedPokemonBinder
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.FlattenedNode
import com.example.overdex.model.navigation.InstrumentCommand
import com.example.overdex.model.navigation.InstrumentTree
import org.junit.Assert.*
import org.junit.Test

class DirectorySelectionPreviewTest {
    @Test fun collapsedFolderShowsActualChildrenWithoutNeedingVisibleDescendants() {
        val tree = InstrumentTree(listOf(DirectoryNode("BATTLE", listOf(
            ActionNode("Current Team", InstrumentCommand.OpenCurrentTeam),
            DirectoryNode("BINDERS", emptyList()),
            ActionNode("Match History", InstrumentCommand.OpenBattleHistory),
        ))))
        val collapsed = tree.getState()
        assertEquals(1, collapsed.visibleNodes.size)
        val before = directorySelectionPreview(collapsed.visibleNodes, collapsed.selectedPath)!!
        assertEquals("battle", before.title)
        assertEquals(listOf("current team", "binders", "match history"), before.entries.map { it.label })
        assertEquals(listOf(false, true, false), before.entries.map { it.isFolder })

        tree.openOrEnterSelected()
        val expanded = tree.getState()
        assertEquals(before, directorySelectionPreview(expanded.visibleNodes, expanded.selectedPath))
    }

    @Test fun binderRefreshUsesRealChildrenAndPreservesCustomNamesIncludingSlashes() {
        val tree = InstrumentTree(listOf(DirectoryNode("BINDERS", listOf(
            ActionNode("All Owned", InstrumentCommand.OpenOwnedBinder(OwnedPokemonBinder.ALL)),
            ActionNode("Shadow", InstrumentCommand.OpenOwnedBinder(OwnedPokemonBinder.SHADOW)),
        ))))
        val custom = OwnedPokemonBinder("custom:rain", "Rain / GREAT League")
        tree.replaceChildren("/BINDERS", listOf(
            ActionNode("All Owned", InstrumentCommand.OpenOwnedBinder(OwnedPokemonBinder.ALL)),
            ActionNode(custom.displayName, InstrumentCommand.OpenOwnedBinder(custom)),
        ))
        val state = tree.getState()
        val preview = directorySelectionPreview(state.visibleNodes, state.selectedPath)!!
        assertEquals(listOf("all owned", "Rain / GREAT League"), preview.entries.map { it.label })
    }

    @Test fun singleWorkflowFolderSummarizesUtilityRatherThanRepeatingAOneItemList() {
        val leaf = ActionNode("Replay Editor", InstrumentCommand.OpenReplayEditor)
        val row = FlattenedNode(DirectoryNode("Match", listOf(leaf)), 1, "/BATTLE/Match", false)
        val preview = directorySelectionPreview(listOf(row), row.path)!!
        assertEquals("match", preview.title)
        assertTrue(preview.description.contains("saved replay clips"))
        assertTrue(preview.entries.isEmpty())
        assertTrue(preview.controls.single().contains("Right open / enter"))
    }

    @Test fun emptyFolderDoesNotInventContentsOrAnOpenAction() {
        val row = FlattenedNode(DirectoryNode("Empty", emptyList()), 0, "/Empty", false)
        val preview = directorySelectionPreview(listOf(row), row.path)!!
        assertEquals("This folder has no entries yet.", preview.description)
        assertTrue(preview.entries.isEmpty())
        assertEquals(listOf("Left return"), preview.controls)
    }

    @Test fun missingSelectionDoesNotRetainAPreviousPreview() {
        val row = FlattenedNode(ActionNode("OVERDEX", InstrumentCommand.OpenSearch), 0, "/OVERDEX", false)
        assertNull(directorySelectionPreview(listOf(row), "/missing"))
        assertNull(directorySelectionPreview(emptyList(), "/OVERDEX"))
    }

    @Test fun genericActionUsesItsActualDetailWhenAvailable() {
        val row = FlattenedNode(ActionNode("Capture", InstrumentCommand.OpenCapture,
            detail = "Register a Pokémon from a saved image."), 0, "/Capture", false)
        val preview = directorySelectionPreview(listOf(row), row.path)!!
        assertEquals("Register a Pokémon from a saved image.", preview.description)
        assertEquals(listOf("A open · B return"), preview.controls)
    }

    @Test fun calibrationAndDroidballHaveSeparatePurposesAndActualEntryHints() {
        fun preview(name: String, command: InstrumentCommand): DirectorySelectionPreview {
            val row = FlattenedNode(ActionNode(name, command), 0, "/$name", false)
            return directorySelectionPreview(listOf(row), row.path)!!
        }
        val launch = preview("Launch Droidball", InstrumentCommand.LaunchDroidball)
        val calibration = preview("Calibration", InstrumentCommand.OpenDirectoryCalibration)
        assertTrue(launch.description.contains("record battles"))
        assertTrue(launch.controls.any { "Hold A calibration" in it })
        assertTrue(calibration.description.contains("captured battle crops"))
        assertFalse(calibration.description.contains("record battles"))
        assertTrue(calibration.entries.isEmpty())
    }

    @Test fun contextualHintsDistinguishOpeningAWorkflowFromItsInternalControls() {
        fun controls(command: InstrumentCommand): List<String> {
            val row = FlattenedNode(ActionNode("Utility", command), 0, "/Utility", false)
            return directorySelectionPreview(listOf(row), row.path)!!.controls
        }
        val dex = controls(InstrumentCommand.OpenSearch)
        assertEquals("A open binder", dex.first())
        assertTrue(dex.any { it.startsWith("In binder:") && "Select search" in it })

        val records = controls(InstrumentCommand.OpenBattleRecords)
        assertEquals("A open", records.first())
        assertTrue(records.any { "Start filters" in it && "Select change event" in it })
        assertTrue(records.none { "Select search" in it })

        val replay = controls(InstrumentCommand.OpenReplayEditor)
        assertTrue(replay.any { "Up / Down item" in it && "Left / Right action" in it })
        val binders = controls(InstrumentCommand.ManageBinders)
        assertTrue(binders.any { it.startsWith("Inside:") && "Start (+) new" in it && "Select edit custom binder" in it })
    }
}
