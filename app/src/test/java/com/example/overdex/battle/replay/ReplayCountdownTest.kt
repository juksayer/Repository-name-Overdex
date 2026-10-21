package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayCountdownTest {
    @Test fun `uneven repeated readings produce four equal beats without changing the record`() {
        val archive = archive(
            event("recording", ArchivedMatchRecordStarted, 1.0),
            glyph("three", "3", 10.0), glyph("three-again", "3", 11.0),
            glyph("three-still", "3", 12.0), glyph("two", "2", 12.2),
            event("start", ArchivedMatchStarted, 13.0),
            event("charge", ArchivedChargeMoveUsedAnnounced, 30.0),
        )
        val before = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)

        assertSequence(model, 13.0)
        assertEquals(listOf("three", "three-again", "three-still"), model.sceneAt(ns(10.2)).countdownFrame!!.observedArticleIds)
        assertTrue(model.sceneAt(ns(12.2)).countdownFrame!!.observedArticleIds.isEmpty())
        assertTrue(model.sceneAt(ns(13.2)).countdownFrame!!.observedArticleIds.isEmpty())
        assertEquals(ns(13.0), model.matchStartNanos)
        assertEquals(ns(1.0), model.recordingStartNanos)
        assertEquals(archive.articles, model.timedArticles)
        assertEquals(before, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `every single numeral can reconstruct the full countdown when combat follows`() {
        for (number in listOf(3, 2, 1)) {
            val model = MatchReplayModel(archive(
                event("record", ArchivedMatchRecordStarted, 1.0),
                glyph("number", number.toString(), 20.0 - number),
                event("combat", ArchivedGetReadyWitnessed, 30.0),
            ))
            assertSequence(model, 20.0)
            assertEquals(ReplayCountdownTiming.ESTIMATED_FROM_NUMERAL, model.countdown!!.timingBasis)
            assertEquals(setOf("number", "combat"), model.countdown!!.evidenceArticleIds.toSet())
            assertNull("Presentation cannot insert a dated MatchStarted", model.matchStartNanos)
        }
    }

    @Test fun `recording first GO still has a complete reachable countdown`() {
        val model = MatchReplayModel(archive(glyph("go", "GO", 100.0)))
        assertEquals(ns(100.0), model.recordingStartNanos)
        assertEquals(ns(97.0), model.startNanos)
        assertEquals(ns(101.0), model.endNanos)
        assertSequence(model, 100.0)
        assertEquals(ReplayCountdownTiming.OBSERVED_GO, model.countdown!!.timingBasis)
    }

    @Test fun `recorded match start reconstructs all glyphs when none were captured`() {
        val model = MatchReplayModel(archive(event("start", ArchivedMatchStarted, 100.0)))
        assertSequence(model, 100.0)
        assertTrue(model.countdown!!.observations.isEmpty())
        assertEquals(ns(100.0), model.matchStartNanos)
    }

    @Test fun `charged attack proves prerequisite countdown but does not invent a precise start`() {
        val model = MatchReplayModel(archive(
            event("record", ArchivedMatchRecordStarted, 1.0),
            event("species", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Charizard", 6), 50.0),
            event("attack", ArchivedChargeMoveUsedAnnounced, 90.0),
        ))
        assertSequence(model, 50.0)
        assertNull(model.matchStartNanos)
        assertEquals(ReplayCountdownTiming.PRESENTATION_ONLY, model.countdown!!.timingBasis)
        assertTrue(model.sceneAt(ns(48.0)).countdownFrame!!.lcdLabel.contains("START TIME UNKNOWN"))
        assertEquals(ns(90.0), model.timedArticles.single { it.articleId == "attack" }.monotonicTimeNanos)
    }

    @Test fun `an interrupted countdown without continuation is not completed`() {
        val model = MatchReplayModel(archive(
            glyph("three", "3", 10.0),
            event("stop", ArchivedObservationSessionStoppedByUser, 11.0),
        ))
        assertNull(model.countdown)
        assertEquals("3", model.sceneAt(ns(10.4)).countdownGlyph)
        assertNull(model.sceneAt(ns(13.0)).countdownGlyph)
    }

    @Test fun `a move label or configured moves cannot prove that a battle began`() {
        val model = MatchReplayModel(archive(
            event("team", ArchivedPlayerTeamSlotConfigured(1, "Charizard", 6, "Fire Spin", listOf("Blast Burn")), 1.0),
            event("label", ArchivedPlayerChargeMoveLabelRead(1, "Blast Burn"), 2.0),
            event("name", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Charizard", 6), 3.0),
        ))
        assertNull(model.countdown)
    }

    @Test fun `scrubbing backward has the same beats and later glyph noise never restarts them`() {
        val model = MatchReplayModel(archive(
            event("start", ArchivedMatchStarted, 20.0),
            glyph("noise", "3", 55.0),
        ))
        assertSequence(model, 20.0)
        assertNull(model.sceneAt(ns(55.4)).countdownGlyph)
        for ((second, expected) in listOf(20.9 to "GO", 19.9 to "1", 18.9 to "2", 17.9 to "3")) {
            assertEquals(expected, model.sceneAt(ns(second)).countdownGlyph)
        }
    }

    @Test fun `previous cancelled attempt cannot supply the new countdown anchor`() {
        val model = MatchReplayModel(archive(
            glyph("old-three", "3", 10.0),
            event("restart", ArchivedObservationRestartedByUser, 20.0),
            glyph("new-two", "2", 40.0),
            event("attack", ArchivedAttackIncoming, 50.0),
        ))
        assertSequence(model, 42.0)
        assertFalse("old-three" in model.countdown!!.evidenceArticleIds)
    }

    @Test fun `replay speed changes all countdown beats equally`() {
        val model = MatchReplayModel(archive(event("start", ArchivedMatchStarted, 20.0)))
        for (speed in listOf(0.1f, 1f, 5f)) {
            val clock = ReplayPlaybackClock(model.startNanos, model.endNanos, speed)
            clock.positionAt(ns(100.0))
            listOf("3", "2", "1", "GO").forEachIndexed { index, glyph ->
                val elapsed = ((index + 0.5) * 1_000_000_000 / speed).toLong()
                assertEquals("speed=$speed step=$index", glyph,
                    model.sceneAt(clock.positionAt(ns(100.0) + elapsed)).countdownGlyph)
            }
        }
    }

    private fun assertSequence(model: MatchReplayModel, go: Double) {
        assertNull(model.sceneAt(ns(go - 3.0) - 1).countdownGlyph)
        for ((index, glyph) in listOf("3", "2", "1", "GO").withIndex()) {
            val start = ns(go - 3.0 + index)
            assertEquals(glyph, model.sceneAt(start).countdownGlyph)
            assertEquals(glyph, model.sceneAt(start + 999_999_999).countdownGlyph)
        }
        assertNull(model.sceneAt(ns(go + 1.0)).countdownGlyph)
    }

    private fun archive(vararg articles: ArchivedRealityArticle) = MatchArchive(matchId = "match", articles = articles.toList())
    private fun glyph(id: String, glyph: String, seconds: Double) = event(
        id, ArchivedCountdownGlyphWitnessed(glyph, .98f, 1, basis = "TEST"), seconds,
    )
    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Double) = ArchivedRealityArticle(
        articleId = id, matchId = "match", perceivedAt = (seconds * 1_000).toLong(),
        recordedAt = (seconds * 1_000).toLong() + 500, sourceId = "test", payload = payload,
        predecessorIds = emptyList(), confidence = null, sequenceNumber = null,
        evidenceReferences = emptyList(), monotonicTimeNanos = ns(seconds),
    )
    private fun ns(seconds: Double) = (seconds * 1_000_000_000).toLong()
}
