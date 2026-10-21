package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchiveSerializer
import org.junit.Assert.*
import org.junit.Test

class ReplayOpeningHpTest {
    @Test fun `recorded separate Incinerates cannot combine half of Sneasels HP into the first hit`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(
            javaClass.getResource("/replay/incinerate-opening-hp-20261013.json")).readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        val actions = (0..890).flatMap { step -> model.sceneAt(ns(step / 100.0)).fastMoveActions }
            .filter { it.side == "PLAYER" }.distinctBy { it.impactAtNanos }.sortedBy { it.impactAtNanos }
        println("Opening Incinerates: " + actions.map { action ->
            "${action.impactAtNanos / 1e9}: HP=${model.sceneAt(action.impactAtNanos).opponentHp?.filledFraction} witnesses=${action.observedAtNanos.map { it / 1e9 }}"
        })
        assertEquals(3, actions.size)
        assertTrue(actions.all { it.moveName == "Incinerate" && it.durationNanos == ns(2.5) })
        assertTrue(actions.zipWithNext().all { (a, b) -> b.impactAtNanos - a.impactAtNanos == ns(2.5) })
        assertEquals(.73544973f, model.sceneAt(actions[0].impactAtNanos).opponentHp!!.filledFraction!!, .01f)
        assertEquals(.47340426f, model.sceneAt(actions[1].impactAtNanos).opponentHp!!.filledFraction!!, .01f)
        assertEquals(.22222222f, model.sceneAt(actions[2].impactAtNanos).opponentHp!!.filledFraction!!, .01f)
        val firstImpact = model.sceneAt(actions.first().impactAtNanos)
        assertEquals(.99f, firstImpact.opponentHp!!.damageTrailFraction!!, .01f)
        assertTrue(firstImpact.opponentHp.damageTimingReconstructed)
        assertTrue(actions.all { "HP_DAMAGE_TICK" in it.evidenceKinds })
        assertEquals(firstImpact, model.sceneAt(actions.first().impactAtNanos)) // reverse scrub
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    private fun ns(seconds: Double) = (seconds * 1_000_000_000L).toLong()
}
