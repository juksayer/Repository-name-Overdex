package com.example.overdex.battle.observation

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SpeciesMissingCharacterTest {
    // Match PokemonRepository's imported names plus GameMaster forms/base aliases.
    // A one-entry vocabulary cannot establish that a repair is unambiguous.
    private val names by lazy {
        val game = Json.parseToJsonElement(File("src/main/assets/gamemaster.json").readText())
        val imported = Json.parseToJsonElement(File("src/main/assets/pokemon_go_data.json").readText())
        (game.jsonObject.getValue("pokemon").jsonArray.flatMap {
            val name = it.jsonObject.getValue("speciesName").jsonPrimitive.content
            listOf(name, name.substringBefore(" ("))
        } + imported.jsonObject.getValue("pokemon").jsonArray.map {
            it.jsonObject.getValue("name").jsonPrimitive.content
        }).toSet()
    }

    @Test fun `lost first middle and final letters resolve against the complete catalogue`() {
        assertTrue(names.size > 1_700)
        for (reading in listOf("karmory", "s armory", "Skarmry", "Skarmor", "CP 1499 karmory", "s armory\nCP 1499")) {
            assertEquals(reading, "Skarmory", SpeciesTextResolver.resolve(reading, names))
            val live = requireNotNull(FastOverlaySpeciesResolver.resolveDetailed(reading, names))
            assertEquals(reading, "Skarmory", live.speciesName)
            assertEquals("UNAMBIGUOUS_CATALOGUE_MISSING_LETTERS_1", live.basis)
            assertTrue(live.confidence < requireNotNull(FastOverlaySpeciesResolver.resolveDetailed("Skarmory", names)).confidence)
        }
        assertEquals("Mewtwo", SpeciesTextResolver.resolve("Mewto", names))
        assertEquals("Sealeo", SpeciesTextResolver.resolve("Seale", names))
    }

    @Test fun `two lost letters require at least three quarters of the complete name`() {
        val resolution = requireNotNull(FastOverlaySpeciesResolver.resolveDetailed("Skamoy", names))
        assertEquals("Skarmory", resolution.speciesName)
        assertEquals("UNAMBIGUOUS_CATALOGUE_MISSING_LETTERS_2", resolution.basis)
        for (reading in listOf("Sarmy", "Ear", "arm", "\n", "CP 1499", "S#armory", "SXarmory", "not s armory", "karmory used")) {
            assertNull(reading, SpeciesTextResolver.resolve(reading, names))
            assertNull(reading, FastOverlaySpeciesResolver.resolveDetailed(reading, names))
        }
    }

    @Test fun `ambiguous missing letters cannot fall back to a prefix guess`() {
        for (reading in listOf("Porygo", "Snease", "Nidora", "Nidorin")) {
            assertNull(reading, SpeciesTextResolver.resolve(reading, names))
            assertNull(reading, FastOverlaySpeciesResolver.resolveDetailed(reading, names))
        }
        for (name in listOf("Mew", "Mewtwo", "Sneasel", "Sneasler", "Porygon", "Porygon2")) {
            assertEquals(name, SpeciesTextResolver.resolve(name, names))
        }
    }

    @Test fun `announcement field extraction shares missing letter recovery`() {
        assertEquals("Skarmory", EntryAnnouncementSpeciesTextResolver.resolve("Go, s armory!", names))
        assertEquals("Skarmory", AnnouncementSpeciesTextResolver.resolveUsedMovePerformer("karmory used Sky Attack!", names))
        assertNull(EntryAnnouncementSpeciesTextResolver.resolve("Go, Porygo!", names))
        assertNull(AnnouncementSpeciesTextResolver.resolveUsedMovePerformer("Sky Attack karmory", names))
    }
}
