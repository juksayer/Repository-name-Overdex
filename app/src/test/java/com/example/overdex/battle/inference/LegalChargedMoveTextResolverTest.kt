package com.example.overdex.battle.inference

import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class LegalChargedMoveTextResolverTest {
    private val earthPower = Move("Earth Power", PokemonType.GROUND, 90, 55, false)

    @Test fun `one extra terminal S after a complete legal name is an explicit OCR repair`() {
        val match = LegalChargedMoveTextResolver.bestMatches("Earth Powers", listOf(earthPower)).single()
        assertEquals(earthPower, match.move)
        assertEquals(1, match.repairRank)
        assertEquals("OCR_SINGLE_CHARACTER_REPAIR", match.repairBasis)
        val exact = LegalChargedMoveTextResolver.bestMatches("Earth Power", listOf(earthPower)).single()
        assertEquals(0, exact.repairRank)
        assertNull(exact.repairBasis)
    }

    @Test fun `the trailing S repair cannot introduce a move outside the supplied legal pool`() {
        val flamethrower = Move("Flamethrower", PokemonType.FIRE, 90, 55, false)
        assertTrue(LegalChargedMoveTextResolver.bestMatches("Earth Powers", listOf(flamethrower)).isEmpty())
        assertTrue(LegalChargedMoveTextResolver.bestMatches("Earth Powers", emptyList()).isEmpty())
    }

    @Test fun `meaningful suffixes multiple added glyphs and clipped prefixes remain unresolved`() {
        for (reading in listOf("Earth Power Super", "Earth Power SS", "Earth Power X", "Super Earth Power",
                "Earth", "Earth Powe")) {
            assertTrue(reading, LegalChargedMoveTextResolver.bestMatches(reading, listOf(earthPower)).isEmpty())
        }
    }

    @Test fun `a repaired visible alias cannot pick between two legal Weather Ball types`() {
        val ice = Move("Weather Ball (Ice)", PokemonType.ICE, 60, 35, false)
        val fire = Move("Weather Ball (Fire)", PokemonType.FIRE, 60, 35, false)
        val matches = LegalChargedMoveTextResolver.bestMatches("Weather Balls", listOf(ice, fire))
        assertEquals(setOf(ice, fire), matches.map { it.move }.toSet())
        assertTrue(matches.all { it.repairRank == 1 && it.repairBasis == "OCR_SINGLE_CHARACTER_REPAIR" })
    }
}
