package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FastOverlaySpeciesReadingResolverTest {
    @Test fun `formal selection reuses rejected and accepted OCR treatment resolutions`() {
        val calls = mutableListOf<String>()
        val accepted = FastOverlaySpeciesResolution("Blastoise", 0.82f, "UNAMBIGUOUS_CATALOGUE_FUZZY_MATCH")
        val resolver = FastOverlaySpeciesReadingResolver { text ->
            calls += text
            accepted.takeIf { text == "Blastolse" }
        }

        // The first OCR treatment fails, so the recognizer tries the next one.
        assertNull(resolver.resolve("CP 1499"))
        assertSame(accepted, resolver.resolve("Blastolse"))
        // Formal publication traverses those same candidates in reading order.
        val selected = listOf("CP 1499", "Blastolse").firstNotNullOfOrNull(resolver::resolve)

        assertSame(accepted, selected)
        assertEquals(listOf("CP 1499", "Blastolse"), calls)
    }

    @Test fun `candidate cache preserves typography confidence and fuzzy repair`() {
        val names = setOf("Blastoise", "Pancham", "Steelix")
        val resolver = FastOverlaySpeciesReadingResolver { FastOverlaySpeciesResolver.resolveDetailed(it, names) }

        assertEquals(
            FastOverlaySpeciesResolution("Blastoise", 0.82f, "UNAMBIGUOUS_CATALOGUE_FUZZY_MATCH"),
            resolver.resolve("Blastolse"),
        )
        assertEquals(
            FastOverlaySpeciesResolution("Blastoise", 0.98f, "EXACT_CATALOGUE_TEXT_EXPECTED_CASE"),
            resolver.resolve("Blastoise"),
        )
        assertEquals(
            "EXACT_CATALOGUE_TEXT_CASE_NORMALIZED",
            resolver.resolve("BLASTOISE")?.basis,
        )
        assertSame(resolver.resolve("Blastolse"), resolver.resolve("Blastolse"))
    }

    @Test fun `ambiguous candidates remain rejected and caches do not cross crops`() {
        val ambiguousNames = setOf("Porygon", "Porygon2", "Porygon-Z")
        val firstCrop = FastOverlaySpeciesReadingResolver {
            FastOverlaySpeciesResolver.resolveDetailed(it, ambiguousNames)
        }
        val nextCrop = FastOverlaySpeciesReadingResolver {
            FastOverlaySpeciesResolver.resolveDetailed(it, setOf("Porygon"))
        }

        assertNull(firstCrop.resolve("Porygo"))
        assertNull(firstCrop.resolve("Porygo"))
        assertEquals("Porygon", nextCrop.resolve("Porygo")?.speciesName)
    }
}
