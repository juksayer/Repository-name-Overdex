package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.ArchivedActiveHpBarMeasured
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.ArchivedPlayerActiveIdentityResolved
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonFaintedWitnessed
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.FastMoveUseObserved
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.model.BattleResult
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Original retained pixels and OCR prove a phase cue, never the unrecorded final HP/faint. */
class OutcomeShieldPromptHandoffTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `recorded prompt prepares fresh Camerupt geometry at native completion with capture based expiry`() {
        val fixture = RecordedFixture()
        val hpArticle = fixture.article("c1ec7dd1-e085-448b-8cc2-0d719b93cd9f")
        val hp = hpArticle.payload as ArchivedActiveHpBarMeasured
        val identity = fixture.article("87d0b788-1d80-47bf-b7fe-fb4b734191b7").payload as
            ArchivedPlayerActiveIdentityResolved
        assertEquals("Camerupt", identity.speciesName)
        assertEquals(2, identity.teamSlot)
        assertEquals(357_628_615L, CAPTURE_AT - requireNotNull(hpArticle.monotonicTimeNanos))
        assertTrue(requireNotNull(hpArticle.monotonicTimeNanos) > identity.appearanceStartedAtNanos)
        val reference = ActiveHpBarMeasurement(hp.barLeft, hp.barTop, hp.barRight, hp.barBottom,
            hp.filledFraction, requireNotNull(hpArticle.confidence))
        val policy = ChargedImpactHpWatchPolicy().apply {
            request(672, 1493, requireNotNull(hpArticle.monotonicTimeNanos) - 1L)
            reset(ActivePokemonSide.PLAYER, identity.appearanceStartedAtNanos)
            seed(ActivePokemonSide.PLAYER, reference, requireNotNull(hpArticle.monotonicTimeNanos))
        }
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        val handoff = AcceptedChargeCapturePreparation(MATCH_ID)
        handoff.attach { cue ->
            cues += cue
            assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.PREPARED,
                policy.prepare(ActivePokemonSide.PLAYER, cue.capturedAtNanos, cue.cropArticleId))
        }

        handoff.offerOutcomePrompt(fixture.crop, fixture.reading, NATIVE_COMPLETION_AT)

        assertEquals(listOf(AcceptedChargeCaptureCue(AcceptedChargeCaptureCueKind.ATTACK_INCOMING,
            CROP_ID, CAPTURE_AT, NATIVE_COMPLETION_AT, fixture.crop.perceivedAt)), cues)
        assertEquals(144_571_292_316L, NATIVE_COMPLETION_AT - MATCH_START_AT)
        val request = requireNotNull(policy.request(672, 1493, NATIVE_COMPLETION_AT))
        assertEquals(ActivePokemonSide.PLAYER, request.side)
        assertEquals(reference, request.reference)
        assertEquals(listOf(CROP_ID), request.cueArticleIds)
        assertNull("Preparation has not measured an impact bar", request.anchor)
        assertEquals(.7195767f, request.reference.filledFraction, 0f)

        handoff.offerOutcomePrompt(fixture.crop, fixture.reading, NATIVE_COMPLETION_AT + 9_000_000L)
        assertEquals(1, cues.size)
        assertEquals(ChargedImpactHpWatchPolicy.PreparationResult.DUPLICATE_OR_OLDER,
            policy.prepare(ActivePokemonSide.PLAYER, CAPTURE_AT, "later-canonical-warning"))
        assertTrue(policy.canCapture(CAPTURE_AT + 12_000_000_000L))
        assertFalse("Recognition/publication must not renew the original twelve-second window",
            policy.canCapture(CAPTURE_AT + 12_000_000_001L))
        assertNotNull(policy.request(672, 1493, CAPTURE_AT + 12_000_000_000L))
        assertNull(policy.request(672, 1493, CAPTURE_AT + 12_000_000_001L))

        val finalCharge = fixture.article("ac929ce2-743a-451c-89a1-f5d38cd2a8cf")
        assertTrue("This recording does not prove recognition completed before the final attack",
            NATIVE_COMPLETION_AT > requireNotNull(finalCharge.monotonicTimeNanos))
        val lastHp = fixture.article("f9f6127e-1f51-4f6a-b134-043ff1a4e09a").payload as ArchivedActiveHpBarMeasured
        assertEquals("No zero HP is supplied by this fixture", .7195767f, lastHp.filledFraction, 0f)
    }

    @Test fun `recorded crop and native word boxes remain byte exact`() {
        val fixture = RecordedFixture()
        val crop = fixture.crop.payload as CropCaptured
        val png = resourceBytes("no-shields.png")
        assertEquals(crop.artifact.sha256, sha256(png))
        assertEquals(crop.artifact.byteCount, png.size.toLong())
        assertEquals(BattleCropBounds(100, 641, 564, 858), crop.cropProvenance.bounds)
        assertEquals("RAW", fixture.reading.treatment)
        assertEquals(PROMPT, fixture.reading.rawText)
        assertEquals(PROMPT, fixture.reading.acceptedText)
        assertEquals(listOf(OcrBox(68f, 55f, 99f, 84f), OcrBox(110f, 55f, 191f, 84f),
            OcrBox(200f, 55f, 278f, 84f), OcrBox(288f, 55f, 404f, 84f)),
            fixture.reading.words.map { it.bounds })
        assertEquals(listOf(CROP_ID), fixture.raw.evidenceReferences)
        assertEquals(fixture.crop.monotonicTimeNanos, fixture.raw.monotonicTimeNanos)
        assertEquals(fixture.crop.perceivedAt, fixture.raw.perceivedAt)
    }

    @Test fun `newer invalid or blank outcome readings cannot suppress the older validated prompt`() {
        val fixture = RecordedFixture()
        val handoff = AcceptedChargeCapturePreparation(MATCH_ID)
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        handoff.attach(cues::add)
        val newer = fixture.crop.copy(id = ArticleId("newer-crop"),
            monotonicTimeNanos = CAPTURE_AT + 1_000_000_000L)
        val invalid = listOf(
            fixture.reading.copy(words = emptyList()),
            fixture.reading.copy(words = fixture.reading.words.map { it.copy(bounds = null) }),
            fixture.reading.copy(rawText = "", acceptedText = "", words = emptyList()),
            fixture.reading.copy(acceptedText = "Get Ready!"),
            fixture.reading.copy(acceptedText = "EXCELLENT!"),
        )
        invalid.forEach { handoff.offerOutcomePrompt(newer, it, NATIVE_COMPLETION_AT) }
        assertTrue(cues.isEmpty())
        handoff.offerOutcomePrompt(fixture.crop, fixture.reading, NATIVE_COMPLETION_AT)
        assertEquals(listOf(CROP_ID), cues.map { it.cropArticleId })
        assertEquals(listOf(CAPTURE_AT), cues.map { it.capturedAtNanos })
    }

    @Test fun `announcement and outcome handoffs share one incoming episode in either delivery order`() {
        val fixture = RecordedFixture()
        for (outcomeFirst in listOf(false, true)) {
            val handoff = AcceptedChargeCapturePreparation(MATCH_ID)
            val cues = mutableListOf<AcceptedChargeCaptureCue>()
            handoff.attach(cues::add)
            val captured = fixture.crop.payload as CropCaptured
            val announcement = fixture.crop.copy(id = ArticleId("announcement-crop"),
                monotonicTimeNanos = CAPTURE_AT - 100_000_000L,
                payload = captured.copy(cropProvenance = captured.cropProvenance.copy(
                    cropName = BattleCropContracts.announcement.cropName)))
            fun offerAnnouncement() = handoff.offer("ANNOUNCEMENT_WITNESS", BattleCropContracts.announcement,
                announcement, "Attack incoming!", NATIVE_COMPLETION_AT)
            if (!outcomeFirst) offerAnnouncement()
            handoff.offerOutcomePrompt(fixture.crop, fixture.reading, NATIVE_COMPLETION_AT)
            if (outcomeFirst) offerAnnouncement()
            assertEquals("delivery order outcomeFirst=$outcomeFirst", 1, cues.size)
            assertEquals(AcceptedChargeCaptureCueKind.ATTACK_INCOMING, cues.single().kind)
        }
    }

    @Test fun `closed match handoff cannot reopen for the recorded prompt`() {
        val fixture = RecordedFixture()
        val cues = mutableListOf<AcceptedChargeCaptureCue>()
        val handoff = AcceptedChargeCapturePreparation(MATCH_ID)
        handoff.attach(cues::add)
        handoff.close()
        handoff.offerOutcomePrompt(fixture.crop, fixture.reading, NATIVE_COMPLETION_AT)
        handoff.attach(cues::add)
        handoff.offerOutcomePrompt(fixture.crop, fixture.reading, NATIVE_COMPLETION_AT)
        assertTrue(cues.isEmpty())
    }

    @Test fun `recorded raw and threshold passes produce one canonical warning with both source references`() = runBlocking {
        val fixture = RecordedFixture()
        val live = LiveWitness(fixture)
        try {
            live.submit(fixture.raw)
            live.submit(fixture.threshold)
            live.submit(fixture.raw)
            live.submitText("ANNOUNCEMENT_WITNESS", "Attack incoming!", CAPTURE_AT + 100_000_000L)
            live.submitText("QTE_PROGRESS_TEXT_WITNESS", "Use a Protect Shield?", CAPTURE_AT + 200_000_000L)
            live.drainWitness()
            val articles = live.timeline.getArticles()
            val originals = articles.filter { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" }
            assertEquals(listOf(fixture.raw.payload, fixture.threshold.payload, fixture.raw.payload),
                originals.map { it.payload })
            originals.forEach {
                assertEquals(CAPTURE_AT, it.monotonicTimeNanos)
                assertEquals(fixture.crop.perceivedAt, it.perceivedAt)
                assertEquals(listOf(CROP_ID), it.evidenceReferences)
            }
            val warning = live.phaseWarnings().single()
            assertEquals(SourceId("ATTACK_INCOMING_WITNESS"), warning.sourceId)
            assertEquals(CAPTURE_AT, warning.monotonicTimeNanos)
            assertEquals(fixture.crop.perceivedAt, warning.perceivedAt)
            assertEquals(listOf(originals.first().id.value, CROP_ID), warning.evidenceReferences)
            assertEquals(fixture.crop, articles.single { it.id.value == CROP_ID })
            assertTrue(articles.none { it.payload is ActivePokemonFaintedWitnessed || it.payload is MatchEnded })
        } finally { live.close() }
    }

    @Test fun `canonical invalid newer raw cannot block older valid raw and missing OCR geometry is rejected`() = runBlocking {
        val fixture = RecordedFixture()
        val live = LiveWitness(fixture)
        try {
            val newerAt = CAPTURE_AT + 1_000_000_000L
            val newer = fixture.crop.copy(id = ArticleId("invalid-newer-crop"),
                monotonicTimeNanos = newerAt, perceivedAt = fixture.crop.perceivedAt + 1_000L)
            live.timeline.append(newer)
            val invalid = listOf(
                RawTestimony(PROMPT),
                RawTestimony(PROMPT, fixture.reading.copy(words = emptyList())),
                RawTestimony(PROMPT, fixture.reading.copy(words = fixture.reading.words.map { it.copy(bounds = null) })),
                RawTestimony("", fixture.reading.copy(rawText = "", acceptedText = "", words = emptyList())),
                RawTestimony("a different original reading", fixture.reading),
            )
            invalid.forEach { payload -> live.submit(fixture.raw.copy(payload = payload,
                perceivedAt = newer.perceivedAt, monotonicTimeNanos = newerAt,
                evidenceReferences = listOf(newer.id.value))) }
            live.submit(fixture.raw.copy(evidenceReferences = listOf("uncited-missing-crop")))
            live.submit(fixture.raw.copy(monotonicTimeNanos = newerAt))
            live.submit(fixture.raw)
            live.drainWitness()
            val warning = live.phaseWarnings().single()
            assertEquals("Only the older original capture was fully validated", CAPTURE_AT, warning.monotonicTimeNanos)
            val sources = live.timeline.getArticles().filter { it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS" }
            assertEquals(invalid.size + 3, sources.size)
            assertEquals(invalid, sources.take(invalid.size).map { it.payload })
            assertEquals(listOf(sources.last().id.value, CROP_ID), warning.evidenceReferences)
            assertTrue(live.timeline.getArticles().none {
                it.payload is ActivePokemonFaintedWitnessed || it.payload is MatchEnded
            })
        } finally { live.close() }
    }

    @Test fun `earlier canonical announcement and later outcome reading do not duplicate the warning`() = runBlocking {
        val fixture = RecordedFixture()
        val live = LiveWitness(fixture)
        try {
            val earlierAt = CAPTURE_AT - 100_000_000L
            live.submitText("ANNOUNCEMENT_WITNESS", "Attack incoming!", earlierAt)
            live.submit(fixture.raw)
            live.drainWitness()
            val warning = live.phaseWarnings().single()
            val announcement = live.timeline.getArticles().single {
                it.sourceId.id == "ANNOUNCEMENT_WITNESS" && it.monotonicTimeNanos == earlierAt
            }
            assertEquals(earlierAt, warning.monotonicTimeNanos)
            assertEquals(listOf(announcement.id.value), warning.evidenceReferences)
            assertEquals(fixture.raw.payload, live.timeline.getArticles().single {
                it.sourceId.id == "MATCH_OUTCOME_TEXT_WITNESS"
            }.payload)
        } finally { live.close() }
    }

    @Test fun `late canonical no-shields prompt does not suppress supported fast move after completed charge`() = runBlocking {
        for (completionFirst in listOf(false, true)) {
            val fixture = RecordedFixture()
            val live = LiveWitness(fixture)
            val usedAt = requireNotNull(fixture.article("ac929ce2-743a-451c-89a1-f5d38cd2a8cf").monotonicTimeNanos)
            fun submit(source: String, payload: TestimonyPayload, at: Long) = live.custody.submitTestimony(
                SourceId(source), payload, fixture.crop.perceivedAt + (at - CAPTURE_AT) / 1_000_000L,
                null, emptyList(), at)
            suspend fun warning() {
                live.submit(fixture.raw)
                withTimeout(5_000) {
                    while (live.phaseWarnings().isEmpty()) delay(5)
                    live.match.awaitAcceptedEvidencePublished()
                }
            }
            suspend fun completion() {
                submit("CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS", ChargeMoveUsedAnnounced, usedAt)
                live.match.awaitAcceptedEvidencePublished()
            }
            try {
                submit(BattleWitnessContracts.countdownGlyph.witnessId, CountdownGlyphWitnessed("GO", .99f, 1), MATCH_START_AT)
                live.match.awaitAcceptedEvidencePublished()
                if (completionFirst) { completion(); warning() } else { warning(); completion() }
                // Synthetic follow-up damage verifies ordering only; this is not a recovered
                // attack or final faint from the original recording.
                val hitAt = usedAt + 1_000_000_000L
                submit("FOLLOWUP_DAMAGE_FIXTURE", ActiveHpBarDamageTickMeasured(
                    ActivePokemonSide.OPPONENT, .9f, .8f, .1f), hitAt)
                submit("END_FIXTURE", MatchEnded(BattleResult.LOSS), hitAt + 1_000_000_000L)
                live.match.awaitAcceptedEvidencePublished()
                val attacks = live.timeline.getArticles().filter { it.payload is FastMoveUseObserved }
                assertEquals("completionFirst=$completionFirst", 1, attacks.size)
                assertEquals(hitAt, attacks.single().monotonicTimeNanos)
                assertEquals(ActivePokemonSide.PLAYER, (attacks.single().payload as FastMoveUseObserved).attackingSide)
            } finally { live.close() }
        }
    }

    @Test fun `known completion rearms early and canonical incoming warnings without waiting six seconds`() = runBlocking {
        for (completionFirst in listOf(false, true)) {
            val fixture = RecordedFixture()
            val live = LiveWitness(fixture)
            val cues = mutableListOf<AcceptedChargeCaptureCue>()
            val handoff = live.match.acceptedChargeCapturePreparation
            handoff.attach(cues::add)
            val usedAt = requireNotNull(fixture.article("ac929ce2-743a-451c-89a1-f5d38cd2a8cf").monotonicTimeNanos)
            suspend fun complete() {
                live.custody.submitTestimony(SourceId("CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS"),
                    ChargeMoveUsedAnnounced, fixture.crop.perceivedAt + 1521L, null, emptyList(), usedAt)
                live.match.awaitAcceptedEvidencePublished()
            }
            try {
                if (completionFirst) complete()
                handoff.offerOutcomePrompt(fixture.crop, fixture.reading, NATIVE_COMPLETION_AT)
                live.submit(fixture.raw)
                withTimeout(5_000) {
                    while (live.phaseWarnings().isEmpty()) delay(5)
                    live.match.awaitAcceptedEvidencePublished()
                }
                if (!completionFirst) complete()
                val nextAt = usedAt + 500_000_000L
                val captured = fixture.crop.payload as CropCaptured
                val next = fixture.crop.copy(id = ArticleId("next-announcement"), monotonicTimeNanos = nextAt,
                    payload = captured.copy(cropProvenance = captured.cropProvenance.copy(
                        cropName = BattleCropContracts.announcement.cropName)))
                handoff.offer("ANNOUNCEMENT_WITNESS", BattleCropContracts.announcement, next,
                    "Attack incoming!", NATIVE_COMPLETION_AT)
                live.submitText("ANNOUNCEMENT_WITNESS", "Attack incoming!", nextAt)
                live.drainWitness()
                assertEquals("Early completionFirst=$completionFirst", listOf(CAPTURE_AT, nextAt),
                    cues.map { it.capturedAtNanos })
                assertEquals("Canonical completionFirst=$completionFirst", listOf(CAPTURE_AT, nextAt),
                    live.phaseWarnings().map { it.monotonicTimeNanos })
            } finally { live.close() }
        }
    }

    private inner class LiveWitness(fixture: RecordedFixture) {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline().apply { append(fixture.crop) }
        val match = Match(MATCH_ID, custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        private val witness = PersistedAttackIncomingWitness(FileCropArtifactStore(temporaryFolder.root))
        init { witness.start(match) }

        fun submit(article: RealityArticle) {
            custody.submitTestimony(article.sourceId, article.payload, article.perceivedAt,
                article.confidence, article.evidenceReferences.orEmpty(), requireNotNull(article.monotonicTimeNanos))
        }

        fun submitText(source: String, text: String, at: Long) {
            custody.submitTestimony(SourceId(source), RawTestimony(text), 1_000L,
                null, emptyList(), at)
        }

        /** A separate later episode acknowledges this same collector processed all earlier inputs. */
        suspend fun drainWitness() {
            submitText("ANNOUNCEMENT_WITNESS", "Attack incoming!", BARRIER_AT)
            withTimeout(5_000L) {
                while (timeline.getArticles().none { it.payload is AttackIncoming && it.monotonicTimeNanos == BARRIER_AT })
                    delay(10L)
                match.awaitAcceptedEvidencePublished()
            }
        }

        fun phaseWarnings() = timeline.getArticles().filter {
            it.payload is AttackIncoming && it.monotonicTimeNanos != BARRIER_AT
        }

        fun close() { witness.stop(); match.release() }
    }

    private class RecordedFixture {
        private val articles: Map<String, ArchivedRealityArticle>
        init {
            val bytes = resourceBytes("articles.json")
            assertEquals("Literal original Article objects must remain unchanged",
                "9ed52463a6e15e0af5a5d8f29b6e71a2581167779e9cb1a1baeb1c6f8cbaab1f", sha256(bytes))
            val decoded = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }
                .decodeFromString<List<ArchivedRealityArticle>>(bytes.toString(Charsets.UTF_8))
            assertEquals(36, decoded.size)
            articles = decoded.associateBy { it.articleId }
        }
        fun article(id: String) = articles.getValue(id)
        val crop get() = liveArticle(article(CROP_ID))
        val raw get() = liveArticle(article("a5ec92e7-b8c7-4463-945e-cfa70e34e28a"))
        val threshold get() = liveArticle(article("7ab6e1ec-2181-4046-8355-1a85d3648a90"))
        val reading get() = requireNotNull((raw.payload as RawTestimony).ocr)
    }

    companion object {
        private const val MATCH_ID = "e6fa7745-8827-4fd0-a4ec-659d1bbf7ad7"
        private const val CROP_ID = "87936339-bf17-4117-9448-d2d1eb858337"
        private const val PROMPT = "No Protect Shields remaining!"
        private const val MATCH_START_AT = 10_825_626_046_183L
        private const val CAPTURE_AT = 10_966_831_338_499L
        // totalMs=3366 is the retained duration; the original native clock was not archived separately.
        private const val NATIVE_COMPLETION_AT = CAPTURE_AT + 3_366_000_000L
        private const val BARRIER_AT = CAPTURE_AT + 20_000_000_000L
        private fun resourceBytes(name: String) = checkNotNull(OutcomeShieldPromptHandoffTest::class.java
            .getResourceAsStream("/battle/recorded-evidence/2026-11-11-no-shields-phase/$name"))
            .use { it.readBytes() }
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

        /** Copy the original relevant payloads and every custody field, without repaired OCR or clocks. */
        private fun liveArticle(article: ArchivedRealityArticle): RealityArticle {
            val payload: TestimonyPayload = when (val p = article.payload) {
                is ArchivedRawText -> RawTestimony(p.value, p.ocr)
                is ArchivedCropCaptured -> CropCaptured(
                    CropArtifactReference(p.artifactPath, p.sha256, p.byteCount, p.mediaType),
                    BattleCropProvenance(p.cropName, p.sourceWidth, p.sourceHeight,
                        BattleCropBounds(p.cropLeft, p.cropTop, p.cropRight, p.cropBottom)), p.overlayBounds)
                else -> error("Not an original crop/raw fixture: ${article.articleId}")
            }
            return RealityArticle(ArticleId(article.articleId), article.perceivedAt, article.recordedAt,
                SourceId(article.sourceId), payload, article.predecessorIds.map(::ArticleId),
                article.confidence, article.sequenceNumber, article.evidenceReferences,
                MatchId(article.matchId), article.monotonicTimeNanos)
        }
    }
}
