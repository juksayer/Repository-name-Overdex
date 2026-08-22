package com.example.overdex.battle.interpretation

import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.BattleActor
import com.example.overdex.model.BattleEventType
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleInterpreterTest {

    private val pokemonKnowledge = object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String): Pokemon? =
            when (name) {
                "Pikachu" -> Pokemon(
                    id = 25,
                    name = "Pikachu",
                    types = emptyList(),
                    region = "",
                    fastMoves = emptyList(),
                    chargedMoves = emptyList()
                )

                "Raichu" -> Pokemon(
                    id = 26,
                    name = "Raichu",
                    types = emptyList(),
                    region = "",
                    fastMoves = emptyList(),
                    chargedMoves = emptyList()
                )

                else -> null
            }

        override suspend fun getPokemonById(id: Int): Pokemon? =
            when (id) {
                25 -> getPokemonByName("Pikachu")
                26 -> getPokemonByName("Raichu")
                else -> null
            }
    }

    private val interpreter = BattleInterpreter(pokemonKnowledge)
    @Test
    fun `interprets species witness testimony as pokemon identified event`() {
        val perceivedAt = 123456789L
        val article = RealityArticle(
            id = ArticleId("A1"),
            perceivedAt = perceivedAt,
            recordedAt = System.currentTimeMillis(),
            sourceId = SourceId("SPECIES_WITNESS"),
            payload = RawTestimony("Pikachu")
        )

        val event = runBlocking {
            interpreter.interpret(article)
        }

        assertEquals(BattleEventType.POKEMON_IDENTIFIED, event?.type)
        assertEquals(BattleActor.ENEMY, event?.actor)
        assertEquals("Pikachu", event?.message)
        assertEquals(25, event?.pokemonId)
        assertEquals(perceivedAt, event?.timestamp)
        assertEquals(ArticleId("A1"), event?.sourceArticleId)
    }

    @Test
    fun `returns null for unrelated source`() {
        val article = RealityArticle(
            id = ArticleId("A2"),
            perceivedAt = 100L,
            recordedAt = 200L,
            sourceId = SourceId("DROIDBALL_SERVICE"),
            payload = RawTestimony("Started")
        )

        val event = runBlocking {
            interpreter.interpret(article)
        }

        assertNull(event)
    }

    @Test
    fun `returns null for unsupported payload data type`() {
        val article = RealityArticle(
            id = ArticleId("A3"),
            perceivedAt = 100L,
            recordedAt = 200L,
            sourceId = SourceId("SPECIES_WITNESS"),
            payload = RawTestimony(12345) // Int instead of String
        )

        val event = runBlocking {
            interpreter.interpret(article)
        }

        assertNull(event)
    }

    @Test
    fun `is stateless and produces independent results`() {
        val article1 = RealityArticle(
            id = ArticleId("A1"),
            perceivedAt = 100L,
            recordedAt = 200L,
            sourceId = SourceId("SPECIES_WITNESS"),
            payload = RawTestimony("Pikachu")
        )
        val article2 = RealityArticle(
            id = ArticleId("A2"),
            perceivedAt = 300L,
            recordedAt = 400L,
            sourceId = SourceId("SPECIES_WITNESS"),
            payload = RawTestimony("Raichu")
        )

        val event1 = runBlocking {
            interpreter.interpret(article1)
        }
        val event2 = runBlocking {
            interpreter.interpret(article2)
        }

        assertEquals("Pikachu", event1?.message)
        assertEquals("Raichu", event2?.message)
        assertEquals(25, event1?.pokemonId)
        assertEquals(26, event2?.pokemonId)
    }
}
