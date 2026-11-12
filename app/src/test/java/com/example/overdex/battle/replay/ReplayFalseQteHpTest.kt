package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayFalseQteHpTest {
    // Exact opening Articles selected from the Nov 11 Moondrop e23c82aa archive.
    // Includes the original clocks, geometry, false border-pulse use and later real HP.
    // It is a metadata subset; no images, audio or fabricated attack testimony.
    private val archive = MatchArchiveSerializer.deserialize(checkNotNull(javaClass.getResource(
        "/replay/moondrop-false-qte-hp-20261111.json")).readText())
    private val base = archive.articles.minOf { it.monotonicTimeNanos!! }

    @Test fun `isolated smaller thicker QTE shape cannot drain HP or create a replay hit`() {
        val badHp = archive.articles.single { it.articleId == FALSE_HP_ID }
        val badUse = archive.articles.single { it.articleId == FALSE_USE_ID }
        val shape = badHp.payload as ArchivedActiveHpBarMeasured
        assertEquals(173, shape.barRight - shape.barLeft)
        assertEquals(23, shape.barBottom - shape.barTop)
        assertEquals(.082840234f, shape.filledFraction, 0f)

        val evidence = ReplayHpEvidence(archive.articles)
        assertFalse(evidence.accepts(badHp))
        assertFalse("The same-frame border-pulse use must share the geometry rejection", evidence.accepts(badUse))
        val original = MatchArchiveSerializer.serialize(archive)
        val replay = MatchReplayModel(archive)
        val badAt = badHp.monotonicTimeNanos!!
        assertEquals(.7074468f, replay.sceneAt(badAt - 1).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(.7074468f, replay.sceneAt(badAt + 1).opponentHp!!.filledFraction!!, .0001f)
        val actions = (0..250).flatMap { replay.sceneAt(at(79.0) + it * 20_000_000L).fastMoveActions }
        assertTrue(actions.none { FALSE_USE_ID in it.sourceArticleIds })
        assertTrue("Rejected testimony is still in the record", replay.timedArticles.any { it.articleId == FALSE_HP_ID })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `independent real HP steps remain even when their attack observations were missed`() {
        val replay = MatchReplayModel(archive)
        assertEquals(.8518519f, replay.sceneAt(at(74.62)).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(.7074468f, replay.sceneAt(at(76.18)).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(.6968085f, replay.sceneAt(at(87.80)).opponentHp!!.filledFraction!!, .0001f)
        assertEquals(.55851066f, replay.sceneAt(at(99.00)).opponentHp!!.filledFraction!!, .0001f)
        // Preserve a recognition gap, rather than manufacturing attacks to explain the HP loss.
        val opening = (0..400).flatMap { replay.sceneAt(at(70.0) + it * 20_000_000L).fastMoveActions }
        assertTrue(opening.none { it.side == "PLAYER" })
    }

    @Test fun `properly shaped bar remains valid when the charged camera moves it`() {
        val baseline = event("normal", ArchivedActiveHpBarMeasured("PLAYER", 20, 450, 212, 468, .9f), 8)
        val moved = event("charge-camera", ArchivedActiveHpBarMeasured("PLAYER", 105, 790, 297, 808, .3f), 12)
        val records = listOf(
            event("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 1),
            event("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Stunfisk", 618), 1),
            baseline,
            event("charge", ArchivedChargeMoveUsedAnnounced, 10),
            moved,
        )
        val evidence = ReplayHpEvidence(records)
        assertTrue(evidence.accepts(baseline))
        assertTrue(evidence.accepts(moved))
        val replay = MatchReplayModel(MatchArchive(matchId = "camera", articles = records))
        assertEquals(.9f, replay.sceneAt(9_000_000_000).playerHp!!.filledFraction!!, .0001f)
        assertEquals(.3f, replay.sceneAt(12_000_000_000).playerHp!!.filledFraction!!, .0001f)
    }

    private fun at(seconds: Double) = base + (seconds * 1e9).toLong()
    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Long) = ArchivedRealityArticle(
        id, "camera", seconds * 1_000, seconds * 1_000, "TEST", payload, emptyList(), null, null,
        emptyList(), seconds * 1_000_000_000)

    companion object {
        private const val FALSE_HP_ID = "e405911b-8eda-4358-a1db-f1c78b6ee81c"
        private const val FALSE_USE_ID = "63f8094a-9b34-49cd-94e7-2f5ed20a99d1"
    }
}
