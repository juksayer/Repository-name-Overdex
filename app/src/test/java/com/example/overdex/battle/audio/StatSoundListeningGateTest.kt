package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class StatSoundListeningGateTest {
    private fun seconds(value: Long) = value * 1_000_000_000L

    @Test fun `ordinary battle and visual hits never open stat matching`() {
        val gate = StatSoundListeningGate()
        listOf(MatchStarted, VsScreenWitnessed, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt"),
            ActiveHpBarDamageTickMeasured(ActivePokemonSide.OPPONENT, 1f, .9f, .1f)).forEach {
            gate.observe(it, seconds(10))
            assertFalse(gate.allowsScan(seconds(11)))
        }
    }

    @Test fun `each charged phase has a source-time deadline and never renews itself`() {
        for (cue in listOf(AttackIncoming, GetReadyWitnessed)) {
            val gate = StatSoundListeningGate()
            gate.observe(cue, seconds(10))
            assertFalse(gate.allowsScan(seconds(9)))
            assertTrue(gate.allowsScan(seconds(10)))
            assertTrue(gate.allowsScan(seconds(25)))
            assertFalse(gate.allowsScan(seconds(25) + 1))
            gate.observe(cue, seconds(10)) // Late duplicate must not restart a wall-clock timer.
            assertFalse(gate.allowsScan(seconds(30)))
        }
    }

    @Test fun `used text and independent QTE evidence open short effect tails`() {
        for (cue in listOf(ChargeMoveUsedAnnounced, ChargeMoveQteVibrationPatternInferred(pulseCount = 2,
            windowNanos = seconds(1)))) {
            val gate = StatSoundListeningGate()
            gate.observe(cue, seconds(20))
            assertTrue(gate.allowsScan(seconds(27)))
            assertFalse(gate.allowsScan(seconds(27) + 1))
        }
        val gate = StatSoundListeningGate()
        gate.observe(RawTestimony("GREAT!"), seconds(20), "ANNOUNCEMENT_WITNESS")
        assertFalse(gate.allowsScan(seconds(21)))
        gate.observe(RawTestimony("NICEIIII"), seconds(20), "QTE_PROGRESS_TEXT_WITNESS")
        assertFalse(gate.allowsScan(seconds(21)))
        gate.observe(RawTestimony("EXCELLENT!"), seconds(20), "QTE_PROGRESS_TEXT_WITNESS")
        assertTrue(gate.allowsScan(seconds(27)))
        assertFalse(gate.allowsScan(seconds(28)))
    }

    @Test fun `source order preserves the latest phase while rejecting obsolete queued audio`() {
        val gate = StatSoundListeningGate()
        gate.observe(GetReadyWitnessed, seconds(50))
        gate.observe(ChargeMoveUsedAnnounced, seconds(10))
        assertTrue(gate.allowsScan(seconds(60)))
        assertFalse(gate.allowsScan(seconds(16), now = seconds(60)))
        assertFalse(gate.allowsScan(seconds(60), now = seconds(63)))
        assertFalse(gate.allowsScan(seconds(65), now = seconds(66)))
        assertTrue(gate.allowsScan(seconds(60), now = seconds(61)))
    }

    @Test fun `result retires recognition while only existing probes can finish`() {
        val gate = StatSoundListeningGate()
        gate.observe(GetReadyWitnessed, seconds(10))
        val requestedGeneration = gate.generation
        assertTrue(gate.canRequestProbe(seconds(15)))
        gate.observe(MatchEnded(BattleResult.WIN), seconds(18))
        assertFalse(gate.allowsScan(seconds(17)))
        assertFalse(gate.canRequestProbe(seconds(15)))
        assertTrue(gate.acceptsPendingProbe(seconds(15), requestedGeneration))
        gate.observe(ChargeMoveUsedAnnounced, seconds(16))
        assertFalse(gate.allowsScan(seconds(19)))
    }

    @Test fun `manual boundaries invalidate queued probes and old phase evidence`() {
        val gate = StatSoundListeningGate()
        gate.observe(GetReadyWitnessed, seconds(10))
        val oldGeneration = gate.generation
        gate.observe(ObservationRestartedByUser, seconds(20))
        gate.observe(GetReadyWitnessed, seconds(10))
        assertFalse(gate.allowsScan(seconds(21)))
        assertFalse(gate.acceptsPendingProbe(seconds(15), oldGeneration))
        gate.observe(GetReadyWitnessed, seconds(25))
        assertTrue(gate.allowsScan(seconds(26)))
        gate.observe(ScreenIgnoredByUser, seconds(27))
        assertFalse(gate.allowsScan(seconds(28)))
        assertFalse(gate.acceptsPendingProbe(seconds(25), gate.generation - 1))
        gate.observe(ObservationRestartedByUser, seconds(30))
        gate.observe(GetReadyWitnessed, seconds(31))
        gate.observe(ObservationSessionStoppedByUser, seconds(32))
        assertFalse(gate.allowsScan(seconds(33)))
    }

    @Test fun `quiet analysis retains original audio for a delayed charged announcement`() {
        val gate = StatSoundListeningGate()
        val history = TimedStatSoundProbeBuffer()
        for (second in 0 until 20) {
            val at = seconds(second.toLong() + 1)
            assertFalse(gate.allowsScan(at))
            history.ingest(LivePcmChunk(Pcm16Audio(8_000, 1, ShortArray(8_000) { second.toShort() }),
                second.toLong(), at, 100_000 + (second + 1) * 1_000L, "TEST"))
        }
        gate.observe(ChargeMoveUsedAnnounced, seconds(10)) // Delivered at +20 seconds.
        assertFalse(gate.allowsScan(seconds(20)))
        assertTrue(gate.canRequestProbe(seconds(10)))
        val retained = history.read(seconds(10)) as TimedStatSoundProbeBuffer.Read.Ready
        assertEquals(4, retained.audio.samples.first().toInt())
        assertEquals(16, retained.audio.samples.last().toInt())
        assertEquals(seconds(17), retained.endNanos)
    }
}
