package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class SwitchTimerCaptureGateTest {
    private fun seconds(value: Long) = value * 1_000_000_000L
    private fun article(payload: TestimonyPayload, at: Long) = RealityArticle(
        ArticleId("$at-$payload"), at / 1_000_000, at / 1_000_000, SourceId("TEST"), payload,
        monotonicTimeNanos = at,
    )
    private fun player(name: String) = ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, name)
    private fun resolved(observed: String, name: String, at: Long, slot: Int = 1) =
        PlayerActiveIdentityResolved(observed, name, null, slot, at, "TEST_OBSERVED_APPEARANCE")

    @Test fun `first player identity and repeated names never request switch timer pixels`() {
        var now = seconds(10)
        val gate = SwitchTimerCaptureGate { now }
        gate.observe(article(MatchStarted, now))
        gate.observe(article(player("Camerupt"), now))
        assertFalse(gate.isEnabled())
        now = seconds(20)
        gate.observe(article(player(" camerupt "), now))
        assertFalse(gate.isEnabled())
        assertFalse(gate.acceptsFrame(now))
    }

    @Test fun `changed player species acquires exactly three seconds of source-time frames`() {
        var now = seconds(10)
        val gate = SwitchTimerCaptureGate { now }
        gate.observe(article(player("Camerupt"), now))
        now = seconds(20)
        gate.observe(article(player("Gourgeist"), now))
        assertTrue(gate.isEnabled())
        assertFalse(gate.acceptsFrame(seconds(19)))
        assertTrue(gate.acceptsFrame(seconds(20)))
        assertFalse(gate.acceptsFrame(seconds(21))) // A future frame is not acquired evidence.
        now = seconds(22)
        assertTrue(gate.acceptsFrame(seconds(21)))
        gate.observe(article(player("Gourgeist"), now))
        now = seconds(23)
        assertFalse(gate.isEnabled())
        assertFalse(gate.acceptsFrame(seconds(22)))
    }

    @Test fun `delayed changed identity cannot start a new wall-time acquisition`() {
        var now = seconds(10)
        val gate = SwitchTimerCaptureGate { now }
        gate.observe(article(player("Camerupt"), now))
        now = seconds(30)
        gate.observe(article(player("Gourgeist"), seconds(20)))
        assertFalse(gate.isEnabled())
        gate.observe(article(player("Camerupt"), seconds(15)))
        assertFalse(gate.isEnabled())
        gate.observe(article(player("Gourgeist"), seconds(30)))
        assertFalse("Repeated current identity cannot revive its expired assignment", gate.isEnabled())
    }

    @Test fun `opponent changes charges faints and countdown do not impersonate a player switch`() {
        var now = seconds(10)
        val gate = SwitchTimerCaptureGate { now }
        gate.observe(article(player("Camerupt"), now))
        now = seconds(20)
        listOf(ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Golbat"),
            GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced,
            ActivePokemonFaintedWitnessed(ActivePokemonSide.PLAYER, "Camerupt"),
            CountdownGlyphWitnessed("GO", .99f, 1)).forEach {
            gate.observe(article(it, now))
            assertFalse(gate.isEnabled())
        }
        // Replacement may still request neutral pixels; it is not labelled a voluntary switch.
        gate.observe(article(player("Gourgeist"), now))
        assertTrue(gate.isEnabled())
    }

    @Test fun `resolved same-appearance refinement is baseline metadata not a switch`() {
        var now = seconds(10)
        val gate = SwitchTimerCaptureGate { now }
        gate.observe(article(player("Raichu"), now))
        gate.observe(article(resolved("Raichu", "Raichu (Alolan)", now), now))
        assertFalse(gate.isEnabled())
        now = seconds(20)
        gate.observe(article(player("Raichu"), now))
        gate.observe(article(resolved("Raichu", "Raichu (Alolan)", seconds(10)), seconds(10)))
        assertFalse(gate.isEnabled())
    }

    @Test fun `explicit newly resolved appearance can acquire even for a same-name combatant`() {
        var now = seconds(10)
        val gate = SwitchTimerCaptureGate { now }
        gate.observe(article(resolved("Raichu", "Raichu", now), now))
        assertFalse(gate.isEnabled())
        now = seconds(20)
        gate.observe(article(resolved("Raichu", "Raichu", now, slot = 2), now))
        assertTrue(gate.isEnabled())
        now = seconds(23)
        assertFalse(gate.isEnabled())
        gate.observe(article(resolved("Raichu", "Raichu", seconds(20), slot = 2), seconds(20)))
        assertFalse(gate.isEnabled())
    }

    @Test fun `completion and manual boundaries retire capture and reject stale attempt evidence`() {
        for (end in listOf(MatchEnded(BattleResult.WIN), ScreenIgnoredByUser, ObservationSessionStoppedByUser)) {
            var now = seconds(10)
            val gate = SwitchTimerCaptureGate { now }
            gate.observe(article(player("Camerupt"), now))
            now = seconds(20)
            gate.observe(article(player("Gourgeist"), now))
            assertTrue(gate.isEnabled())
            gate.observe(article(end, now))
            assertFalse(gate.isEnabled())
            assertFalse(gate.acceptsFrame(now))
            now = seconds(21)
            gate.observe(article(player("Turtonator"), now))
            assertFalse(gate.isEnabled())
            now = seconds(30)
            gate.observe(article(ObservationRestartedByUser, now))
            gate.observe(article(player("Gourgeist"), seconds(20)))
            gate.observe(article(player("Turtonator"), now))
            assertFalse("The restarted attempt's initial entry remains baseline", gate.isEnabled())
            now = seconds(31)
            gate.observe(article(player("Camerupt"), now))
            assertTrue(gate.isEnabled())
            assertFalse(gate.acceptsFrame(seconds(20)))
            gate.stop()
            assertFalse(gate.isEnabled())
        }
    }
}
