package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryAnnouncementSpeciesTextResolverTest {
    private val species = setOf("Turtonator", "Sneasel", "Camerupt")

    @Test fun `reads only the species field from an entry announcement`() {
        assertEquals("Sneasel", EntryAnnouncementSpeciesTextResolver.resolve("Go, Sneasel!", species))
    }

    @Test fun `tolerates a small OCR error in the species field`() {
        assertEquals("Turtonator", EntryAnnouncementSpeciesTextResolver.resolve("Go, Turtoator!", species))
    }

    @Test fun `rejects text that is not an entry announcement`() {
        assertNull(EntryAnnouncementSpeciesTextResolver.resolve("Sneasel used Avalanche!", species))
    }
}
