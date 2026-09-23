package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.MatchRecordStarted
import com.example.overdex.battle.custody.VsScreenWitnessed
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.custody.WitnessOperating
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonTypesWitnessed
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.model.PokemonType
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
            evidenceReferences = listOf("frame-17"),
            monotonicTimeNanos = 3_000_000L
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
        assertEquals(3_000_000L, archived.monotonicTimeNanos)
    }

    @Test
    fun `maps supported testimony payloads without reclassifying them`() {
        val recordStarted = RealityArticleArchiveMapper.map(
            article(id = "record-started", payload = MatchRecordStarted)
        )
        val vsScreen = RealityArticleArchiveMapper.map(
            article(id = "vs-screen", payload = VsScreenWitnessed)
        )
        val attack = RealityArticleArchiveMapper.map(
            article(id = "attack", payload = AttackIncoming)
        )
        val species = RealityArticleArchiveMapper.map(
            article(id = "species", payload = PokemonIdentified("Sneasel"))
        )
        val rawInt = RealityArticleArchiveMapper.map(
            article(id = "raw-int", payload = RawTestimony(25))
        )
        val matchStarted = RealityArticleArchiveMapper.map(
            article(id = "match-started", payload = MatchStarted)
        )

        assertEquals(ArchivedMatchRecordStarted, recordStarted.payload)
        assertEquals(ArchivedVsScreenWitnessed, vsScreen.payload)
        assertEquals(ArchivedAttackIncoming, attack.payload)
        assertEquals(ArchivedPokemonIdentified("Sneasel"), species.payload)
        assertEquals(ArchivedRawInt(25), rawInt.payload)
        assertEquals(ArchivedMatchStarted, matchStarted.payload)
    }

    @Test
    fun `maps witness operating coverage without treating it as an observation`() {
        val article = article(id = "coverage", payload = WitnessOperating(operating = true))

        assertEquals(ArchivedWitnessOperating(operating = true), RealityArticleArchiveMapper.map(article).payload)
    }

    @Test
    fun `maps typed announcement testimony without replacing its crop evidence`() {
        assertEquals(
            ArchivedGetReadyWitnessed,
            RealityArticleArchiveMapper.map(article(id = "get-ready", payload = GetReadyWitnessed)).payload
        )
        assertEquals(
            ArchivedChargeMoveUsedAnnounced,
            RealityArticleArchiveMapper.map(article(id = "charge-used", payload = ChargeMoveUsedAnnounced)).payload
        )
    }

    @Test
    fun `maps active type icon testimony with its original side and measurement`() {
        val payload = ActivePokemonTypesWitnessed(
            side = ActivePokemonSide.OPPONENT,
            types = listOf(PokemonType.DARK, PokemonType.ICE),
            similarity = 0.91f
        )

        assertEquals(
            ArchivedActivePokemonTypesWitnessed(
                side = "OPPONENT",
                types = listOf("DARK", "ICE"),
                similarity = 0.91f,
                basis = "POKEMON_GO_TYPE_ICON_REFERENCE_MATCH"
            ),
            RealityArticleArchiveMapper.map(article(id = "types", payload = payload)).payload
        )
    }

    @Test
    fun `maps active HP evidence without turning it into a faint or move claim`() {
        val payload = ActiveHpBarMeasured(
            side = ActivePokemonSide.OPPONENT,
            barLeft = 0,
            barTop = 775,
            barRight = 301,
            barBottom = 804,
            filledFraction = 0.71f
        )

        assertEquals(
            ArchivedActiveHpBarMeasured(
                side = "OPPONENT",
                barLeft = 0,
                barTop = 775,
                barRight = 301,
                barBottom = 804,
                filledFraction = 0.71f
            ),
            RealityArticleArchiveMapper.map(article(id = "active-hp", payload = payload)).payload
        )
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

    @Test
    fun `archive JSON round trips without changing records`() {
        val archive = MatchArchive(
            matchId = "match-a",
            articles = listOf(
                ArchivedRealityArticle(
                    articleId = "article-1",
                    matchId = "match-a",
                    perceivedAt = 100L,
                    recordedAt = 110L,
                    sourceId = "SPECIES_WITNESS",
                    payload = ArchivedPokemonIdentified("Vaporeon"),
                    predecessorIds = listOf("predecessor-1"),
                    confidence = 0.85f,
                    sequenceNumber = 17L,
                    evidenceReferences = listOf("frame-17")
                )
            )
        )

        val restored = MatchArchiveSerializer.deserialize(
            MatchArchiveSerializer.serialize(archive)
        )

        assertEquals(archive, restored)
    }

    @Test
    fun `archive JSON uses kind as payload discriminator`() {
        val archive = MatchArchive(
            matchId = "match-a",
            articles = listOf(
                archivedArticle(payload = ArchivedAttackIncoming)
            )
        )

        val json = MatchArchiveSerializer.serialize(archive)

        assertTrue(json.contains("\"kind\": \"attack_incoming\""))
    }

    @Test
    fun `serializer rejects unsupported schema version`() {
        val archive = MatchArchive(
            schemaVersion = 2,
            matchId = "match-a",
            articles = emptyList()
        )

        assertIllegalArgument("Unsupported schema version") {
            MatchArchiveSerializer.serialize(archive)
        }
    }

    @Test
    fun `deserializer rejects unsupported schema version`() {
        val futureSchemaJson = """
            {
              "schemaVersion": 2,
              "matchId": "match-a",
              "articles": []
            }
        """.trimIndent()

        assertIllegalArgument("Unsupported schema version") {
            MatchArchiveSerializer.deserialize(futureSchemaJson)
        }
    }

    private fun article(
        id: String,
        payload: TestimonyPayload,
        matchId: MatchId? = matchA,
        predecessorIds: List<ArticleId> = emptyList(),
        confidence: Float? = null,
        sequenceNumber: Long? = null,
        evidenceReferences: List<String>? = null,
        monotonicTimeNanos: Long? = null
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
        matchId = matchId,
        monotonicTimeNanos = monotonicTimeNanos
    )

    private fun archivedArticle(
        payload: ArchivedTestimonyPayload
    ) = ArchivedRealityArticle(
        articleId = "article-1",
        matchId = "match-a",
        perceivedAt = 100L,
        recordedAt = 110L,
        sourceId = "SPECIES_WITNESS",
        payload = payload,
        predecessorIds = emptyList(),
        confidence = null,
        sequenceNumber = null,
        evidenceReferences = null
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
