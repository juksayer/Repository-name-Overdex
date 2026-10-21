package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import org.junit.Assert.*
import org.junit.Test

class RecordedHpStepReplayTest {
    private val archive = MatchArchiveSerializer.deserialize(checkNotNull(
        javaClass.getResource("/replay/abomasnow-hp-steps-20261014.json")).readText())

    @Test fun `recorded Powder Snow losses appear at distinct projectile impacts`() {
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        val actions = (0..350).flatMap { model.sceneAt(it * 10_000_000L).fastMoveActions }
            .filter { it.side == "PLAYER" }.distinctBy { it.impactAtNanos }.sortedBy { it.impactAtNanos }
        assertEquals(3, actions.size)
        assertEquals(listOf(1L, 2L, 3L).map { it * 1_000_000_000L }, actions.map { it.impactAtNanos })
        val expected = listOf(.95238f, .92063f, .87766f)
        actions.forEachIndexed { index, action ->
            val before = model.sceneAt(action.impactAtNanos - 1).opponentHp!!
            val impact = model.sceneAt(action.impactAtNanos)
            assertTrue("Separate HP chunk for hit $index", before.filledFraction!! > impact.opponentHp!!.filledFraction!!)
            assertEquals(expected[index], impact.opponentHp!!.filledFraction!!, .001f)
            assertTrue(impact.opponentHp.damageTimingReconstructed)
            assertEquals(1f, impact.fastMoveActions.single { it.side == "PLAYER" }.progress, 0f)
            assertTrue("REPLAY_CORROBORATED_HP_STEP" in action.evidenceKinds)
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
        assertEquals(model.sceneAt(1_000_000_000L), model.sceneAt(1_000_000_000L))
    }

    @Test fun `late accumulated damage articles do not generate new attacks`() {
        val evidence = ReplayHpEvidence(archive.articles)
        for (id in listOf("3a5ef741-2f18-4a46-883a-839c888411ca", "f695c8f2-6702-48c8-8044-cea68200c531")) {
            assertFalse(evidence.acceptsSingleHit(archive.articles.single { it.articleId == id }))
        }
    }
}
