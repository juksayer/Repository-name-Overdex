package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayDecisionTimerTest {
    @Test fun `shield timer counts between reads ends early on clearance and restores on reverse scrub`() {
        val archive = battle(listOf(event("incoming", ArchivedAttackIncoming, 10),
            timer("four", 4, 11), timer("two", 2, 13), timer("clear", null, 14)))
        val bytes = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        assertNull(model.sceneAt(ns(9)).decisionTimer)
        assertEquals(4, model.sceneAt(ns(11)).decisionTimer!!.secondsRemaining)
        assertEquals(3, model.sceneAt(ns(12)).decisionTimer!!.secondsRemaining)
        assertEquals("SHIELD?", model.sceneAt(ns(12)).decisionTimer!!.reason)
        assertTrue(model.sceneAt(ns(12)).decisionTimer!!.interpolated)
        assertNull(model.sceneAt(ns(14)).decisionTimer)
        assertEquals(4, model.sceneAt(ns(11)).decisionTimer!!.secondsRemaining)
        assertEquals(bytes, MatchArchiveSerializer.serialize(archive))
        assertEquals(archive, MatchArchiveSerializer.deserialize(bytes))
    }

    @Test fun `HP noise during a witnessed decision cannot become a fast move`() {
        val model = MatchReplayModel(battle(listOf(timer("four", 4, 10), timer("clear", null, 14),
            hit("impossible", 12), hit("resumed", 17))))
        for (millis in 10_000L..13_999L step 50) assertTrue(model.sceneAt(millis * 1_000_000L).fastMoveActions.isEmpty())
        assertTrue(model.sceneAt(ns(17)).fastMoveActions.isNotEmpty())
    }

    @Test fun `legacy replacement pause suppresses attacks without inventing timer numerals`() {
        val model = MatchReplayModel(battle(listOf(
            event("ko", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Abomasnow", 460, "BALL_COUNT"), 10),
            hit("impossible", 12),
            event("entry", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 14))))
        val paused = model.sceneAt(ns(12))
        assertEquals("CHOOSE POKÉMON", paused.decisionTimer!!.reason)
        assertNull(paused.decisionTimer.secondsRemaining)
        assertEquals("RECONSTRUCTED_REPLACEMENT_PAUSE", paused.decisionTimer.basis)
        assertEquals(setOf("ko", "entry"), paused.decisionTimer.sourceArticleIds.toSet())
        assertEquals(.5f, paused.decisionTimer.remainingFraction, .001f)
        assertTrue(paused.fastMoveActions.isEmpty())
        assertNull(model.sceneAt(ns(14)).decisionTimer)
    }

    @Test fun `opponent replacement pause has a dial and cannot invent attacks or seconds`() {
        val model = MatchReplayModel(battle(listOf(
            event("ko", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Combusken", 256, "BALL_COUNT"), 10),
            hit("noise", 12),
            event("entry", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364), 14))))
        val paused = model.sceneAt(ns(12))
        assertEquals("OPPONENT CHOOSING", paused.decisionTimer!!.reason)
        assertNull(paused.decisionTimer.secondsRemaining)
        assertTrue(paused.fastMoveActions.isEmpty())
        assertEquals(0f, paused.opponentHp!!.filledFraction!!, 0f)
        assertNull(model.sceneAt(ns(14)).decisionTimer)
    }

    @Test fun `numeric readings take priority without discarding the unobserved ends of a replacement pause`() {
        val model = MatchReplayModel(battle(listOf(
            event("ko", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Abomasnow", 460, "BALL_COUNT"), 10),
            timer("eleven", 11, 11), timer("clear", null, 13),
            event("entry", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 14))))
        assertNull(model.sceneAt(ns(10)).decisionTimer!!.secondsRemaining)
        assertEquals(11, model.sceneAt(ns(11)).decisionTimer!!.secondsRemaining)
        assertEquals("WITNESSED_TIMER", model.sceneAt(ns(12)).decisionTimer!!.basis)
        assertNull(model.sceneAt(ns(13)).decisionTimer!!.secondsRemaining)
        assertNull(model.sceneAt(ns(14)).decisionTimer)
    }

    @Test fun `a voluntary switch or final result does not manufacture a replacement timer`() {
        val model = MatchReplayModel(battle(listOf(
            event("entry", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 10),
            event("ko", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Camerupt", 323, "BALL_COUNT"), 15),
            event("result", ArchivedMatchEnded("LOSS"), 16))))
        for (seconds in 0L..20L) assertNull(model.sceneAt(ns(seconds)).decisionTimer)
    }

    private fun battle(extra: List<ArchivedRealityArticle>) = MatchArchive(matchId = "timer", articles = listOf(
        event("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Abomasnow", 460), 0),
        event("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Combusken", 256), 0),
        event("go", ArchivedMatchStarted, 0)) + extra)
    private fun timer(id: String, seconds: Int?, at: Long) = event(id,
        ArchivedBattleDecisionTimerObserved(seconds, seconds?.toString().orEmpty(), "TEST"), at)
    private fun hit(id: String, at: Long) = event(id, ArchivedFastMoveUseObserved(id, "OPPONENT", "PLAYER", "a", "Combusken",
        listOf("HP_BORDER_PULSE"), "TEST"), at)
    private fun event(id: String, payload: ArchivedTestimonyPayload, at: Long) = ArchivedRealityArticle(
        id, "timer", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), ns(at))
    private fun ns(seconds: Long) = seconds * 1_000_000_000L
}
