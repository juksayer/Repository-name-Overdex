package com.example.overdex.model.navigation

import org.junit.Assert.*
import org.junit.Test

class InstrumentTreeNavigationTest {
    private fun tree() = InstrumentTree(listOf(
        ActionNode("Launch Droidball", InstrumentCommand.LaunchDroidball),
        DirectoryNode("BATTLE", listOf(
            DirectoryNode("Add Pokémon", listOf(
                ActionNode("Species", InstrumentCommand.AddSpecimen),
            )),
            DirectoryNode("BINDERS", listOf(
                DirectoryNode("Training Partners", listOf(
                    ActionNode("Partner", InstrumentCommand.OpenCollection),
                )),
                ActionNode("Favorites", InstrumentCommand.OpenCollection),
            )),
            DirectoryNode("Match", listOf(
                ActionNode("Match Summary", InstrumentCommand.OpenBattleLogs),
            )),
            ActionNode("Current Team", InstrumentCommand.OpenCurrentTeam),
        )),
        DirectoryNode("Tools", listOf(
            DirectoryNode("Calibration", listOf(
                ActionNode("Match Calibration", InstrumentCommand.OpenMatchCalibration),
            )),
        )),
        ActionNode("PROFILE", InstrumentCommand.OpenProfile),
    ))

    private fun InstrumentTree.select(path: String) {
        val state = getState()
        val destination = state.visibleNodes.indexOfFirst { it.path == path }
        assertTrue("Expected a visible path: $path", destination >= 0)
        val current = state.visibleNodes.indexOfFirst { it.path == state.selectedPath }
        moveSelection(destination - current)
        assertEquals(path, getState().selectedPath)
    }

    private fun InstrumentTree.open(path: String) {
        select(path)
        assertTrue(openOrEnterSelected())
        assertEquals(path, getState().selectedPath)
    }

    @Test fun rightOpensThenEntersAndNeverExecutesALeaf() {
        val tree = tree()
        tree.select("/BATTLE")
        assertTrue(tree.openOrEnterSelected())
        assertEquals("/BATTLE", tree.getState().selectedPath)
        assertEquals(setOf("/BATTLE"), tree.getState().expandedPaths)

        assertTrue(tree.openOrEnterSelected())
        assertEquals("/BATTLE/Add Pokémon", tree.getState().selectedPath)
        assertEquals(setOf("/BATTLE"), tree.getState().expandedPaths)
        assertTrue(tree.openOrEnterSelected())
        assertEquals("/BATTLE/Add Pokémon", tree.getState().selectedPath)
        assertTrue(tree.openOrEnterSelected())
        assertEquals("/BATTLE/Add Pokémon/Species", tree.getState().selectedPath)

        val before = tree.getState()
        assertFalse(tree.openOrEnterSelected())
        assertEquals(before, tree.getState())
        assertEquals(InstrumentCommand.AddSpecimen, tree.executeSelected())
    }

    @Test fun aUsesTheSameFolderOpeningAndEnteringWithoutTogglingItClosed() {
        val tree = tree()
        assertNull(tree.executePath("/BATTLE"))
        assertEquals("/BATTLE", tree.getState().selectedPath)
        assertNull(tree.executeSelected())
        assertEquals("/BATTLE/Add Pokémon", tree.getState().selectedPath)
        assertTrue("A must keep the parent open", "/BATTLE" in tree.getState().expandedPaths)
        assertNull(tree.executeSelected())
        assertEquals("/BATTLE/Add Pokémon", tree.getState().selectedPath)
        assertNull(tree.executeSelected())
        assertEquals("/BATTLE/Add Pokémon/Species", tree.getState().selectedPath)
        assertEquals(InstrumentCommand.AddSpecimen, tree.executeSelected())
    }

    @Test fun movingPastAFolderDoesNotExpandOrExecuteIt() {
        val tree = tree()
        tree.moveSelection(1)
        assertEquals("/BATTLE", tree.getState().selectedPath)
        assertTrue(tree.getState().expandedPaths.isEmpty())
        tree.moveSelection(1)
        assertEquals("/Tools", tree.getState().selectedPath)
        assertTrue(tree.getState().expandedPaths.isEmpty())
    }

    @Test fun leftCollapsesSelectedOpenBranchRecursivelyBeforeReturningToParent() {
        val tree = tree()
        tree.open("/BATTLE")
        tree.open("/BATTLE/Add Pokémon")
        tree.open("/BATTLE/BINDERS")
        tree.open("/BATTLE/BINDERS/Training Partners")
        tree.select("/BATTLE/BINDERS")

        assertTrue(tree.collapseOrParent())
        assertEquals("/BATTLE/BINDERS", tree.getState().selectedPath)
        assertEquals(setOf("/BATTLE", "/BATTLE/Add Pokémon"), tree.getState().expandedPaths)
        assertFalse(tree.getState().visibleNodes.any { it.path.startsWith("/BATTLE/BINDERS/") })

        assertTrue(tree.collapseOrParent())
        assertEquals("/BATTLE", tree.getState().selectedPath)
        assertEquals(setOf("/BATTLE"), tree.getState().expandedPaths)
    }

    @Test fun returningToParentClosesEverySiblingFolderAndTheirDescendants() {
        val tree = tree()
        tree.open("/BATTLE")
        tree.open("/BATTLE/Add Pokémon")
        tree.open("/BATTLE/BINDERS")
        tree.open("/BATTLE/BINDERS/Training Partners")
        tree.open("/BATTLE/Match")
        tree.open("/Tools")
        tree.open("/Tools/Calibration")
        tree.select("/BATTLE/Current Team")

        assertTrue(tree.collapseOrParent())
        assertEquals("/BATTLE", tree.getState().selectedPath)
        assertEquals(setOf("/BATTLE", "/Tools", "/Tools/Calibration"), tree.getState().expandedPaths)
        assertEquals(listOf("Add Pokémon", "BINDERS", "Match", "Current Team"),
            tree.getState().visibleNodes.filter { it.path.startsWith("/BATTLE/") }.map { it.node.name })

        tree.select("/BATTLE/BINDERS")
        assertTrue(tree.openOrEnterSelected())
        assertTrue(tree.getState().visibleNodes.any { it.path == "/BATTLE/BINDERS/Training Partners" })
        assertFalse("Reopening a parent must not resurrect a previously open nested folder",
            tree.getState().visibleNodes.any { it.path == "/BATTLE/BINDERS/Training Partners/Partner" })
    }

    @Test fun returningFromNestedLeafKeepsItsParentOpenAndCleansOnlyThatLevel() {
        val tree = tree()
        tree.open("/BATTLE")
        tree.open("/BATTLE/Add Pokémon")
        tree.open("/BATTLE/BINDERS")
        tree.open("/BATTLE/BINDERS/Training Partners")
        tree.open("/BATTLE/Match")
        tree.select("/BATTLE/BINDERS/Favorites")

        assertTrue(tree.collapseOrParent())
        assertEquals("/BATTLE/BINDERS", tree.getState().selectedPath)
        assertEquals(setOf("/BATTLE", "/BATTLE/Add Pokémon", "/BATTLE/BINDERS", "/BATTLE/Match"),
            tree.getState().expandedPaths)
        assertFalse(tree.getState().visibleNodes.any { it.path == "/BATTLE/BINDERS/Training Partners/Partner" })
    }

    @Test fun leftFromTopLevelActionReturnsToFirstRootAndClosesAllRootBranches() {
        val tree = tree()
        tree.open("/BATTLE")
        tree.open("/BATTLE/BINDERS")
        tree.open("/Tools")
        tree.open("/Tools/Calibration")
        tree.select("/PROFILE")

        assertTrue(tree.collapseOrParent())
        assertEquals("/Launch Droidball", tree.getState().selectedPath)
        assertTrue(tree.getState().expandedPaths.isEmpty())
        assertEquals(4, tree.getState().visibleNodes.size)
        assertFalse(tree.collapseOrParent())
    }

    @Test fun leftFromOpenTopLevelFolderCollapsesItBeforeReturningToTop() {
        val tree = tree()
        tree.open("/BATTLE")
        tree.open("/Tools")
        tree.select("/BATTLE")

        assertTrue(tree.collapseOrParent())
        assertEquals("/BATTLE", tree.getState().selectedPath)
        assertEquals(setOf("/Tools"), tree.getState().expandedPaths)
        assertTrue(tree.collapseOrParent())
        assertEquals("/Launch Droidball", tree.getState().selectedPath)
        assertTrue(tree.getState().expandedPaths.isEmpty())
    }

    @Test fun emptyTreeAndEmptyFolderHaveNoPhantomChildOrCommand() {
        val emptyTree = InstrumentTree(emptyList())
        assertFalse(emptyTree.openOrEnterSelected())
        assertFalse(emptyTree.collapseOrParent())
        assertNull(emptyTree.executeSelected())

        val tree = InstrumentTree(listOf(DirectoryNode("Empty", emptyList())))
        val before = tree.getState()
        assertFalse(tree.openOrEnterSelected())
        assertNull(tree.executeSelected())
        assertFalse(tree.collapseOrParent())
        assertEquals(before, tree.getState())
    }

    @Test fun backRetainsExistingEditorParentNavigationWithoutClearingOtherBranches() {
        val tree = tree()
        tree.open("/BATTLE")
        tree.open("/BATTLE/Add Pokémon")
        tree.open("/BATTLE/BINDERS")
        tree.select("/BATTLE/Add Pokémon/Species")
        val expanded = tree.getState().expandedPaths

        assertTrue(tree.navigateBack())
        assertEquals("/BATTLE/Add Pokémon", tree.getState().selectedPath)
        assertEquals(expanded, tree.getState().expandedPaths)
    }

    @Test fun rightAfterLibraryRefreshEntersNewChildAndPreservesOriginalNamesAndPaths() {
        val tree = tree()
        tree.open("/BATTLE")
        tree.open("/BATTLE/BINDERS")
        tree.replaceChildren("/BATTLE/BINDERS", listOf(ActionNode("My Mixed CASE Pokémon",
            InstrumentCommand.OpenCollection)))

        assertTrue(tree.openOrEnterSelected())
        assertEquals("/BATTLE/BINDERS/My Mixed CASE Pokémon", tree.getState().selectedPath)
        assertEquals("My Mixed CASE Pokémon", tree.getState().visibleNodes.first {
            it.path == tree.getState().selectedPath
        }.node.name)
        assertFalse(tree.openOrEnterSelected())
        assertEquals(InstrumentCommand.OpenCollection, tree.executeSelected())
    }

    @Test fun hiddenLeafCannotBeExecutedByAStalePath() {
        val tree = tree()
        assertNull(tree.executePath("/BATTLE/Current Team"))
        assertEquals("/Launch Droidball", tree.getState().selectedPath)
        assertTrue(tree.getState().expandedPaths.isEmpty())
    }

    @Test fun levelCleanupDoesNotConfuseSiblingPathsThatShareANamePrefix() {
        fun branch(name: String) = DirectoryNode(name, listOf(
            DirectoryNode("Child", listOf(ActionNode("Leaf", InstrumentCommand.OpenSearch))),
            ActionNode("Return", InstrumentCommand.OpenSearch),
        ))
        val tree = InstrumentTree(listOf(branch("A"), branch("AB")))
        tree.open("/A")
        tree.open("/A/Child")
        tree.open("/AB")
        tree.open("/AB/Child")
        tree.select("/A/Return")

        assertTrue(tree.collapseOrParent())
        assertEquals("/A", tree.getState().selectedPath)
        assertEquals(setOf("/A", "/AB", "/AB/Child"), tree.getState().expandedPaths)
        assertTrue(tree.getState().visibleNodes.any { it.path == "/AB/Child/Leaf" })
    }

    @Test fun rightEntersBinderWithSlashAndLeftReturnsToActualParentAndClearsItsChildFolders() {
        val tree = tree()
        tree.open("/BATTLE")
        tree.open("/BATTLE/BINDERS")
        tree.replaceChildren("/BATTLE/BINDERS", listOf(
            ActionNode("My GO Team / Rain", InstrumentCommand.OpenCollection),
            DirectoryNode("Archive / 2026", listOf(
                DirectoryNode("Season / One", listOf(ActionNode("Saved", InstrumentCommand.OpenCollection))),
            )),
        ))
        tree.open("/BATTLE/BINDERS/Archive / 2026")
        tree.open("/BATTLE/BINDERS/Archive / 2026/Season / One")
        tree.select("/BATTLE/BINDERS")

        assertTrue(tree.openOrEnterSelected())
        assertEquals("/BATTLE/BINDERS/My GO Team / Rain", tree.getState().selectedPath)
        assertTrue(tree.collapseOrParent())
        assertEquals("/BATTLE/BINDERS", tree.getState().selectedPath)
        assertEquals(setOf("/BATTLE", "/BATTLE/BINDERS"), tree.getState().expandedPaths)
        assertFalse(tree.getState().visibleNodes.any { it.path.endsWith("Season / One") })
    }

    @Test fun closedTopLevelFolderWithSlashReturnsToActualFirstRoot() {
        val tree = InstrumentTree(listOf(
            ActionNode("Launch Droidball", InstrumentCommand.LaunchDroidball),
            DirectoryNode("Tools / Settings", listOf(ActionNode("Calibration", InstrumentCommand.OpenCalibration))),
        ))
        tree.select("/Tools / Settings")
        assertTrue(tree.collapseOrParent())
        assertEquals("/Launch Droidball", tree.getState().selectedPath)
        assertTrue(tree.getState().expandedPaths.isEmpty())
    }

    @Test fun closingFolderDoesNotCloseASiblingWhoseNameLooksLikeItsDescendantPath() {
        val tree = InstrumentTree(listOf(DirectoryNode("BINDERS", listOf(
            DirectoryNode("Rain", listOf(
                DirectoryNode("Notes", listOf(ActionNode("Saved", InstrumentCommand.OpenCollection))),
            )),
            DirectoryNode("Rain/Forecast", listOf(ActionNode("Saved", InstrumentCommand.OpenCollection))),
        ))))
        tree.open("/BINDERS")
        tree.open("/BINDERS/Rain")
        tree.open("/BINDERS/Rain/Notes")
        tree.open("/BINDERS/Rain/Forecast")
        tree.select("/BINDERS/Rain")

        assertTrue(tree.collapseOrParent())
        assertEquals("/BINDERS/Rain", tree.getState().selectedPath)
        assertEquals(setOf("/BINDERS", "/BINDERS/Rain/Forecast"), tree.getState().expandedPaths)
        assertTrue(tree.getState().visibleNodes.any { it.path == "/BINDERS/Rain/Forecast/Saved" })
        assertFalse(tree.getState().visibleNodes.any { it.path == "/BINDERS/Rain/Notes" })
    }
}
