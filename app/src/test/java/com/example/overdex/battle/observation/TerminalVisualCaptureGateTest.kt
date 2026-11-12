package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class TerminalVisualCaptureGateTest {
    private var clock = 100_000_000_000L
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val gate = TerminalVisualCaptureGate { clock }.also { it.beginSession(clock) }
    private fun article(id: String, payload: TestimonyPayload, at: Long = clock) = RealityArticle(
        ArticleId(id), 1_000L + at / 1_000_000L, 99_999L, SourceId("RAW_TEST_SOURCE"), payload,
        monotonicTimeNanos = at,
    )
    private fun hp(id: String, fraction: Float, side: ActivePokemonSide = player, at: Long = clock) =
        article(id, ActiveHpBarMeasured(side, 10, 30, 150, 40, fraction, 200, 400, "RED"), at)
    private fun request(side: ActivePokemonSide = player, id: String = "trigger") =
        gate.request(side, TerminalVisualTrigger.DEPARTURE, clock, listOf(id))
    private fun preserve(side: ActivePokemonSide = player, id: String = "crop-$clock", publishFirst: Boolean = false) {
        val ticket = requireNotNull(gate.takeFrame(side, clock))
        if (publishFirst) gate.published(side, clock, id)
        gate.finishFrame(ticket, 5_000L + clock / 1_000_000L, preserved = true)
        if (!publishFirst) gate.published(side, clock, id)
    }

    @Test fun `fixed window caps source frames and repeated cues cannot extend it`() {
        val start = clock
        assertTrue(request())
        repeat(30) { index ->
            clock = start + index * 100_000_000L
            assertFalse(request(id = "repeat-$index"))
            preserve(id = "crop-$index")
            assertNull(gate.takeFrame(player, clock + 1))
        }
        val sequence = gate.drainCompleted().single()
        assertEquals(30, sequence.payload.frameCount)
        assertEquals(start, sequence.payload.sourceStartedAtNanos)
        assertEquals(clock, sequence.payload.sourceEndedAtNanos)
        assertEquals(31, sequence.evidenceReferences.size)
        clock = start + TerminalVisualCaptureGate.WINDOW_NANOS
        assertNull(gate.takeFrame(player, clock))
        assertFalse(request(id = "cooldown"))
        clock += TerminalVisualCaptureGate.COOLDOWN_NANOS
        assertTrue(request(id = "new-event"))
    }

    @Test fun `stale future preroll and regressed frames never enter a source sequence`() {
        assertFalse(gate.request(player, TerminalVisualTrigger.DEPARTURE,
            clock - TerminalVisualCaptureGate.WINDOW_NANOS, listOf("old")))
        assertFalse(gate.request(player, TerminalVisualTrigger.DEPARTURE, clock + 1, listOf("future")))
        assertTrue(request())
        assertNull(gate.takeFrame(player, clock - 1))
        preserve()
        assertNull(gate.takeFrame(player, clock))
        clock += 1_000_000_000L
        assertNull(gate.takeFrame(player, clock - 600_000_000L))
    }

    @Test fun `sequence cites real published crop articles and retains capture clocks under delayed publication`() {
        assertTrue(request())
        val at = clock
        val ticket = requireNotNull(gate.takeFrame(player, at))
        gate.finishFrame(ticket, wallTime = 7_654L, preserved = true)
        clock += 3_000_000_000L
        assertTrue(gate.drainCompleted().isEmpty())
        gate.published(player, at, "raw-crop-id")
        val sequence = gate.drainCompleted().single()
        assertEquals(at, sequence.payload.sourceStartedAtNanos)
        assertEquals(at, sequence.payload.sourceEndedAtNanos)
        assertEquals(7_654L, sequence.perceivedAt)
        assertEquals(listOf("trigger", "raw-crop-id"), sequence.evidenceReferences)
        assertTrue(gate.drainCompleted().isEmpty())
    }

    @Test fun `publication before persistence acknowledgement still cites that actual crop once`() {
        assertTrue(request())
        preserve(id = "raw", publishFirst = true)
        gate.endSession()
        assertEquals(listOf("trigger", "raw"), gate.drainCompleted().single().evidenceReferences)
    }

    @Test fun `obscuration and failed persistence are not absent sprite observations`() {
        assertTrue(request())
        val ticket = requireNotNull(gate.takeFrame(player, clock))
        gate.finishFrame(ticket, 10L, preserved = false)
        clock += 3_000_000_000L
        assertTrue(gate.drainCompleted().isEmpty())
    }

    @Test fun `fast moves need fresh independently measured low HP and cannot use historical named faints`() {
        gate.observe(article("old-faint", ActivePokemonFaintedWitnessed(player, "Gourgeist"), clock - 1_000_000_000L))
        assertFalse(gate.request(player, TerminalVisualTrigger.INDEPENDENT_IMPACT_WITH_LOW_HP, clock, listOf("impact")))
        gate.observe(hp("low", .1f))
        assertTrue(gate.isCapturing(player))
        clock += 4_000_000_000L
        gate.observe(article("fast-old-hp", FastMoveUseObserved("use1", opponent, player, evidenceKinds = listOf("VISUAL"))))
        assertFalse(gate.isCapturing(player))
        // An unchanged fresh low reading updates context without itself reopening a window.
        gate.observe(hp("fresh-low", .1f))
        assertFalse(gate.isCapturing(player))
        gate.observe(article("fast", FastMoveUseObserved("use2", opponent, player, evidenceKinds = listOf("VISUAL"))))
        assertTrue(gate.isCapturing(player))
    }

    @Test fun `new actor clears low HP and old delayed HP cannot arm its successor`() {
        gate.observe(article("first", ActivePokemonSpeciesWitnessed(player, "Gourgeist")))
        gate.observe(hp("low", .1f))
        clock += 200_000_000L
        gate.observe(article("second", ActivePokemonSpeciesWitnessed(player, "Camerupt")))
        assertFalse(gate.isCapturing(player))
        gate.observe(hp("old-late", .01f, at = clock - 100_000_000L))
        clock += 4_000_000_000L
        assertFalse(gate.request(player, TerminalVisualTrigger.INDEPENDENT_IMPACT_WITH_LOW_HP, clock, listOf("impact")))
    }

    @Test fun `stop ignore and result end capture without inventing an unseen final frame`() {
        for (end in listOf<TestimonyPayload>(ScreenIgnoredByUser, ObservationSessionStoppedByUser, MatchEnded(BattleResult.LOSS))) {
            gate.beginSession(clock)
            assertTrue(request())
            preserve(id = "actual")
            val capturedAt = clock
            clock += 200_000_000L
            gate.observe(article("end", end))
            assertFalse(gate.isCapturing(player))
            assertFalse(request(id = "after-end"))
            assertEquals(capturedAt, gate.drainCompleted().single().payload.sourceEndedAtNanos)
        }
    }

    @Test fun `living recall remains a neutral raw sequence`() {
        // Real-video negative: Carbink recalls alive at181.421-181.655. The
        // scheduling reason is deliberately not a semantic recall/faint label.
        assertTrue(gate.request(opponent, TerminalVisualTrigger.DEPARTURE, clock, listOf("source-cue")))
        preserve(opponent, "source-image")
        gate.endSession()
        val payload = gate.drainCompleted().single().payload
        assertEquals("DEPARTURE", payload.trigger)
        assertEquals(1, payload.frameCount)
        assertFalse((payload as TestimonyPayload) is ActivePokemonFaintedWitnessed)
    }

    @Test fun `duplicate source trigger cannot reopen a window after cooldown`() {
        assertTrue(request())
        clock += 4_000_000_000L
        assertFalse(request())
        assertTrue(request(id = "independent-next"))
    }

    @Test fun `one side-owned charge announcement arms raw capture and repeated OCR does not extend it`() {
        gate.observe(article("ready", GetReadyWitnessed))
        assertFalse(gate.isCapturing(opponent))
        clock += 8_000_000_000L
        gate.observe(article("used", ChargeMoveUsedAnnounced))
        val start = clock
        assertTrue(gate.isCapturing(opponent))
        preserve(opponent, "charge-frame")
        clock += 2_500_000_000L
        gate.observe(article("used-again", ChargeMoveUsedAnnounced))
        clock = start + 3_000_000_000L
        assertFalse(gate.isCapturing(opponent))
        val sequence = gate.drainCompleted().single()
        assertEquals("CHARGED_IMPACT", sequence.payload.trigger)
        assertEquals(listOf("ready", "used", "charge-frame"), sequence.evidenceReferences)
    }

    @Test fun `unowned charge words and decreasing low readings cannot repeatedly reopen capture`() {
        gate.observe(article("used-without-side", ChargeMoveUsedAnnounced))
        assertFalse(gate.isCapturing(player))
        assertFalse(gate.isCapturing(opponent))
        gate.observe(hp("low", .2f))
        clock += 4_000_000_000L
        gate.observe(hp("lower", .1f))
        assertFalse(gate.isCapturing(player))
        gate.observe(hp("zero", 0f))
        assertFalse(gate.isCapturing(player))
    }

    @Test fun `delayed charge OCR captures only remainder of original source window`() {
        gate.observe(article("incoming", AttackIncoming))
        clock += 8_000_000_000L
        val usedAt = clock
        clock += 900_000_000L
        gate.observe(article("delayed-used", ChargeMoveUsedAnnounced, usedAt))
        assertTrue(gate.isCapturing(player))
        assertNull(gate.takeFrame(player, usedAt)) // old pixels remain stale
        preserve(id = "actual-after-delivery")
        clock = usedAt + TerminalVisualCaptureGate.WINDOW_NANOS
        assertFalse(gate.isCapturing(player))
        val sequence = gate.drainCompleted().single()
        assertEquals(usedAt + 900_000_000L, sequence.payload.sourceStartedAtNanos)
        assertEquals(listOf("incoming", "delayed-used", "actual-after-delivery"), sequence.evidenceReferences)
    }

    @Test fun `charge OCR arriving after original source deadline cannot start a new window`() {
        gate.observe(article("incoming", AttackIncoming))
        clock += 8_000_000_000L
        val usedAt = clock
        clock += TerminalVisualCaptureGate.WINDOW_NANOS
        gate.observe(article("expired-used", ChargeMoveUsedAnnounced, usedAt))
        assertFalse(gate.isCapturing(player))
    }

    @Test fun `old restart and old match start cannot clear or reopen a newer lifecycle`() {
        val old = clock
        clock += 100_000_000L
        gate.observe(article("restart", ObservationRestartedByUser))
        assertTrue(request())
        preserve(id = "current-frame")
        gate.observe(article("old-restart", ObservationRestartedByUser, old))
        clock += 100_000_000L
        gate.observe(article("end", MatchEnded(BattleResult.LOSS)))
        assertEquals(listOf("trigger", "current-frame"), gate.drainCompleted().single().evidenceReferences)
        gate.observe(article("old-start", MatchStarted, old))
        assertFalse(request(id = "after-old-start"))
        clock += 100_000_000L
        gate.observe(article("new-match", MatchStarted))
        assertTrue(request(id = "new-match-event"))
    }

    @Test fun `repeated preparation OCR after impact cannot establish a new charge window`() {
        gate.observe(article("ready", GetReadyWitnessed))
        clock += 8_000_000_000L
        gate.observe(article("used", ChargeMoveUsedAnnounced))
        preserve(opponent, "actual")
        clock += 1_000_000_000L
        gate.observe(article("repeated-ready", GetReadyWitnessed))
        clock += 3_000_000_000L
        gate.observe(article("repeated-used", ChargeMoveUsedAnnounced))
        assertFalse(gate.isCapturing(opponent))
        assertEquals(listOf("ready", "used", "actual"), gate.drainCompleted().single().evidenceReferences)
    }

    @Test fun `one measured return closes impact sequence and captures a bounded three second tail`() {
        gate.observe(article("ready", GetReadyWitnessed))
        clock += 8_000_000_000L
        val impactAt = clock
        gate.observe(article("used", ChargeMoveUsedAnnounced))
        preserve(opponent, "centered-frame")
        clock += 2_500_000_000L
        val returnAt = clock
        assertTrue(gate.requestChargedReturn(opponent, returnAt))
        val impact = gate.drainCompleted().single()
        assertEquals("CHARGED_IMPACT", impact.payload.trigger)
        assertEquals(impactAt, impact.payload.sourceEndedAtNanos)
        preserve(opponent, "return-frame")
        clock += 100_000_000L
        assertFalse(gate.requestChargedReturn(opponent, clock))
        gate.observe(article("duplicate-used", ChargeMoveUsedAnnounced))
        assertTrue(gate.isCapturing(opponent))
        clock = returnAt + TerminalVisualCaptureGate.WINDOW_NANOS
        assertFalse(gate.isCapturing(opponent))
        val tail = gate.drainCompleted().single()
        assertEquals("CHARGED_RETURN", tail.payload.trigger)
        assertEquals(returnAt, tail.payload.sourceStartedAtNanos)
        assertEquals(listOf("ready", "used", "return-frame"), tail.evidenceReferences)
        assertTrue(clock <= impactAt + 6_000_000_000L)
        assertFalse(gate.requestChargedReturn(opponent, clock))
    }

    @Test fun `wrong side or late return cannot bypass cooldown or create a charge tail`() {
        assertFalse(gate.request(player, TerminalVisualTrigger.CHARGED_RETURN, clock, listOf("unowned")))
        assertFalse(gate.request(player, TerminalVisualTrigger.CHARGED_IMPACT, clock, listOf("unowned")))
        gate.observe(article("incoming", AttackIncoming))
        clock += 8_000_000_000L
        gate.observe(article("used", ChargeMoveUsedAnnounced))
        assertFalse(gate.requestChargedReturn(opponent, clock))
        clock += 3_000_000_001L
        assertFalse(gate.requestChargedReturn(player, clock))
        assertFalse(gate.isCapturing(player))
    }

    @Test fun `missing used OCR cannot block a later same-side preparation after phase expiry`() {
        gate.observe(article("missed-phase", GetReadyWitnessed))
        clock += 21_000_000_000L
        gate.observe(article("new-ready", GetReadyWitnessed))
        clock += 8_000_000_000L
        gate.observe(article("new-used", ChargeMoveUsedAnnounced))
        assertTrue(gate.isCapturing(opponent))
        preserve(opponent, "new-frame")
        gate.endSession()
        assertEquals(listOf("new-ready", "new-used", "new-frame"), gate.drainCompleted().single().evidenceReferences)
    }

    @Test fun `late identity source boundary retires low HP without arming historical attention`() {
        gate.observe(article("first", ActivePokemonSpeciesWitnessed(player, "Gourgeist")))
        gate.observe(hp("old-low", .1f))
        val identityAt = clock + 100_000_000L
        clock += 1_000_000_000L
        gate.observe(article("late-new", ActivePokemonSpeciesWitnessed(player, "Camerupt"), identityAt))
        assertFalse(gate.isCapturing(player))
        assertFalse(gate.request(player, TerminalVisualTrigger.INDEPENDENT_IMPACT_WITH_LOW_HP, clock, listOf("impact")))
        gate.observe(hp("pre-boundary-hp", .01f, at = identityAt - 1))
        assertFalse(gate.isCapturing(player))
    }
}
