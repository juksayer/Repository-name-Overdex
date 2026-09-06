package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RealityArticleArchiveMapperTest {

    private val matchA = MatchId("match-a")
    private val matchB = MatchId("match-b")

    @Test
    fun `maps raw text while preserving article provenance`() {
        val article = article(
            id = "article-1",
            payload = RawTestimony("Vaporeon"),
            predecessorIds = listOf(ArticleId("predecessor-1")),
            confidence = 0.85f,
            sequenceNumber = 17L,
            evidenceReferences = listOf("frame-17")
        )

        val archived = RealityArticleArchiveMapper.map(article)

        assertEquals("article-1", archived.articleId)
        assertEquals("match-a", archived.matchId)
        assertEquals(100L, archived.perceivedAt)
        assertEquals(110L, archived.recordedAt)
        assertEquals("SPECIES_WITNESS", archived.sourceId)
        assertEquals(ArchivedRawText("Vaporeon"), archived.payload)
        assertEquals(listOf("predecessor-1"), archived.predecessorIds)
        assertEquals(0.85f, archived.confidence)
        assertEquals(17L, archived.sequenceNumber)
        assertEquals(listOf("frame-17"), archived.evidenceReferences)
    }

    @Test
    fun `maps supported testimony payloads without reclassifying them`() {
        val attack = RealityArticleArchiveMapper.map(
            article(id = "attack", payload = AttackIncoming)
        )
        val species = RealityArticleArchiveMapper.map(
            article(id = "species", payload = PokemonIdentified("Sneasel"))
        )
        val rawInt = RealityArticleArchiveMapper.map(
            article(id = "raw-int", payload = RawTestimony(25))
        )

        assertEquals(ArchivedAttackIncoming, attack.payload)
        assertEquals(ArchivedPokemonIdentified("Sneasel"), species.payload)
        assertEquals(ArchivedRawInt(25), rawInt.payload)
    }

    @Test
    fun `creates archive only from articles belonging to requested match`() {
        val first = article(id = "first", payload = RawTestimony("One"))
        val second = article(id = "second", payload = RawTestimony("Two"))

        val archive = RealityArticleArchiveMapper.createArchive(
            matchId = matchA,
            articles = listOf(first, second)
        )

        assertEquals(1, archive.schemaVersion)
        assertEquals("match-a", archive.matchId)
        assertEquals(listOf("first", "second"), archive.articles.map { it.articleId })
    }

    @Test
    fun `rejects article without match membership`() {
        val article = article(
            id = "orphan",
            matchId = null,
            payload = RawTestimony("Unknown")
        )

        assertIllegalArgument("null matchId") {
            RealityArticleArchiveMapper.map(article)
        }
    }

    @Test
    fun `rejects articles belonging to another match`() {
        val foreignArticle = article(
            id = "foreign",
            matchId = matchB,
            payload = RawTestimony("Foreign")
        )

        assertIllegalArgument("does not match requested matchId") {
            RealityArticleArchiveMapper.createArchive(
                matchId = matchA,
                articles = listOf(foreignArticle)
            )
        }
    }

    @Test
    fun `rejects unsupported raw testimony without flattening it`() {
        val article = article(
            id = "unsupported",
            payload = RawTestimony(12345L)
        )

        assertIllegalArgument("Unsupported RawTestimony data type") {
            RealityArticleArchiveMapper.map(article)
        }
    }

    private fun article(
        id: String,
        payload: TestimonyPayload,
        matchId: MatchId? = matchA,
        predecessorIds: List<ArticleId> = emptyList(),
        confidence: Float? = null,
        sequenceNumber: Long? = null,
        evidenceReferences: List<String>? = null
    ) = RealityArticle(
        id = ArticleId(id),
        perceivedAt = 100L,
        recordedAt = 110L,
        sourceId = SourceId("SPECIES_WITNESS"),
        payload = payload,
        predecessorIds = predecessorIds,
        confidence = confidence,
        sequenceNumber = sequenceNumber,
        evidenceReferences = evidenceReferences,
        matchId = matchId
    )

    private fun assertIllegalArgument(
        expectedMessagePart: String,
        action: () -> Unit
    ) {
        try {
            action()
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message?.contains(expectedMessagePart) == true)
        }
    }
}