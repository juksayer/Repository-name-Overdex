package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamSelectSpriteMatcherTest {
    @Test fun `runner up ignores alternate forms of the winning species`() {
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
