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
}
