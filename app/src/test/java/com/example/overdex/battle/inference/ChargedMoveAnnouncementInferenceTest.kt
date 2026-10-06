package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargedMoveAnnouncementInferenceTest {
    private val pokemon = Pokemon(
        id = 776,
        name = "Turtonator",
        types = listOf(PokemonType.FIRE),
        region = "Alola",
        fastMoves = emptyList(),
        chargedMoves = listOf(
            Move("Dragon Pulse", PokemonType.DRAGON, 90, 60, isFast = false)
        )
    )
    private val inference = ChargedMoveAnnouncementInference(SinglePokemonKnowledge(pokemon))

    @Test fun `named announced charged move cites its Pokedex energy cost`() = runBlocking {
        inference.accept(species("active", ActivePokemonSide.PLAYER), ::noRosterSide)

        val result = inference.accept(announcement("move", "Turtonator used Dragon Pulse!"), ::noRosterSide)

        requireNotNull(result)
        assertEquals(ActivePokemonSide.PLAYER, result.payload.side)
        assertEquals("Dragon Pulse", result.payload.moveName)
        assertEquals(60, result.payload.energyCost)
        assertEquals(listOf(ArticleId("move")), result.predecessorIds)
    }

    @Test fun `mirror active species does not invent the charged move performer`() = runBlocking {
        val mirrorInference = ChargedMoveAnnouncementInference(SinglePokemonKnowledge(pokemon))
        mirrorInference.accept(species("player", ActivePokemonSide.PLAYER), ::noRosterSide)
        mirrorInference.accept(species("opponent", ActivePokemonSide.OPPONENT), ::noRosterSide)

        assertNull(mirrorInference.accept(announcement("move", "Turtonator used Dragon Pulse!"), ::noRosterSide))
    }

    private fun noRosterSide(@Suppress("UNUSED_PARAMETER") name: String): ActivePokemonSide? = null

    private fun species(id: String, side: ActivePokemonSide) = article(
        id, SourceId("SPECIES"), ActivePokemonSpeciesWitnessed(side, "Turtonator", 776)
    )

    private fun announcement(id: String, text: String) = article(
        id, SourceId("ANNOUNCEMENT_WITNESS"), RawTestimony(text)
    )

    private fun article(id: String, sourceId: SourceId, payload: com.example.overdex.battle.custody.TestimonyPayload) = RealityArticle(
        id = ArticleId(id), perceivedAt = 1, recordedAt = 1, sourceId = sourceId,
        payload = payload, monotonicTimeNanos = 1
    )

    private class SinglePokemonKnowledge(private val pokemon: Pokemon) : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String): Pokemon? = pokemon.takeIf { it.name == name }
        override suspend fun getPokemonById(id: Int): Pokemon? = pokemon.takeIf { it.id == id }
        override suspend fun getAllSpeciesNames(): Set<String> = setOf(pokemon.name)
    }
}
