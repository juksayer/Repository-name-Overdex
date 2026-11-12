package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class TeamResourceSnapshotGateTest {
    private val playerBalls = BattleCropContracts.playerPokeBalls.cropName
    private val opponentBalls = BattleCropContracts.opponentPokeBalls.cropName
    private val playerShields = BattleCropContracts.playerShields.cropName
    private val opponentShields = BattleCropContracts.opponentShields.cropName
    private val crops = setOf(playerBalls, opponentBalls, playerShields, opponentShields)
    private val balls = setOf(playerBalls, opponentBalls)
    private var now = 10 * SECOND
    private fun gate() = TeamResourceSnapshotGate(crops, balls, now = { now })
    private fun resolveAll(gate: TeamResourceSnapshotGate, at: Long = now) = crops.forEach { gate.measured(it, at) }

    @Test fun `first opponent identity retries its missing baseline after pre-GO window expires`() {
        val gate = gate()
        gate.observeActiveHp(ActivePokemonSide.PLAYER, 1f, now)
        gate.measured(playerBalls, now)
        gate.measured(playerShields, now)
        gate.measured(opponentShields, now)
        now += 4 * SECOND
        assertFalse(gate.isEnabled(opponentBalls))
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Tinkaton", now)
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(playerShields))
        assertFalse(gate.isEnabled(opponentShields))
        now += 3 * SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Tinkaton", now)
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `first GO retries an expired missing baseline once and never renews solved crops`() {
        val gate = gate()
        gate.observeActiveHp(ActivePokemonSide.PLAYER, 1f, now)
        gate.measured(playerBalls, now)
        now += 4 * SECOND
        val goAt = now
        gate.observe(article(MatchStarted, goAt))
        assertFalse(gate.isEnabled(playerBalls))
        assertTrue(gate.isEnabled(opponentBalls))
        now += 3 * SECOND
        gate.observe(article(MatchStarted, goAt))
        gate.observe(article(MatchStarted, now))
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `usable candidate gets exactly one three second tail but is not a measurement`() {
        val gate = gate()
        gate.requestSnapshot()
        val firstAt = now
        now += 2 * SECOND
        gate.candidateRead(opponentBalls, firstAt)
        now += SECOND
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(playerBalls))
        assertTrue(gate.acceptsFrame(opponentBalls, now))
        repeat(3) {
            gate.candidateRead(opponentBalls, now)
            now += SECOND
        }
        assertFalse(gate.isEnabled(opponentBalls))
        gate.candidateRead(opponentBalls, firstAt)
        assertFalse(gate.isEnabled(opponentBalls))
    }

    @Test fun `late decoding can use only the remainder of the fixed confirmation tail`() {
        val gate = gate()
        gate.requestSnapshot()
        val sourceAt = now + SECOND
        now += 4 * SECOND
        assertFalse(gate.isEnabled(opponentBalls))
        gate.candidateRead(opponentBalls, sourceAt)
        assertTrue(gate.isEnabled(opponentBalls))
        now += 2 * SECOND
        assertFalse(gate.isEnabled(opponentBalls))
    }

    @Test fun `old assignment candidate cannot extend newer request and successful count closes tail`() {
        val gate = gate()
        gate.requestSnapshot()
        val oldAt = now
        now += 4 * SECOND
        gate.requestDepartureCheck(ActivePokemonSide.OPPONENT, now)
        val newAt = now
        gate.candidateRead(opponentBalls, oldAt)
        now += 3 * SECOND
        assertFalse(gate.isEnabled(opponentBalls))
        gate.candidateRead(opponentBalls, newAt)
        assertTrue(gate.isEnabled(opponentBalls))
        gate.measured(opponentBalls, newAt)
        assertFalse(gate.isEnabled(opponentBalls))
        gate.candidateRead(opponentBalls, newAt)
        assertFalse(gate.isEnabled(opponentBalls))
    }

    @Test fun `stop and restart reject old and future candidate feedback`() {
        val gate = gate()
        gate.requestSnapshot()
        val oldAt = now
        gate.stop()
        gate.candidateRead(opponentBalls, oldAt)
        assertFalse(gate.isEnabled(opponentBalls))
        now += SECOND
        gate.observe(article(ObservationRestartedByUser, now))
        gate.observe(article(MatchStarted, now))
        gate.candidateRead(opponentBalls, oldAt)
        gate.candidateRead(opponentBalls, now + SECOND)
        now += 3 * SECOND
        assertFalse(gate.isEnabled(opponentBalls))
    }

    @Test fun `saved images do not complete assignments but usable readings do independently`() {
        val gate = gate()
        gate.requestSnapshot()
        repeat(4) { gate.captured(playerBalls) }
        assertTrue(gate.isEnabled(playerBalls))
        gate.measured(playerBalls, now)
        assertFalse(gate.isEnabled(playerBalls))
        assertTrue(gate.isEnabled(opponentBalls))
        gate.measured(opponentBalls, now)
        gate.measured(playerShields, now)
        assertTrue(gate.isEnabled(opponentShields))
        gate.measured(opponentShields, now)
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `unreadable assignment expires even when no sample was preserved`() {
        val gate = gate()
        gate.requestSnapshot()
        now += 3 * SECOND
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `initial battle resources are read once and all accepted count payloads finish their own crop`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        gate.observe(article(PlayerPokeBallCountMeasured(3), now))
        gate.observe(article(PlayerShieldCountMeasured(2), now))
        gate.observe(article(OpponentBattleResourceCountMeasured(OpponentBattleResource.POKE_BALLS, 3, 3), now))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(playerShields))
        assertFalse(gate.isEnabled(opponentBalls))
        assertTrue(gate.isEnabled(opponentShields))
        gate.observe(article(OpponentBattleResourceCountMeasured(OpponentBattleResource.SHIELDS, 2, 2), now))
        now += SECOND
        gate.observe(article(MatchStarted, 10 * SECOND))
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `charge and QTE cues wake shields without waking balls`() {
        for (cue in listOf(GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced)) {
            val gate = gate()
            gate.observe(article(MatchStarted, now))
            resolveAll(gate)
            now += SECOND
            gate.observe(article(cue, now))
            assertTrue(gate.isEnabled(playerShields))
            assertTrue(gate.isEnabled(opponentShields))
            assertFalse(gate.isEnabled(playerBalls))
            assertFalse(gate.isEnabled(opponentBalls))
            now += 3 * SECOND
            assertTrue(crops.none(gate::isEnabled))
        }
    }

    @Test fun `red HP and nonterminal damage never wake count readers`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        resolveAll(gate)
        repeat(20) {
            now += SECOND
            gate.observeActiveHp(ActivePokemonSide.OPPONENT, .08f, now)
            gate.observeDamageTick(ActivePokemonSide.OPPONENT, .04f, now)
            assertTrue(crops.none(gate::isEnabled))
        }
    }

    @Test fun `first zero wakes only that ball badge and repeated zero cannot keep polling`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        resolveAll(gate)
        now += SECOND
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0f, now)
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(opponentShields))
        gate.measured(opponentBalls, now)
        repeat(10) {
            now += SECOND
            gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0f, now)
            assertFalse(gate.isEnabled(opponentBalls))
        }
    }

    @Test fun `delayed zero opens one current frame assignment without repeated zero polling`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        resolveAll(gate)
        val delayedZeroAt = now + SECOND / 2
        now += 4 * SECOND
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0f, delayedZeroAt)
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.acceptsFrame(opponentBalls, now - 1))
        val receivedAt = now
        now += SECOND
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0f, now)
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(opponentShields))
        now = receivedAt + 3 * SECOND
        assertFalse(gate.isEnabled(opponentBalls))
    }

    @Test fun `new species wakes only its resources while confirmations remain asleep`() {
        val gate = gate()
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
        resolveAll(gate)
        now += SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "golbat", now)
        assertTrue(crops.none(gate::isEnabled))
        now += SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", now)
        assertTrue(gate.isEnabled(opponentBalls))
        assertTrue(gate.isEnabled(opponentShields))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(playerShields))
    }

    @Test fun `faint refreshes affected shields but its already accepted ball count stays complete`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        resolveAll(gate)
        now += SECOND
        gate.observe(article(PlayerPokeBallCountMeasured(2), now))
        gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Gourgeist"), now))
        assertFalse(gate.isEnabled(playerBalls))
        assertTrue(gate.isEnabled(playerShields))
        assertFalse(gate.isEnabled(opponentShields))
    }

    @Test fun `source late equal and future requests cannot reopen a completed assignment`() {
        val gate = gate()
        gate.requestSnapshot()
        now += SECOND
        resolveAll(gate)
        gate.requestSnapshot(observedAt = now)
        gate.requestSnapshot(observedAt = now - SECOND)
        gate.requestSnapshot(observedAt = now + SECOND)
        assertTrue(crops.none(gate::isEnabled))
        now += SECOND
        gate.requestSnapshot(observedAt = now)
        assertTrue(crops.all(gate::isEnabled))
        assertFalse(gate.acceptsFrame(playerBalls, now - 1))
        assertTrue(gate.acceptsFrame(playerBalls, now))
    }

    @Test fun `old measurement cannot finish newer assignment or revive its deadline`() {
        val gate = gate()
        gate.requestSnapshot()
        now += SECOND
        gate.requestSnapshot()
        gate.measured(playerBalls, now - 1)
        assertTrue(gate.isEnabled(playerBalls))
        now += 3 * SECOND
        assertFalse(gate.isEnabled(playerBalls))
        gate.requestSnapshot(observedAt = 11 * SECOND)
        assertFalse(gate.isEnabled(playerBalls))
    }

    @Test fun `delayed changed identity acquires resources once and repeated same name does not extend window`() {
        val gate = gate()
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
        resolveAll(gate)
        val delayedEntryAt = now + SECOND
        now += 5 * SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", delayedEntryAt)
        val receivedAt = now
        assertFalse(gate.acceptsFrame(opponentBalls, now - 1))
        now += SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", now)
        assertTrue(gate.isEnabled(opponentBalls))
        assertTrue(gate.isEnabled(opponentShields))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(playerShields))
        now = receivedAt + 3 * SECOND
        assertTrue(crops.none(gate::isEnabled))
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", now)
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `departure scheduler hint checks only affected balls and never asserts a faint`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        resolveAll(gate)
        now += SECOND
        gate.requestDepartureCheck(ActivePokemonSide.OPPONENT, now)
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(opponentShields))
        assertFalse(gate.isEnabled(playerShields))
        now += 3 * SECOND
        assertFalse(gate.isEnabled(opponentBalls))
        gate.requestDepartureCheck(ActivePokemonSide.OPPONENT, now - 3 * SECOND)
        assertFalse(gate.isEnabled(opponentBalls))
    }

    @Test fun `late old species and HP cannot rearm departed member resources`() {
        val gate = gate()
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
        resolveAll(gate)
        now += SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", now)
        resolveAll(gate)
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now - SECOND)
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0f, now - 1)
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `late lifecycle reports use their trigger clock rather than delivery clock`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        resolveAll(gate)
        val oldTrigger = now
        now += SECOND
        gate.observe(article(SpeciesCheckMeasured(ActivePokemonSide.OPPONENT, 1, "OPENED", "SWITCH",
            oldTrigger, SECOND, SECOND), now))
        assertTrue(crops.none(gate::isEnabled))
        now += SECOND
        gate.observe(article(SpeciesCheckMeasured(ActivePokemonSide.OPPONENT, 2, "OPENED", "SWITCH",
            now, 0, SECOND), now))
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(playerBalls))
    }

    @Test fun `delayed initial GO acquires a current baseline once without changing its source time`() {
        val gate = gate()
        val cue = article(MatchStarted, now - 5 * SECOND)
        gate.observe(cue)
        assertTrue(crops.all(gate::isEnabled))
        assertEquals(now - 5 * SECOND, cue.monotonicTimeNanos!!)
        assertFalse(gate.acceptsFrame(playerBalls, now - 1))
        resolveAll(gate)
        gate.observeActiveHp(ActivePokemonSide.PLAYER, 1f, now)
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `recorded long and near deadline charge delays receive full windows for fresh confirmation frames`() {
        for (lag in listOf(5_301_000_000L, 6 * SECOND, 10 * SECOND, 2_429_000_000L, 2_926_000_000L)) {
            now = 10 * SECOND
            val gate = gate()
            gate.observe(article(MatchStarted, now))
            resolveAll(gate)
            val cueAt = now + SECOND
            now = cueAt + lag
            val receivedAt = now
            gate.observe(article(AttackIncoming, cueAt))
            assertTrue("lag=$lag", gate.isEnabled(opponentShields))
            assertFalse(gate.isEnabled(opponentBalls))
            assertFalse(gate.acceptsFrame(opponentShields, receivedAt - 1))
            assertTrue(gate.acceptsFrame(opponentShields, receivedAt))
            now += 200_000_000L
            assertTrue(gate.acceptsFrame(opponentShields, now))
            // A confirmation first citing an old queued candidate cannot close
            // the new assignment. Its next unchanged fresh count can do so.
            gate.measured(opponentShields, receivedAt - 1)
            assertTrue(gate.isEnabled(opponentShields))
            gate.measured(opponentShields, now)
            assertFalse(gate.isEnabled(opponentShields))
            now = receivedAt + 3 * SECOND - 1
            gate.observe(article(AttackIncoming, cueAt))
            assertTrue(gate.isEnabled(playerShields))
            assertFalse(gate.isEnabled(opponentShields))
            now++
            assertFalse(gate.isEnabled(playerShields))
            gate.observe(article(AttackIncoming, cueAt))
            assertTrue(crops.none(gate::isEnabled))
        }
    }

    @Test fun `later successful count covers delayed cue and superseded species cannot reverse acquisition order`() {
        val gate = gate()
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
        resolveAll(gate)
        val earlierCue = now + SECOND
        now += 2 * SECOND
        gate.measured(opponentBalls, now)
        gate.measured(opponentShields, now)
        now += 10 * SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", earlierCue)
        assertTrue(crops.none(gate::isEnabled))
        val newerCue = now
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Gourgeist", newerCue)
        assertTrue(gate.isEnabled(opponentBalls))
        val receivedAt = now
        now += SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", earlierCue - 1)
        gate.measured(opponentBalls, receivedAt - 1)
        assertTrue(gate.isEnabled(opponentBalls))
        now = receivedAt + 3 * SECOND
        assertTrue(crops.none(gate::isEnabled))
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", earlierCue)
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `later same name OCR does not replace counting or invalidate a delayed departure check`() {
        for (hint in listOf("DEPARTURE", "FAINT", "SWITCH")) {
            now = 10 * SECOND
            val gate = gate()
            gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
            resolveAll(gate)
            val cueAt = now + SECOND
            now += 2 * SECOND
            gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
            now += 10 * SECOND
            when (hint) {
                "DEPARTURE" -> gate.requestDepartureCheck(ActivePokemonSide.OPPONENT, cueAt)
                "FAINT" -> gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT), cueAt))
                else -> gate.observe(article(SpeciesCheckMeasured(ActivePokemonSide.OPPONENT, 1, "OPENED", "SWITCH",
                    cueAt, 0, SECOND), now))
            }
            assertTrue("hint=$hint", gate.isEnabled(opponentBalls))
            assertFalse(gate.isEnabled(playerBalls))
            assertFalse(gate.acceptsFrame(opponentBalls, now - 1))
        }
    }

    @Test fun `newer appearance request supersedes old side hints even after its window expires`() {
        val gate = gate()
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
        resolveAll(gate)
        val oldHint = now + SECOND
        val newerAppearance = now + 2 * SECOND
        now += 10 * SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", newerAppearance)
        now += 3 * SECOND
        gate.requestDepartureCheck(ActivePokemonSide.OPPONENT, oldHint)
        gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT), oldHint))
        gate.observe(article(SpeciesCheckMeasured(ActivePokemonSide.OPPONENT, 1, "OPENED", "SWITCH",
            oldHint, 0, SECOND), now))
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `old stop result and repeated restart cannot cancel or rewind the new attempt`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        now += 10 * SECOND
        val restartedAt = now
        gate.observe(article(ObservationRestartedByUser, restartedAt))
        gate.observe(article(MatchStarted, restartedAt))
        now += SECOND
        gate.measured(playerBalls, now)
        gate.observe(article(ObservationSessionStoppedByUser, restartedAt - 1))
        gate.observe(article(MatchEnded(BattleResult.LOSS), restartedAt - 1))
        gate.observe(article(ObservationRestartedByUser, restartedAt))
        gate.observe(article(ObservationRestartedByUser, restartedAt - 1))
        gate.observe(article(ObservationRestartedByUser, now + SECOND))
        assertFalse(gate.isEnabled(playerBalls))
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.acceptsFrame(opponentBalls, restartedAt - 1))
        gate.observe(article(MatchEnded(BattleResult.WIN), now))
        gate.observe(article(ObservationRestartedByUser, restartedAt + 1))
        gate.requestSnapshot()
        assertTrue(crops.none(gate::isEnabled))
        now += SECOND
        gate.observe(article(ObservationRestartedByUser, now))
        gate.observe(article(MatchStarted, now))
        assertTrue(crops.all(gate::isEnabled))
    }

    @Test fun `result and stop reject every later wake until explicit restart`() {
        for (stop in listOf(false, true)) {
            val gate = gate()
            gate.requestSnapshot()
            if (stop) gate.stop() else gate.observe(article(MatchEnded(BattleResult.WIN), now))
            now += SECOND
            gate.observeSpecies(ActivePokemonSide.OPPONENT, "Late", now)
            gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0f, now)
            gate.requestResourceWatch()
            gate.requestFaintWatch()
            assertTrue(crops.none(gate::isEnabled))
            gate.observe(article(ObservationRestartedByUser, now))
            gate.observe(article(MatchStarted, now - SECOND))
            assertTrue(crops.none(gate::isEnabled))
            now += SECOND
            gate.observe(article(MatchStarted, now))
            assertTrue(crops.all(gate::isEnabled))
        }
    }

    private fun article(payload: TestimonyPayload, at: Long) = RealityArticle(
        ArticleId(java.util.UUID.randomUUID().toString()), 0, 0, SourceId("TEST"), payload, monotonicTimeNanos = at)
    private companion object { const val SECOND = 1_000_000_000L }
}
