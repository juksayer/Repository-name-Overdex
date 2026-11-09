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

    @Test fun `expired zero does not consume the next fresh zero assignment`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now))
        resolveAll(gate)
        val delayedZeroAt = now + SECOND / 2
        now += 4 * SECOND
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0f, delayedZeroAt)
        assertTrue(crops.none(gate::isEnabled))
        gate.observeActiveHp(ActivePokemonSide.OPPONENT, 0f, now)
        assertTrue(gate.isEnabled(opponentBalls))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(opponentShields))
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

    @Test fun `expired changed identity does not consume next fresh same name resource wake`() {
        val gate = gate()
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Golbat", now)
        resolveAll(gate)
        val delayedEntryAt = now + SECOND
        now += 5 * SECOND
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", delayedEntryAt)
        assertTrue(crops.none(gate::isEnabled))
        gate.observeSpecies(ActivePokemonSide.OPPONENT, "Camerupt", now)
        assertTrue(gate.isEnabled(opponentBalls))
        assertTrue(gate.isEnabled(opponentShields))
        assertFalse(gate.isEnabled(playerBalls))
        assertFalse(gate.isEnabled(playerShields))
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

    @Test fun `first fresh surface acquires resources if old GO was delivered too late`() {
        val gate = gate()
        gate.observe(article(MatchStarted, now - 5 * SECOND))
        assertTrue(crops.none(gate::isEnabled))
        gate.observeActiveHp(ActivePokemonSide.PLAYER, 1f, now)
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
