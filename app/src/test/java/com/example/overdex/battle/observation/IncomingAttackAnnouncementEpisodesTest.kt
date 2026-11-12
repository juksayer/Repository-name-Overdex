package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class IncomingAttackAnnouncementEpisodesTest {
    @Test fun `completion retires outcome visibility without discarding a late historical prompt`() {
        for (completionFirst in listOf(false, true)) {
            val episodes = IncomingAttackAnnouncementEpisodes()
            val outcome = OutcomeShieldPromptEvidence.SOURCE_ID
            if (completionFirst) episodes.observeChargeCompletion(12_000_000_000L)
            assertTrue(episodes.accept(OutcomeShieldPromptEvidence.PROMPT, 10_000_000_000L, outcome))
            if (!completionFirst) episodes.observeChargeCompletion(12_000_000_000L)
            assertFalse(episodes.accept(OutcomeShieldPromptEvidence.PROMPT, 10_000_000_000L, outcome))
            assertFalse("A later reading of the same completed prompt is still a duplicate",
                episodes.accept(OutcomeShieldPromptEvidence.PROMPT, 10_500_000_000L, outcome))
            assertTrue("A later main warning must not wait for the old outcome's six-second expiry",
                episodes.accept("Attack incoming!", 13_000_000_000L))
            episodes.observeChargeCompletion(11_000_000_000L)
            assertFalse("An older completion cannot retire the newer warning",
                episodes.accept("Attack incoming!", 14_000_000_000L, "QTE_PROGRESS_TEXT_WITNESS"))
        }
    }

    @Test fun `first and later opponent charges are witnessed while continuous repeats are not`() {
        val episodes = IncomingAttackAnnouncementEpisodes()
        assertTrue(episodes.accept("Attack incoming!", 12_214_000_000L))
        assertFalse(episodes.accept("Attack incoming!", 12_500_000_000L))
        assertFalse(episodes.accept("Sneasel used Foul Play!", 20_386_000_000L))
        assertTrue(episodes.accept("Attack incoming!", 108_291_000_000L))
    }

    @Test fun `unread intervening crops do not disable all subsequent warnings`() {
        val episodes = IncomingAttackAnnouncementEpisodes()
        assertTrue(episodes.accept("Attack incoming!", 1_000_000_000L))
        assertTrue(episodes.accept("Attack incoming!", 20_000_000_000L))
        assertFalse(episodes.accept("Attack incoming!", 2_000_000_000L))
    }
}
