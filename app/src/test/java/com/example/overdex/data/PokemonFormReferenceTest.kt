package com.example.overdex.data

import com.example.overdex.model.GameMaster
import kotlinx.serialization.json.Json
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PokemonFormReferenceTest {
    private val catalogue = Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(
        File("src/main/assets/gamemaster.json").readText()).pokemon

    @Test fun `real Raichu forms have separate types and legal pools`() {
        val ordinary = requireNotNull(PokemonFormReference.bySprite(26, "g2.s", catalogue))
        val alolan = requireNotNull(PokemonFormReference.bySprite(26, "fALOLA.s", catalogue))
        assertEquals("Raichu", ordinary.speciesName)
        assertEquals("Raichu (Alolan)", alolan.speciesName)
        assertTrue("psychic" in alolan.types)
        assertFalse("psychic" in ordinary.types)
        assertTrue("CHARM" in ordinary.fastMoves)
        assertFalse("CHARM" in alolan.fastMoves)
        assertTrue("SPARK" in alolan.fastMoves)
    }

    @Test fun `exact form wins regardless of catalogue order and unknown form stays unknown`() {
        val raichu = catalogue.filter { it.dex == 26 }.reversed()
        assertEquals("raichu", PokemonFormReference.byName("Raichu", raichu)?.speciesId)
        assertEquals("raichu_alolan", PokemonFormReference.byName("Raichu (Alolan)", raichu)?.speciesId)
        assertNull(PokemonFormReference.byName("Raichu (Unknown)", raichu))
        assertNull(PokemonFormReference.bySprite(26, "fUNSUPPORTED.s", raichu))
        assertNull(PokemonFormReference.bySprite(26, "cCOSTUME_1", raichu))
    }

    @Test fun `gender and shiny do not change form but region and size do`() {
        assertEquals("base", PokemonFormReference.spriteForm("g2.s"))
        assertEquals("fALOLA", PokemonFormReference.spriteForm("fALOLA.g2.s"))
        assertEquals("Gourgeist (Super)", PokemonFormReference.bySprite(711, "fSUPER.s", catalogue)?.speciesName)
    }

    @Test fun `bare OCR preserves form but a new visual base result replaces it`() {
        assertEquals("Raichu (Alolan)", PokemonFormReference.mergeRosterName("Raichu (Alolan)", "Raichu"))
        assertEquals("Raichu", PokemonFormReference.mergeRosterName("Raichu (Alolan)", "Raichu", true))
        assertEquals("Sneasel", PokemonFormReference.mergeRosterName("Raichu (Alolan)", "Sneasel"))
    }

    @Test fun `bare badge uses only a unique roster form and explicit observation wins`() {
        assertEquals("Raichu (Alolan)", PokemonFormReference.playerReferenceName("Raichu", listOf("Raichu (Alolan)", "Sneasel")))
        assertEquals("Raichu", PokemonFormReference.playerReferenceName("Raichu", listOf("Raichu", "Raichu (Alolan)")))
        assertEquals("Raichu (Alolan)", PokemonFormReference.playerReferenceName("Raichu (Alolan)", listOf("Raichu")))
    }
}
