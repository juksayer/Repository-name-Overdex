package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.DeviceMotionPulseMeasured
import com.example.overdex.battle.custody.ChargeMoveQteVibrationPatternInferred
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
import com.example.overdex.battle.custody.ActiveHpBarMotionCadenceMeasured
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillIncreased
import com.example.overdex.battle.custody.PlayerChargeMoveEnergyFillCadenceMeasured
import com.example.overdex.battle.custody.PlayerTeamSlotConfigured
import com.example.overdex.battle.custody.ActiveHpBarBorderPulseObserved
import com.example.overdex.battle.custody.FastMoveRecipientVisualArtifactMeasured
import com.example.overdex.battle.custody.FastMoveRecipientVisualCadenceMeasured
import com.example.overdex.battle.custody.FastMoveEffectiveness
import com.example.overdex.battle.custody.FastMoveEffectivenessWitnessed
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.FastMoveSoundMeasured
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
    fun `maps current team configuration as reference input`() {
        val payload = PlayerTeamSlotConfigured(
            slot = 2,
            speciesName = "Gourgeist",
            speciesId = 711,
            fastMoveName = "Incinerate",
            chargedMoveNames = listOf("Seed Bomb", "Shadow Ball")
        )

        assertEquals(
            ArchivedPlayerTeamSlotConfigured(2, "Gourgeist", 711, "Incinerate", listOf("Seed Bomb", "Shadow Ball")),
            RealityArticleArchiveMapper.map(article(id = "team-config", payload = payload)).payload
        )
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
    fun `maps raw device motion separately from its charge move qte conclusion`() {
        assertEquals(
            ArchivedDeviceMotionPulseMeasured(80L, 1.2f, 0.7f, 8, "LINEAR_ACCELERATION"),
            RealityArticleArchiveMapper.map(article(
                id = "motion",
                payload = DeviceMotionPulseMeasured(80L, 1.2f, 0.7f, 8)
            )).payload
        )
        assertEquals(
            ArchivedChargeMoveQteVibrationPatternInferred(
                "PLAYER", 3, 2_000_000_000L, "THREE_SHORT_DEVICE_MOTION_PULSES"
            ),
            RealityArticleArchiveMapper.map(article(
                id = "qte",
                payload = ChargeMoveQteVibrationPatternInferred(
                    pulseCount = 3,
                    windowNanos = 2_000_000_000L
                )
            )).payload
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
    fun `maps HP cadence as a measurement rather than a move identity`() {
        val payload = ActiveHpBarMotionCadenceMeasured(
            movingSide = ActivePokemonSide.PLAYER,
            intervalNanos = 1_500_000_000L,
            verticalExcursionPixels = 42f,
            sampleCount = 7
        )

        assertEquals(
            ArchivedActiveHpBarMotionCadenceMeasured("PLAYER", 1_500_000_000L, 42f, 7),
            RealityArticleArchiveMapper.map(article(id = "cadence", payload = payload)).payload
        )
    }

    @Test
    fun `maps player charged control fill evidence and its independent cadence`() {
        assertEquals(
            ArchivedPlayerChargeMoveEnergyFillIncreased(
                listOf(0.1f, 0.2f), listOf(0.2f, 0.3f), listOf(0, 1)
            ),
            RealityArticleArchiveMapper.map(article(
                id = "energy-fill",
                payload = PlayerChargeMoveEnergyFillIncreased(
                    listOf(0.1f, 0.2f), listOf(0.2f, 0.3f), listOf(0, 1)
                )
            )).payload
        )
        assertEquals(
            ArchivedPlayerChargeMoveEnergyFillCadenceMeasured(500_000_000L, listOf(0, 1)),
            RealityArticleArchiveMapper.map(article(
                id = "energy-fill-cadence",
                payload = PlayerChargeMoveEnergyFillCadenceMeasured(500_000_000L, listOf(0, 1))
            )).payload
        )
    }

    @Test
    fun `maps each HP border pulse as an independent measurement`() {
        val payload = ActiveHpBarBorderPulseObserved(
            damagedBarSide = ActivePokemonSide.PLAYER,
            peakColorDistance = 0.42f,
            sampleCount = 6
        )

        assertEquals(
            ArchivedActiveHpBarBorderPulseObserved("PLAYER", 0.42f, 6),
            RealityArticleArchiveMapper.map(article(id = "pulse", payload = payload)).payload
        )
    }

    @Test
    fun `maps recipient visual artifact features without claiming a move type`() {
        val payload = FastMoveRecipientVisualArtifactMeasured(
            damagedSide = ActivePokemonSide.OPPONENT,
            changedPixelFraction = 0.24f,
            meanColorDistance = 0.31f,
            meanRed = 0.8f,
            meanGreen = 0.2f,
            meanBlue = 0.1f,
            centroidX = 0.65f,
            centroidY = 0.42f,
            changedSampleCount = 138
        )

        assertEquals(
            ArchivedFastMoveRecipientVisualArtifactMeasured(
                "OPPONENT", 0.24f, 0.31f, 0.8f, 0.2f, 0.1f, 0.65f, 0.42f, 138
            ),
            RealityArticleArchiveMapper.map(article(id = "recipient-artifact", payload = payload)).payload
        )
    }

    @Test
    fun `maps fast move effectiveness without inventing a damaged side`() {
        val payload = FastMoveEffectivenessWitnessed(
            effectiveness = FastMoveEffectiveness.SUPER_EFFECTIVE,
            damagedSide = null,
            recognizedText = "SUPER EFFECTIVE!"
        )

        assertEquals(
            ArchivedFastMoveEffectivenessWitnessed(
                effectiveness = "SUPER_EFFECTIVE",
                damagedSide = null,
                recognizedText = "SUPER EFFECTIVE!"
            ),
            RealityArticleArchiveMapper.map(article(id = "effectiveness", payload = payload)).payload
        )
    }

    @Test
    fun `maps a fused fast move use with its appearance and evidence kinds`() {
        val payload = FastMoveUseObserved(
            useId = "fast-move-use:pulse-1",
            attackingSide = ActivePokemonSide.OPPONENT,
            damagedSide = ActivePokemonSide.PLAYER,
            appearanceId = "sealeo-appearance",
            attackerSpeciesName = "Sealeo",
            evidenceKinds = listOf("HP_BORDER_PULSE", "RECIPIENT_VISUAL_ARTIFACT")
        )

        assertEquals(
            ArchivedFastMoveUseObserved(
                useId = "fast-move-use:pulse-1",
                attackingSide = "OPPONENT",
                damagedSide = "PLAYER",
                appearanceId = "sealeo-appearance",
                attackerSpeciesName = "Sealeo",
                evidenceKinds = listOf("HP_BORDER_PULSE", "RECIPIENT_VISUAL_ARTIFACT"),
                basis = "FUSED_SIDE_ATTRIBUTED_FAST_MOVE_EVIDENCE"
            ),
            RealityArticleArchiveMapper.map(article(id = "use", payload = payload)).payload
        )
    }

    @Test
    fun `maps hp damage ticks as measurements on the damaged side`() {
        val payload = ActiveHpBarDamageTickMeasured(
            damagedSide = ActivePokemonSide.PLAYER,
            beforeFraction = 0.82f,
            afterFraction = 0.68f,
            lostFraction = 0.14f
        )

        assertEquals(
            ArchivedActiveHpBarDamageTickMeasured("PLAYER", 0.82f, 0.68f, 0.14f),
            RealityArticleArchiveMapper.map(article(id = "hp-tick", payload = payload)).payload
        )
    }

    @Test
    fun `maps recipient visual cadence independently from artifact appearance`() {
        val payload = FastMoveRecipientVisualCadenceMeasured(
            damagedSide = ActivePokemonSide.PLAYER,
            intervalNanos = 500_000_000L
        )

        assertEquals(
            ArchivedFastMoveRecipientVisualCadenceMeasured("PLAYER", 500_000_000L),
            RealityArticleArchiveMapper.map(article(id = "recipient-artifact-cadence", payload = payload)).payload
        )
    }

    @Test
    fun `maps fast move sound metadata without presenting it as a move identity`() {
        val payload = FastMoveSoundMeasured(
            audible = true,
            onsetOffsetNanos = 500_000_000L,
            soundDurationNanos = 220_000_000L,
            spectralCentroidHz = 930f,
            peakAmplitude = 0.43f
        )

        assertEquals(
            ArchivedFastMoveSoundMeasured(true, 500_000_000L, 220_000_000L, 930f, 0.43f),
            RealityArticleArchiveMapper.map(article(id = "fast-sound", payload = payload)).payload
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
                ),
                ArchivedRealityArticle(
                    articleId = "team-1",
                    matchId = "match-a",
                    perceivedAt = 101L,
                    recordedAt = 111L,
                    sourceId = "CURRENT_TEAM_CONFIGURATION",
                    payload = ArchivedPlayerTeamSlotConfigured(
                        1, "Gourgeist", 711, "Incinerate", listOf("Seed Bomb", "Shadow Ball")
                    ),
                    predecessorIds = emptyList(),
                    confidence = null,
                    sequenceNumber = 18L,
                    evidenceReferences = emptyList()
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
