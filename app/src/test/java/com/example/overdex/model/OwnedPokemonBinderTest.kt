package com.example.overdex.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedPokemonBinderTest {
    private val card = OwnedPokemon(
        id = "owned-6",
        speciesId = 6,
        isFavorite = true,
        isShadow = true,
        isShiny = false,
    )

    @Test
    fun `binder views reference the same owned card`() {
        val allCard = listOf(card).filter(OwnedPokemonBinder.ALL::contains).single()
        val favoriteCard = listOf(card).filter(OwnedPokemonBinder.FAVORITES::contains).single()
        val shadowCard = listOf(card).filter(OwnedPokemonBinder.SHADOW::contains).single()

        assertSame(card, allCard)
        assertSame(card, favoriteCard)
        assertSame(card, shadowCard)
        assertFalse(OwnedPokemonBinder.SHINY.contains(card))
    }

    @Test
    fun `unknown binder route safely opens all owned`() {
        assertTrue(OwnedPokemonBinder.fromRouteKey("missing") == OwnedPokemonBinder.ALL)
    }
}
