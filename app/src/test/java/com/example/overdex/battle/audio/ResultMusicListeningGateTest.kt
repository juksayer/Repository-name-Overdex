package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.*
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class ResultMusicListeningGateTest {
    @Test fun `loading screens and preflight music are ineligible even with a visible HUD`() {
        val gate = ResultMusicListeningGate()
        assertFalse(gate.allows(100, true))
        gate.observe(MatchStarted, 100)
        assertFalse(gate.allows(90, true))
        assertFalse(gate.allows(200, false))
        assertTrue(gate.allows(200, true))
    }

    @Test fun `ignored loading screen and a manual retry do not disable music for the eventual battle`() {
        val gate = ResultMusicListeningGate()
        gate.observe(ScreenIgnoredByUser, 100)
        gate.observe(ObservationRestartedByUser, 200)
        assertFalse(gate.allows(300, true))
        gate.observe(MatchStarted, 400)
        assertTrue(gate.allows(500, true))
    }

    @Test fun `result grace is bounded and explicit stop always wins`() {
        val gate = ResultMusicListeningGate()
        gate.observe(MatchStarted, 1_000_000_000)
        gate.observe(MatchEnded(BattleResult.WIN), 100_000_000_000)
        assertTrue(gate.allows(105_000_000_000, false))
        assertFalse(gate.allows(121_000_000_000, true))
        gate.observe(ObservationSessionStoppedByUser, 106_000_000_000)
        assertFalse(gate.allows(107_000_000_000, true))
        assertFalse(ResultMusicListeningGate().allows(108_000_000_000, true))
    }
}
