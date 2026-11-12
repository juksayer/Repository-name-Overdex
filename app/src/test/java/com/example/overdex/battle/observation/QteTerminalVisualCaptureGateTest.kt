package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

/** Scheduling simulations, including original recorded cue clocks; no invented archived pixels. */
class QteTerminalVisualCaptureGateTest {
    private val second = 1_000_000_000L
    private var clock = 100 * second
    private val start = clock
    private val opponent = ActivePokemonSide.OPPONENT
    private val player = ActivePokemonSide.PLAYER
    private val gate = TerminalVisualCaptureGate { clock }.also { it.beginSession(clock) }
    private fun cue(at: Long = clock, id: String = "grade-$at",
        kind: AcceptedChargeCaptureCueKind = AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS) =
        AcceptedChargeCaptureCue(kind, id, at, clock, 1_000L + at / 1_000_000)
    private fun article(id: String, payload: TestimonyPayload, at: Long = clock, source: String = "TEST") =
        RealityArticle(ArticleId(id), 1_000L + at / 1_000_000, 999_999L, SourceId(source), payload,
            evidenceReferences = listOf("crop-$id"), monotonicTimeNanos = at)
    private fun image(id: String = "image-$clock") {
        val ticket = requireNotNull(gate.takeFrame(opponent, clock))
        gate.finishFrame(ticket, 1_000L + clock / 1_000_000, true)
        gate.published(opponent, clock, id)
    }

    @Test fun `recorded final Cramorant timing captures before late Used without requiring HP`() {
        // Original 21:13 archive: grade crop e70d4cff, early receipt 67b62448,
        // Used 15178b40. These are offsets from its original Match Start.
        val grade = start + 135_722_650_469L
        clock = start + 138_141_021_316L
        assertTrue(gate.prepare(cue(grade, "e70d4cff-38bb-4771-99e8-0a12d5c17d74")))
        val window = requireNotNull(gate.captureWindow(opponent))
        assertEquals(grade + 8_500_000_000L, window.until)
        image("sample-before-used-publication")
        clock = start + 141_500_000_000L
        image("sample-during-terminal-interval")
        clock = start + 143_080_000_000L
        gate.observe(article("15178b40", ChargeMoveUsedAnnounced, start + 138_709_973_239L))
        assertEquals(window, gate.captureWindow(opponent))
        assertFalse(gate.requestChargedReturn(opponent, clock))
        clock = window.until
        assertFalse(gate.isCapturing(opponent))
        val sequence = gate.drainCompleted().single()
        assertEquals("PLAYER_QTE_PROGRESS", sequence.payload.trigger)
        assertEquals(2, sequence.payload.frameCount)
        assertEquals(start + 141_500_000_000L, sequence.payload.sourceEndedAtNanos)
    }

    @Test fun `QTE cadence and total attempts stay bounded even with repeated grades`() {
        assertTrue(gate.prepare(cue()))
        val window = requireNotNull(gate.captureWindow(opponent))
        repeat(34) { index ->
            clock = start + index * 250_000_000L
            assertFalse(gate.prepare(cue(id = "repeated-$index")))
            image()
            clock += 100_000_000L
            assertNull(gate.takeFrame(opponent, clock))
        }
        assertFalse(gate.isCapturing(opponent))
        assertEquals(34, gate.drainCompleted().single().payload.frameCount)
        clock = window.until + second
        assertFalse("Another grade alone cannot replenish an expired phase", gate.prepare(cue()))
    }

    @Test fun `canonical grade fallback cannot renew early budget or mistake another reader for QTE`() {
        gate.observe(article("wrong-reader", RawTestimony("GREAT!")))
        assertFalse(gate.isCapturing(opponent))
        gate.observe(article("native", RawTestimony("GREAT:"), source = "QTE_PROGRESS_TEXT_WITNESS"))
        assertFalse(gate.isCapturing(opponent))
        gate.observe(article("accepted", RawTestimony("GREAT!"), source = "QTE_PROGRESS_TEXT_WITNESS"))
        val first = requireNotNull(gate.captureWindow(opponent))
        clock += second
        gate.observe(article("later-grade", RawTestimony("EXCELLENT!"), source = "QTE_PROGRESS_TEXT_WITNESS"))
        assertEquals(first, gate.captureWindow(opponent))
        assertFalse(gate.prepare(cue(start)))
    }

    @Test fun `expiry never moves from source time to recognition time`() {
        clock = start + TerminalVisualCaptureGate.QTE_WINDOW_NANOS
        assertFalse(gate.prepare(cue(start)))
        assertFalse(gate.prepare(cue(clock + 1)))
        assertFalse(gate.prepare(cue().copy(recognizedAtNanos = clock + 1)))
        assertTrue(gate.prepare(cue(clock - second)))
        val window = requireNotNull(gate.captureWindow(opponent))
        assertNull(gate.takeFrame(opponent, clock - second))
        assertEquals(start + 16 * second, window.until)
    }

    @Test fun `Used-first delivery cannot attach an old grade to a completed phase`() {
        clock += 4 * second
        gate.observe(article("used-without-phase", ChargeMoveUsedAnnounced, start + 3 * second))
        assertFalse(gate.prepare(cue(start)))
        assertFalse(gate.isCapturing(opponent))
        assertFalse(gate.isCapturing(player))
    }

    @Test fun `incoming between player grade and Carbink move retires opponent window`() {
        assertTrue(gate.prepare(cue()))
        clock += 17 * second
        assertTrue(gate.prepare(cue(kind = AcceptedChargeCaptureCueKind.ATTACK_INCOMING)))
        assertFalse(gate.isCapturing(opponent))
        assertFalse(gate.prepare(cue(start)))
        clock += 5 * second
        gate.observe(article("carbink-rock-slide", ChargeMoveUsedAnnounced))
        assertTrue(gate.isCapturing(player))
        assertFalse(gate.isCapturing(opponent))
    }

    @Test fun `new opposing phase immediately closes still active QTE and old phase cannot replace it`() {
        assertTrue(gate.prepare(cue()))
        clock += second
        assertTrue(gate.prepare(cue(kind = AcceptedChargeCaptureCueKind.ATTACK_INCOMING)))
        assertFalse(gate.isCapturing(opponent))
        gate.observe(article("late-ready", GetReadyWitnessed, start))
        assertFalse(gate.prepare(cue(start + second / 2)))
        clock += second
        gate.observe(article("incoming-used", ChargeMoveUsedAnnounced))
        assertTrue(gate.isCapturing(player))
    }

    @Test fun `older identity delivered after grade preserves newer owned window and real replacement closes it`() {
        gate.observe(article("old", ActivePokemonSpeciesWitnessed(opponent, "Annihilape")))
        clock += 6 * second
        assertTrue(gate.prepare(cue()))
        val window = gate.captureWindow(opponent)
        clock += second
        gate.observe(article("late-cramorant", ActivePokemonSpeciesWitnessed(opponent, "Cramorant",
            appearanceStartedAtNanos = start + 2 * second), start + 3 * second))
        assertEquals(window, gate.captureWindow(opponent))
        gate.observe(article("replacement", ActivePokemonSpeciesWitnessed(opponent, "Quagsire")))
        assertFalse(gate.isCapturing(opponent))
        assertFalse(gate.prepare(cue(start + 6 * second)))
    }

    @Test fun `player replacement also invalidates a player charge assignment`() {
        gate.observe(article("player-first", ActivePokemonSpeciesWitnessed(player, "Camerupt")))
        clock += second
        assertTrue(gate.prepare(cue()))
        clock += second
        gate.observe(article("player-replacement", ActivePokemonSpeciesWitnessed(player, "Gourgeist")))
        assertFalse(gate.isCapturing(opponent))
    }

    @Test fun `old faint cannot retire later source-owned phase but current faint can`() {
        clock += 3 * second
        assertTrue(gate.prepare(cue()))
        gate.observe(article("late-faint", ActivePokemonFaintedWitnessed(opponent), start + second))
        assertTrue(gate.isCapturing(opponent))
        clock += second
        gate.observe(article("faint-now", ActivePokemonFaintedWitnessed(opponent)))
        assertFalse(gate.isCapturing(opponent))
    }

    @Test fun `dimension change retires old window and old frame or cue cannot resurrect it`() {
        assertTrue(gate.observeDimensions(672, 1493, clock))
        assertTrue(gate.prepare(cue()))
        clock += second
        assertTrue(gate.observeDimensions(1080, 2400, clock))
        assertFalse(gate.isCapturing(opponent))
        assertFalse(gate.observeDimensions(672, 1493, start))
        assertFalse(gate.prepare(cue(start)))
    }

    @Test fun `future or pre-session dimensions cannot change active ownership`() {
        assertFalse(gate.observeDimensions(816, 1808, start - 1))
        assertTrue(gate.observeDimensions(672, 1493, clock))
        assertTrue(gate.prepare(cue()))
        val window = gate.captureWindow(opponent)
        assertFalse(gate.observeDimensions(1080, 2400, clock + second))
        assertEquals(window, gate.captureWindow(opponent))
        clock += second / 2
        assertTrue(gate.observeDimensions(672, 1493, clock))
        assertEquals(window, gate.captureWindow(opponent))
    }

    @Test fun `end stop and restart reject old callbacks without disabling a new independent phase`() {
        assertTrue(gate.prepare(cue()))
        clock += second
        gate.observe(article("end", MatchEnded(BattleResult.WIN)))
        assertFalse(gate.prepare(cue()))
        clock += second
        gate.beginSession(clock)
        assertFalse(gate.prepare(cue(start)))
        assertTrue(gate.prepare(cue()))
        gate.endSession()
        assertFalse(gate.prepare(cue()))
    }

    @Test fun `genuine next QTE after used owns a new budget without extending previous phase`() {
        assertTrue(gate.prepare(cue()))
        val first = requireNotNull(gate.captureWindow(opponent))
        clock += 4 * second
        gate.observe(article("first-used", ChargeMoveUsedAnnounced))
        assertEquals(first, gate.captureWindow(opponent))
        clock += 8 * second
        assertTrue(gate.prepare(cue(kind = AcceptedChargeCaptureCueKind.GET_READY)))
        clock += 5 * second
        assertTrue(gate.prepare(cue()))
        val next = requireNotNull(gate.captureWindow(opponent))
        assertNotEquals(first.id, next.id)
        assertEquals(clock + TerminalVisualCaptureGate.QTE_WINDOW_NANOS, next.until)
    }
}
