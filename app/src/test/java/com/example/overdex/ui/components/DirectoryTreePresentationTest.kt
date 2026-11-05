package com.example.overdex.ui.components

import com.example.overdex.model.OwnedPokemonBinder
import com.example.overdex.model.OwnedPokemonField
import com.example.overdex.model.navigation.ActionNode
import com.example.overdex.model.navigation.DirectoryNode
import com.example.overdex.model.navigation.FlattenedNode
import com.example.overdex.model.navigation.InstrumentCommand
import org.junit.Assert.*
import org.junit.Test

class DirectoryTreePresentationTest {
    private fun folder(path: String, depth: Int) = FlattenedNode(
        DirectoryNode(path.substringAfterLast('/'), emptyList()), depth, path, true,
    )

    private fun leaf(path: String, depth: Int, name: String = path.substringAfterLast('/')) = FlattenedNode(
        ActionNode(name, InstrumentCommand.OpenCurrentTeam), depth, path, false,
    )

    @Test fun nestedBranchesContinueOnlyWhileTheirAncestorHasAFollowingSibling() {
        val rows = directoryRows(listOf(
            folder("/BATTLE", 0),
            folder("/BATTLE/Team", 1),
            leaf("/BATTLE/Team/First", 2),
            leaf("/BATTLE/Team/Last", 2),
            folder("/BATTLE/Binders", 1),
            leaf("/BATTLE/Binders/Owned", 2),
            leaf("/PROFILE", 0),
        ), "/BATTLE/Team/Last")

        assertTrue(rows[1].hasFollowingSibling)
        assertEquals(listOf(true), rows[2].continuingAncestors)
        assertTrue(rows[2].hasFollowingSibling)
        assertEquals(listOf(true), rows[3].continuingAncestors)
        assertFalse(rows[3].hasFollowingSibling)
        assertFalse(rows[4].hasFollowingSibling)
        assertEquals(listOf(false), rows[5].continuingAncestors)
        assertTrue(rows[0].isActiveAncestor)
        assertTrue(rows[1].isActiveAncestor)
        assertFalse(rows[3].isActiveAncestor)
        assertFalse(rows[4].isActiveAncestor)
    }

    @Test fun inlineChoicesUseDeclaredDepthWithoutInventingAnExtraPathRail() {
        val command = InstrumentCommand.EditNewOwnedPokemon(OwnedPokemonField.SPECIES)
        val choice = FlattenedNode(ActionNode("#0025 Pikachu", command), 3,
            "/BATTLE/Add Pokémon/Species/choice/0", false)
        val rows = directoryRows(listOf(
            folder("/BATTLE", 0),
            folder("/BATTLE/Add Pokémon", 1),
            FlattenedNode(ActionNode("Species", command), 2, "/BATTLE/Add Pokémon/Species", false),
            choice,
            FlattenedNode(ActionNode("#0026 Raichu", command), 3,
                "/BATTLE/Add Pokémon/Species/choice/1", false),
            leaf("/BATTLE/Add Pokémon/Nickname", 2),
            leaf("/BATTLE/Current Team", 1),
        ), choice.path)

        assertEquals(listOf(true, true), rows[3].continuingAncestors)
        assertTrue(rows[3].hasFollowingSibling)
        assertEquals("#0025 Pikachu", rows[3].label)
        assertSame(choice, rows[3].row)
    }

    @Test fun anUnrelatedPrefixIsNotAnActiveAncestorOrSiblingContinuation() {
        val rows = directoryRows(listOf(
            folder("/BATTLE", 0),
            leaf("/BATTLE/Team", 1),
            folder("/BATTLES", 0),
            leaf("/BATTLES/Team", 1),
        ), "/BATTLES/Team")
        assertFalse(rows[0].isActiveAncestor)
        assertFalse(rows[1].hasFollowingSibling)
        assertTrue(rows[2].isActiveAncestor)
    }

    @Test fun slashesInAUserNameDoNotChangeSiblingRailsOrCreateAnAncestor() {
        val rows = directoryRows(listOf(
            folder("/BATTLE", 0),
            folder("/BATTLE/BINDERS", 1),
            leaf("/BATTLE/BINDERS/Team", 2),
            leaf("/BATTLE/BINDERS/Team/Rain", 2),
        ), "/BATTLE/BINDERS/Team/Rain")
        assertTrue(rows[2].hasFollowingSibling)
        assertFalse(rows[2].isActiveAncestor)
        assertTrue(rows[1].isActiveAncestor)
        assertFalse(rows[3].hasFollowingSibling)
    }

    @Test fun casingChangesOnlyTheStructuralPresentationAndKeepsProperNames() {
        val launch = FlattenedNode(ActionNode("Launch DROIDBALL", InstrumentCommand.LaunchDroidball),
            0, "/Launch DROIDBALL", false)
        val add = folder("/Add POKÉMON", 0)
        assertEquals("launch Droidball", directoryNodeLabel(launch))
        assertEquals("add Pokémon", directoryNodeLabel(add))
        assertEquals("/Launch DROIDBALL", launch.path)
        assertEquals("Launch DROIDBALL", launch.node.name)
    }

    @Test fun customBinderNamesKeepTheirSpellingWhileBuiltInLabelsBecomeLowercase() {
        val custom = OwnedPokemonBinder("custom-123", "My GO Team / Rain")
        fun row(binder: OwnedPokemonBinder) = FlattenedNode(
            ActionNode(binder.displayName, InstrumentCommand.OpenOwnedBinder(binder)),
            2, "/BATTLE/BINDERS/${binder.displayName}", false,
        )
        assertEquals("My GO Team / Rain", directoryNodeLabel(row(custom)))
        assertEquals("all owned", directoryNodeLabel(row(OwnedPokemonBinder.ALL)))
    }
}
