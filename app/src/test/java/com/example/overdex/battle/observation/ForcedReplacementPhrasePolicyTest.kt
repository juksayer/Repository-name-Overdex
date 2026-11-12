package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class ForcedReplacementPhrasePolicyTest {
    @Test fun `complete known phrases survive accent whitespace punctuation case and native glyph confusions`() {
        listOf("Switch in a new Pokémon?", "SWITCH IN A\nNEW POKEMON", "Choose another Pokémon!",
            "Sw1tch in a new Pokem0n?").forEach { text ->
            val resolution = ForcedReplacementPhrasePolicy().observe(text, 1L, listOf("crop", "raw"))
            assertEquals("EXACT_REPLACEMENT_PROMPT", resolution?.basis)
            assertEquals(listOf("crop", "raw"), resolution?.evidenceReferences)
        }
    }

    @Test fun `near complete phrase needs two source frames and cites both unchanged readings`() {
        val policy = ForcedReplacementPhrasePolicy()
        assertNull(policy.observe("Switch in a new Pokmon?", 1L, listOf("first")))
        val result = policy.observe("Switch in a new Pokmon?", 500_000_001L, listOf("second"))
        assertEquals("REPEATED_NEAR_COMPLETE_REPLACEMENT_PROMPT", result?.basis)
        assertEquals(listOf("first", "second"), result?.evidenceReferences)
    }

    @Test fun `fragments battle messages and unrelated selection text are not forced replacement witnesses`() {
        listOf("Switch", "Pokémon", "Choose your team", "Go, Gourgeist!", "Attack incoming!",
            "Use this party", "Switch Pokémon", "NEW POKEMON", "Choose another battle", "").forEach { text ->
            val policy = ForcedReplacementPhrasePolicy()
            assertNull(policy.observe(text, 1, listOf("one")))
            assertNull(policy.observe(text, 500_000_001L, listOf("two")))
        }
    }

    @Test fun `same frame old frame and widely separated fuzzy readings cannot confirm`() {
        val policy = ForcedReplacementPhrasePolicy()
        assertNull(policy.observe("Switch in a new Pokmon?", 1L, listOf("first")))
        assertNull(policy.observe("Switch in a new Pokmon?", 1L, listOf("duplicate")))
        assertNull(policy.observe("Switch in a new Pokmon?", 0L, listOf("old")))
        assertNull(policy.observe("Switch in a new Pokmon?", 2_000_000_001L, listOf("later")))
    }

    @Test fun `one prompt has one witness and a later new assignment can observe another`() {
        val policy = ForcedReplacementPhrasePolicy()
        assertNotNull(policy.observe("Switch in a new Pokémon?", 1L, listOf("one")))
        assertNull(policy.observe("Switch in a new Pokémon?", 500_000_001L, listOf("repeat")))
        assertNotNull(policy.observe("Switch in a new Pokémon?", 4_000_000_001L, listOf("next")))
    }
}
