package com.example.overdex.battle.observation

import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Verifies the dedicated OCR testimony consumer and custody chain, not native OCR accuracy. */
class QteProgressPhaseTextTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `dedicated progress text supplies typed early cues with its own source times and crop ancestry`() = runBlocking {
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("qte-phase", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        val store = FileCropArtifactStore(temporaryFolder.root)
        val ready = PersistedAnnouncementPhraseWitness.getReady(store)
        val incoming = PersistedAttackIncomingWitness(store)
        val used = PersistedAnnouncementPhraseWitness.chargeMoveUsed(store)
        val at = System.nanoTime()
        fun text(id: String, value: String, offset: Long, source: String = "QTE_PROGRESS_TEXT_WITNESS") {
            custody.submitTestimony(SourceId(source), RawTestimony(value), 10_000L + offset / 1_000_000L,
                null, listOf("preserved-qte-crop-$id"), at + offset)
        }
        try {
            ready.start(match); incoming.start(match); used.start(match)
            text("ready", "Get Ready!", 1_000_000_000L)
            // The lower announcement strip is still empty while the higher band
            // reads the same brief cue again. That is one visible appearance.
            text("old-band-empty", "", 1_100_000_000L, "ANNOUNCEMENT_WITNESS")
            text("ready-repeat", "Get Ready!", 1_200_000_000L)
            text("nice", "NICE!", 2_000_000_000L)
            text("great", "GREAT!", 3_000_000_000L)
            text("excellent", "EXCELLENT!", 4_000_000_000L)
            text("used", "Camerupt used Earth Power!", 5_000_000_000L)
            text("incoming", "Attack incoming!", 12_000_000_000L)
            text("shield", "Use a Protect Shield?", 12_200_000_000L)
            text("ordinary-announcement", "Sneasel used Foul Play!", 20_000_000_000L, "ANNOUNCEMENT_WITNESS")
            withTimeout(5_000) {
                while (timeline.getArticles().none { it.payload is GetReadyWitnessed } ||
                    timeline.getArticles().none { it.payload is AttackIncoming } ||
                    timeline.getArticles().none { it.payload is ChargeMoveUsedAnnounced }) delay(10)
                match.awaitAcceptedEvidencePublished()
            }
            val articles = timeline.getArticles()
            val readyArticle = articles.single { it.payload is GetReadyWitnessed }
            val incomingArticle = articles.single { it.payload is AttackIncoming }
            for ((signal, phrase) in listOf(readyArticle to "Get Ready!", incomingArticle to "Attack incoming!")) {
                val raw = articles.single { it.id.value == requireNotNull(signal.evidenceReferences).single() }
                assertEquals("QTE_PROGRESS_TEXT_WITNESS", raw.sourceId.id)
                assertEquals(phrase, (raw.payload as RawTestimony).data)
                assertEquals(raw.perceivedAt, signal.perceivedAt)
                assertEquals(raw.monotonicTimeNanos, signal.monotonicTimeNanos)
                assertEquals(listOf(raw.id.value), signal.evidenceReferences)
                assertTrue(requireNotNull(raw.evidenceReferences).single().startsWith("preserved-qte-crop-"))
            }
            assertEquals(at + 1_000_000_000L, readyArticle.monotonicTimeNanos)
            assertEquals(at + 12_000_000_000L, incomingArticle.monotonicTimeNanos)
            val onlyUsed = articles.single { it.payload is ChargeMoveUsedAnnounced }
            assertEquals(at + 20_000_000_000L, onlyUsed.monotonicTimeNanos)
            val ordinaryRaw = articles.single { it.id.value == requireNotNull(onlyUsed.evidenceReferences).single() }
            assertEquals("ANNOUNCEMENT_WITNESS", ordinaryRaw.sourceId.id)
            assertEquals(listOf("NICE!", "GREAT!", "EXCELLENT!"), articles.filter {
                it.sourceId.id == "QTE_PROGRESS_TEXT_WITNESS" &&
                    (it.payload as? RawTestimony)?.data in listOf("NICE!", "GREAT!", "EXCELLENT!")
            }.map { (it.payload as RawTestimony).data })
        } finally {
            ready.stop(); incoming.stop(); used.stop(); match.release()
        }
    }

    @Test fun `dedicated shield prompt can establish incoming preparation without the warning`() {
        val episodes = IncomingAttackAnnouncementEpisodes()
        assertTrue(episodes.accept("No Protect Shields remaining!", 1_000_000_000L, "QTE_PROGRESS_TEXT_WITNESS"))
        assertFalse(episodes.accept("", 1_100_000_000L, "ANNOUNCEMENT_WITNESS"))
        assertFalse(episodes.accept("No Protect Shields remaining!", 1_200_000_000L, "QTE_PROGRESS_TEXT_WITNESS"))
        assertFalse(episodes.accept("NICE!", 2_000_000_000L, "QTE_PROGRESS_TEXT_WITNESS"))
        assertTrue(episodes.accept("Use a Protect Shield?", 10_000_000_000L, "QTE_PROGRESS_TEXT_WITNESS"))
        assertFalse(episodes.accept("Attack incoming!", 1_050_000_000L, "ANNOUNCEMENT_WITNESS"))
    }
}
