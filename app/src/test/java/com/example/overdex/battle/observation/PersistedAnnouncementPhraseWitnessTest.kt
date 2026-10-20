package com.example.overdex.battle.observation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistedAnnouncementPhraseWitnessTest {
    @Test fun `clipped get ready text is accepted without confusing Great`() {
        assertTrue(announcementContainsPhrase("Get Rea", "GETREADY"))
        assertTrue(announcementContainsPhrase("GET READY!", "GETREADY"))
        assertFalse(announcementContainsPhrase("GREAT!", "GETREADY"))
    }

    @Test fun `October 19 damaged charge cues are accepted without matching other battle text`() {
        assertTrue(announcementContainsPhrase("Ge Keady", "GETREADY"))
        assertTrue(announcementContainsPhrase("Get Kedu", "GETREADY"))
        listOf("GREAT LEAGUE", "GREAT", "Go, Regice!", "Get your team", "Weather Ball", "Energy Ball", "ready")
            .forEach { assertFalse(it, announcementContainsPhrase(it, "GETREADY")) }
    }
}
