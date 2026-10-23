package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamSelectSpriteMatcherTest {
    @Test fun `bundled Raichu reference measurements separate ordinary and Alolan forms`() {
        val catalogue = TeamSelectSpriteMatcher.parseCatalogue(
            java.io.File("src/main/assets/team_select_sprite_catalog.tsv").readLines().asSequence())
        for (variant in listOf("base", "fALOLA", "fALOLA.s")) {
            val query = catalogue.single { it.speciesId == 26 && it.variant == variant }
            val result = requireNotNull(TeamSelectSpriteMatcher.matchDescriptor(query.values, catalogue))
            assertEquals(26, result.speciesId)
            assertEquals(com.example.overdex.data.PokemonFormReference.spriteForm(variant),
                com.example.overdex.data.PokemonFormReference.spriteForm(result.variant))
        }
    }
    @Test fun `separated form preserves both species and form margins`() {
        val query = descriptor(hueBin = 2)
        val result = TeamSelectSpriteMatcher.matchDescriptor(
            query,
            listOf(
                TeamSelectSpriteDescriptor(150, "base", query.copyOf()),
                TeamSelectSpriteDescriptor(150, "form", descriptor(hueBin = 3)),
                TeamSelectSpriteDescriptor(445, "base", descriptor(hueBin = 12)),
            ),
        )

        assertEquals(150, result?.speciesId)
        assertTrue(requireNotNull(result).distinctSpeciesMargin > 0.05f)
        assertTrue(requireNotNull(result.distinctFormMargin) > 0.05f)
    }

    @Test fun `same species but tied regional forms are not accepted`() {
        val query = descriptor(2)
        assertNull(TeamSelectSpriteMatcher.matchDescriptor(query, listOf(
            TeamSelectSpriteDescriptor(26, "base", query.copyOf()),
            TeamSelectSpriteDescriptor(26, "fALOLA", query.copyOf()),
            TeamSelectSpriteDescriptor(150, "base", descriptor(12)),
        )))
    }

    @Test fun `shiny and gender duplicates do not count as competing forms`() {
        val query = descriptor(2)
        val result = TeamSelectSpriteMatcher.matchDescriptor(query, listOf(
            TeamSelectSpriteDescriptor(26, "fALOLA", query.copyOf()),
            TeamSelectSpriteDescriptor(26, "fALOLA.s", query.copyOf()),
            TeamSelectSpriteDescriptor(26, "fALOLA.g2.s", query.copyOf()),
            TeamSelectSpriteDescriptor(26, "base", descriptor(8)),
            TeamSelectSpriteDescriptor(150, "base", descriptor(12)),
        ))
        assertEquals("fALOLA", result?.variant)
        assertTrue(requireNotNull(result?.distinctFormMargin) > 0.05f)
    }

    @Test fun `ambiguous visual measurements are not asserted as a species`() {
        val query = descriptor(hueBin = 2)
        assertNull(
            TeamSelectSpriteMatcher.matchDescriptor(
                query,
                listOf(
                    TeamSelectSpriteDescriptor(150, "base", query.copyOf()),
                    TeamSelectSpriteDescriptor(445, "base", query.copyOf()),
                ),
            ),
        )
    }

    @Test fun `catalogue parser rejects malformed descriptor rows`() {
        val valid = descriptor(hueBin = 2).joinToString(",")
        val parsed = TeamSelectSpriteMatcher.parseCatalogue(
            sequenceOf("# header", "150\tbase\t$valid", "bad row"),
        )
        assertEquals(1, parsed.size)
        assertEquals(150, parsed.single().speciesId)
    }

    private fun descriptor(hueBin: Int): FloatArray = FloatArray(45).also { values ->
        values[hueBin] = 1f
        values[24] = 1f
        values[32] = 1f
        values[40] = 0.4f
        values[41] = 0.7f
        values[42] = 0.7f
        values[43] = 0.5f
        values[44] = 0.5f
    }
}
