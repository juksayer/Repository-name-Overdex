package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchOutcomeTextResolverTest {
    @Test fun `win accepts missing OCR whitespace and punctuation`() {
        assertEquals(MatchOutcomePhrase.WIN, MatchOutcomeTextResolver.resolve("YOUWIN!"))
        assertEquals(MatchOutcomePhrase.WIN, MatchOutcomeTextResolver.resolve("YOU WIN"))
        assertEquals(MatchOutcomePhrase.WIN, MatchOutcomeTextResolver.resolve("YOUWN"))
        assertEquals(MatchOutcomePhrase.WIN, MatchOutcomeTextResolver.resolve("YOU WVIN!"))
    }

    @Test fun `loss accepts missing OCR whitespace`() {
        assertEquals(MatchOutcomePhrase.LOSS, MatchOutcomeTextResolver.resolve("GOODEFFORT"))
        assertEquals(MatchOutcomePhrase.LOSS, MatchOutcomeTextResolver.resolve("GOOD EFFORT"))
    }

    @Test fun `outcome phrase rejects appended unrelated words`() {
        assertNull(MatchOutcomeTextResolver.resolve("YOU WIN SOMETHING"))
    }
}
