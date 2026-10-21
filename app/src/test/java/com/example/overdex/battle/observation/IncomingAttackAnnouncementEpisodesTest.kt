package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class IncomingAttackAnnouncementEpisodesTest {
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
