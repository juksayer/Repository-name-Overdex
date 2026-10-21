package com.example.overdex.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NicknameTokensTest {
    @Test fun `legacy and current type tokens render the same icons`() {
        val text = "Ember :firetype: TYPE:WATER :TYPE:GRASS: :ELECTRIC:"
        val tokens = NicknameTokens.tokens(text)
        assertEquals(listOf(PokemonType.FIRE, PokemonType.WATER, PokemonType.GRASS, PokemonType.ELECTRIC), tokens.map { it.type })
        assertEquals(listOf(":firetype:", "TYPE:WATER", ":TYPE:GRASS:", ":ELECTRIC:"), tokens.map { text.substring(it.range) })
        assertEquals(emptyList<NicknameTokens.Token>(), NicknameTokens.tokens("TYPE:FIREBALL :unknown: FIRE"))
    }

    @Test fun `backspace removes a whole type token or a whole unicode character`() {
        var text = NicknameTokens.applyKey("Ember", "TYPE:FIRE")
        assertEquals("Ember:FIRE:", text)
        text = NicknameTokens.applyKey(text, "DELETE")
        assertEquals("Ember", text)
        assertEquals("Ember", NicknameTokens.applyKey("Ember🔥", "DELETE"))
        assertEquals("Ember", NicknameTokens.applyKey("Ember:firetype:", "DELETE"))
        assertEquals("", NicknameTokens.applyKey("", "DELETE"))
        assertEquals("Ember", NicknameTokens.applyKey("Ember", "#"))
    }
}
