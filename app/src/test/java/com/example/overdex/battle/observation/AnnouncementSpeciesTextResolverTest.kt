package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnouncementSpeciesTextResolverTest {
    private val speciesNames = setOf("TAUROS", "TURTONATOR", "VAPOREON")

    @Test fun `resolves the performer from a used move announcement`() {
        assertEquals(
            "TURTONATOR",
            AnnouncementSpeciesTextResolver.resolveUsedMovePerformer(
                "Turtonator used Dragon Pulse!",
                speciesNames
            )
        )
    }

    @Test fun `tolerates OCR error only inside the performer field`() {
        assertEquals(
            "TURTONATOR",
            AnnouncementSpeciesTextResolver.resolveUsedMovePerformer(
                "Turt0nator used Dragon Pulse!",
                speciesNames
            )
        )
    }

    @Test fun `does not infer Tauros from HUD text overlapping the crop`() {
        assertNull(
            AnnouncementSpeciesTextResolver.resolveUsedMovePerformer(
                "GREAT\nAurora Beam\nBody Slam\nWater Pulse\nSurf",
                speciesNames
            )
        )
    }

    @Test fun `leaves entry announcements to the entry witness`() {
        assertNull(
            AnnouncementSpeciesTextResolver.resolveUsedMovePerformer(
                "Go, Vaporeon!",
                speciesNames
            )
        )
    }
}
