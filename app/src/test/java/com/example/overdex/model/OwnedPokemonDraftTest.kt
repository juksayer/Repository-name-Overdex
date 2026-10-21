package com.example.overdex.model

import com.example.overdex.model.navigation.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OwnedPokemonDraftTest {
    private fun species(id: Int, name: String) = Pokemon(id, name, emptyList(), "",
        fastMoves = emptyList(), chargedMoves = emptyList())

    @Test fun restoredDraftAndRepeatedSaveKeepOneIndividualAcrossTeams() {
        val draft = OwnedPokemonDraft().selectSpecies(species(6, "Charizard")).copy(
            nickname = "Flame:FIRE:", cp = "1498", teams = "Great, Fire, great", size = PokemonSize.XL,
        )
        val restored = Json.decodeFromString<OwnedPokemonDraft>(Json.encodeToString(draft))
        assertEquals(draft.id, restored.toOwnedPokemon().id)
        assertEquals(draft.id, restored.toOwnedPokemon().id)
        assertEquals(listOf("Great", "Fire"), restored.toOwnedPokemon().teams)
        assertEquals("Flame:FIRE:", restored.toOwnedPokemon().displayName)
        assertEquals(PokemonSize.XL, restored.toOwnedPokemon().size)
        assertNotEquals(draft.id, OwnedPokemonDraft().selectSpecies(species(6, "Charizard")).id)
    }

    @Test fun speciesIsRequiredButUnknownMovesAndCpRemainUnknown() {
        assertNotNull(OwnedPokemonDraft().validationError)
        val draft = OwnedPokemonDraft().selectSpecies(species(25, "Pikachu"))
        assertNull(draft.validationError)
        assertNull(draft.toOwnedPokemon().cp)
        assertNull(draft.toOwnedPokemon().fastMove)
        assertNotNull(draft.copy(cp = "0").validationError)
        assertNotNull(draft.copy(cp = "invalid").validationError)
        assertEquals(999, draft.copy(cp = "999").toOwnedPokemon().cp)
    }

    @Test fun correctingSpeciesClearsMovesButRetainsTheIndividualAndOtherEntries() {
        val pikachu = species(25, "Pikachu")
        val draft = OwnedPokemonDraft().selectSpecies(pikachu).copy(fastMove = "Thunder Shock",
            chargedMove1 = "Thunderbolt", chargedMove2 = "Surf", teams = "Great")
        assertEquals(draft, draft.selectSpecies(pikachu))
        val corrected = draft.selectSpecies(species(323, "Camerupt"))
        assertEquals(draft.id, corrected.id)
        assertEquals("Great", corrected.teams)
        assertNull(corrected.fastMove)
        assertNull(corrected.chargedMove1)
        assertNull(corrected.chargedMove2)
    }

    @Test fun finishingAnInlineBranchReturnsFocusToItAndKeepsItsParentOpen() {
        val tree = InstrumentTree(listOf(DirectoryNode("BATTLE", listOf(DirectoryNode("Add Pokémon",
            listOf(ActionNode("Species", InstrumentCommand.EditNewOwnedPokemon(OwnedPokemonField.SPECIES))))))))
        tree.executePath("/BATTLE")
        tree.executePath("/BATTLE/Add Pokémon")
        tree.executePath("/BATTLE/Add Pokémon/Species")
        tree.collapse("/BATTLE/Add Pokémon")
        assertEquals("/BATTLE/Add Pokémon", tree.getState().selectedPath)
        assertEquals(setOf("/BATTLE"), tree.getState().expandedPaths)
        assertEquals(listOf("BATTLE", "Add Pokémon"), tree.getState().visibleNodes.map { it.node.name })
    }
}
