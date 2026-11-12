package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class InactiveBenchSnapshotGateTest {
    private val upperHp = BattleCropContracts.playerInactiveUpperHpBar.cropName
    private val lowerHp = BattleCropContracts.playerInactiveLowerHpBar.cropName
    private val upperSprite = BattleCropContracts.playerInactiveUpperSpeciesSprite.cropName
    private val lowerSprite = BattleCropContracts.playerInactiveLowerSpeciesSprite.cropName
    private val crops = setOf(upperHp, lowerHp, upperSprite, lowerSprite)
    private var now = 10 * SECOND
    private fun gate() = InactiveBenchSnapshotGate(crops, now = { now })
    private fun resolveAll(gate: InactiveBenchSnapshotGate) = crops.forEach { gate.measured(it, now) }

    @Test fun `bench remains pending until each interpreter supplies usable testimony`() {
        val gate = gate()
        gate.observe(article(MatchStarted))
        crops.forEach(gate::captured)
        assertTrue(crops.all(gate::isEnabled))
        gate.observe(article(PlayerInactiveHpBarMeasured(0, .8f)))
        gate.observe(article(PlayerInactiveSpeciesSpriteFingerprintMeasured(0, "0123", 0, 0, 8, 8)))
        assertFalse(gate.isEnabled(upperHp))
        assertFalse(gate.isEnabled(upperSprite))
        assertTrue(gate.isEnabled(lowerHp))
        assertTrue(gate.isEnabled(lowerSprite))
        now += 3 * SECOND
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `opponent alone never wakes bench but first player surface does`() {
        val gate = gate()
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Golbat")))
        gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT)))
        assertTrue(crops.none(gate::isEnabled))
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
        assertTrue(crops.all(gate::isEnabled))
    }

    @Test fun `same species recovery and charged phase do not wake completed cards`() {
        val gate = gate()
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
        resolveAll(gate)
        now += SECOND
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "turtonator")))
        gate.observe(article(GetReadyWitnessed))
        gate.observe(article(ChargeMoveUsedAnnounced))
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `player replacement and faint open new bounded assignments`() {
        val gate = gate()
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
        resolveAll(gate)
        now += SECOND
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist")))
        assertTrue(crops.all(gate::isEnabled))
        resolveAll(gate)
        now += SECOND
        gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Gourgeist")))
        assertTrue(crops.all(gate::isEnabled))
    }

    @Test fun `completed source timestamps and older identities cannot reopen cards`() {
        val gate = gate()
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
        resolveAll(gate)
        now += SECOND
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist")))
        resolveAll(gate)
        gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER), now))
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator"), now - 1))
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `delayed player identity opens one fresh bench window and same name cannot extend it`() {
        val gate = gate()
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
        resolveAll(gate)
        val delayedEntryAt = now + SECOND
        now += 5 * SECOND
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist"), delayedEntryAt))
        val receivedAt = now
        assertTrue(crops.all(gate::isEnabled))
        assertFalse(gate.acceptsFrame(upperHp, receivedAt - 1))
        now += SECOND
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist")))
        assertTrue(crops.all(gate::isEnabled))
        now = receivedAt + 3 * SECOND
        assertTrue(crops.none(gate::isEnabled))
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist")))
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `recorded delayed switches give bench current frames a full three seconds`() {
        for (lag in listOf(6 * SECOND, 10 * SECOND, 2_429_000_000L, 2_926_000_000L)) {
            now = 10 * SECOND
            val gate = gate()
            gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
            resolveAll(gate)
            val switchAt = now + SECOND
            now = switchAt + lag
            val receivedAt = now
            val cue = article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist"), switchAt)
            gate.observe(cue)
            assertTrue("lag=$lag", crops.all(gate::isEnabled))
            assertEquals(switchAt, cue.monotonicTimeNanos!!)
            assertFalse(gate.acceptsFrame(upperHp, receivedAt - 1))
            assertTrue(gate.acceptsFrame(upperHp, receivedAt))
            gate.measured(upperHp, receivedAt - 1)
            assertTrue(gate.isEnabled(upperHp))
            now += 200_000_000L
            assertTrue(gate.acceptsFrame(upperHp, now))
            gate.measured(upperHp, now)
            assertFalse(gate.isEnabled(upperHp))
            now = receivedAt + 3 * SECOND - 1
            gate.observe(cue)
            assertTrue(gate.isEnabled(lowerHp))
            assertFalse(gate.isEnabled(upperHp))
            now++
            assertTrue(crops.none(gate::isEnabled))
            gate.observe(cue)
            assertTrue(crops.none(gate::isEnabled))
        }
    }

    @Test fun `later successful bench readings cover an older delayed switch cue`() {
        val gate = gate()
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
        val switchAt = now + SECOND
        now += 2 * SECOND
        resolveAll(gate)
        now += 10 * SECOND
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist"), switchAt))
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `same name confirmation alone does not supersede delayed bench lifecycle cue`() {
        val gate = gate()
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
        resolveAll(gate)
        val faintAt = now + SECOND
        now += 2 * SECOND
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator")))
        now += 10 * SECOND
        gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER), faintAt))
        assertTrue(crops.all(gate::isEnabled))
        assertFalse(gate.acceptsFrame(upperHp, now - 1))
    }

    @Test fun `old lifecycle and identity cannot cancel or rewind restarted bench acquisition`() {
        val gate = gate()
        gate.observe(article(MatchStarted))
        now += 10 * SECOND
        val restartedAt = now
        gate.observe(article(ObservationRestartedByUser, restartedAt))
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist")))
        now += SECOND
        gate.measured(upperHp, now)
        gate.observe(article(ObservationSessionStoppedByUser, restartedAt - 1))
        gate.observe(article(MatchEnded(BattleResult.LOSS), restartedAt - 1))
        gate.observe(article(ObservationRestartedByUser, restartedAt))
        gate.observe(article(ObservationRestartedByUser, restartedAt - 1))
        gate.observe(article(ObservationRestartedByUser, now + SECOND))
        gate.observe(article(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Turtonator"), restartedAt - 1))
        assertFalse(gate.isEnabled(upperHp))
        assertTrue(gate.isEnabled(lowerHp))
        gate.observe(article(MatchEnded(BattleResult.WIN)))
        gate.observe(article(ObservationRestartedByUser, restartedAt + 1))
        gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER)))
        assertTrue(crops.none(gate::isEnabled))
        now += SECOND
        gate.observe(article(ObservationRestartedByUser))
        gate.observe(article(MatchStarted))
        assertTrue(crops.all(gate::isEnabled))
    }

    @Test fun `late player lifecycle report cannot reopen a completed source time assignment`() {
        val gate = gate()
        gate.observe(article(MatchStarted))
        resolveAll(gate)
        val completedAt = now
        now += SECOND
        gate.observe(article(SpeciesCheckMeasured(ActivePokemonSide.PLAYER, 1, "OPENED", "SWITCH",
            completedAt, SECOND, SECOND)))
        assertTrue(crops.none(gate::isEnabled))
    }

    @Test fun `result clears cards and explicit restart does not admit earlier cues`() {
        val gate = gate()
        gate.observe(article(MatchStarted))
        gate.observe(article(MatchEnded(BattleResult.WIN)))
        now += SECOND
        gate.observe(article(ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER)))
        assertTrue(crops.none(gate::isEnabled))
        gate.observe(article(ObservationRestartedByUser))
        gate.observe(article(MatchStarted, now - SECOND))
        assertTrue(crops.none(gate::isEnabled))
        now += SECOND
        gate.observe(article(MatchStarted))
        assertFalse(gate.acceptsFrame(upperHp, now - 1))
        assertTrue(gate.acceptsFrame(upperHp, now))
    }

    private fun article(payload: TestimonyPayload, at: Long = now) = RealityArticle(
        ArticleId(java.util.UUID.randomUUID().toString()), 0, 0, SourceId("TEST"), payload, monotonicTimeNanos = at)
    private companion object { const val SECOND = 1_000_000_000L }
}
