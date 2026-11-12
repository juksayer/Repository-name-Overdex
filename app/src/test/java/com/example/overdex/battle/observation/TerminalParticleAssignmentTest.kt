package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import org.junit.Assert.*
import org.junit.Test

class TerminalParticleAssignmentTest {
    private val second = 1_000_000_000L
    private val start = 100 * second
    private var clock = start
    private val gate = TerminalVisualCaptureGate { clock }.also { it.beginSession(start) }
    private val opponent = ActivePokemonSide.OPPONENT
    private val player = ActivePokemonSide.PLAYER
    private fun cue(kind: AcceptedChargeCaptureCueKind, at: Long = clock, id: String = "cue-$at") =
        AcceptedChargeCaptureCue(kind, id, at, clock, 1000L)
    private fun article(payload: TestimonyPayload, at: Long = clock, id: String = "article-$at") =
        RealityArticle(ArticleId(id), 1000L, 2000L, SourceId("TEST"), payload, monotonicTimeNanos = at)

    @Test fun `QTE measurement survives exhaustion and drainage of sparse PNG slots`() {
        assertTrue(gate.prepare(cue(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS)))
        repeat(34) { i ->
            clock = start + i * 250_000_000L
            val ticket = requireNotNull(gate.takeFrame(opponent, clock))
            gate.finishFrame(ticket, 1000L, false)
        }
        assertNull(gate.captureWindow(opponent))
        gate.drainCompleted()
        val assignment = requireNotNull(gate.particleAssignment())
        assertEquals(start, assignment.phaseAt)
        assertEquals(start + 8_500_000_000L, assignment.until)
        clock = assignment.until
        assertNull(gate.particleAssignment())
        assertFalse(gate.prepare(cue(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS)))
    }

    @Test fun `recorded Cramorant late Used still has measuring coverage without empty HP`() {
        val gradeAt = start + 135_722_650_469L
        clock = start + 138_141_021_316L
        gate.prepare(cue(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS, gradeAt, "grade"))
        clock = start + 141_500_000_000L
        val first = requireNotNull(gate.particleAssignment())
        assertEquals(opponent, first.side)
        clock = start + 143_080_000_000L
        gate.observe(article(ChargeMoveUsedAnnounced, start + 138_709_973_239L, "used"))
        val later = requireNotNull(gate.particleAssignment())
        assertEquals(first.until, later.until)
        assertEquals(listOf("grade", "used"), later.evidenceReferences)
    }

    @Test fun `both attack directions start at preparation and never extend past source budget`() {
        for (kind in listOf(AcceptedChargeCaptureCueKind.GET_READY, AcceptedChargeCaptureCueKind.ATTACK_INCOMING)) {
            gate.beginSession(start); clock = start + 2 * second
            gate.prepare(cue(kind, start, "prepare"))
            val first = requireNotNull(gate.particleAssignment())
            assertEquals(if (kind == AcceptedChargeCaptureCueKind.GET_READY) opponent else player, first.side)
            assertEquals(start + 20 * second, first.until)
            clock = start + 6 * second
            gate.observe(article(ChargeMoveUsedAnnounced, start + 4 * second, "used"))
            val later = requireNotNull(gate.particleAssignment())
            assertEquals(start + 10 * second, later.until)
            clock = later.until
            assertNull(gate.particleAssignment())
        }
    }

    @Test fun `repeated early grades retain initial deadline and all initial cue references`() {
        gate.prepare(cue(AcceptedChargeCaptureCueKind.GET_READY, id = "ready"))
        clock += 4 * second
        gate.prepare(cue(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS, id = "grade"))
        val first = requireNotNull(gate.particleAssignment())
        assertEquals(listOf("ready", "grade"), first.evidenceReferences)
        clock += second
        assertFalse(gate.prepare(cue(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS, id = "later")))
        assertEquals(first, gate.particleAssignment())
    }

    @Test fun `new opposite phase retires the previous assignment without inheriting its recipient`() {
        gate.prepare(cue(AcceptedChargeCaptureCueKind.GET_READY))
        clock += second
        gate.prepare(cue(AcceptedChargeCaptureCueKind.ATTACK_INCOMING))
        assertEquals(player, gate.particleAssignment()?.side)
        gate.observe(article(GetReadyWitnessed, start))
        assertEquals(player, gate.particleAssignment()?.side)
    }

    @Test fun `either combatant replacement invalidates a prepared charge even before QTE`() {
        for (side in ActivePokemonSide.entries) {
            clock = start
            gate.beginSession(start)
            gate.prepare(cue(AcceptedChargeCaptureCueKind.GET_READY))
            clock += second
            gate.observe(article(ActivePokemonSpeciesWitnessed(side, "New", appearanceStartedAtNanos = clock)))
            assertNull(gate.particleAssignment())
        }
    }

    @Test fun `lifecycle and a real new coordinate space close measurement`() {
        gate.observeDimensions(672,1493,clock)
        gate.prepare(cue(AcceptedChargeCaptureCueKind.PLAYER_QTE_PROGRESS))
        clock += second
        gate.observeDimensions(816,1808,clock)
        assertNull(gate.particleAssignment())
        clock += second
        gate.prepare(cue(AcceptedChargeCaptureCueKind.ATTACK_INCOMING))
        assertNotNull(gate.particleAssignment())
        gate.observe(article(ScreenIgnoredByUser))
        assertNull(gate.particleAssignment())
    }

    @Test fun `low HP and departure do not open continuous particle work outside a charge`() {
        gate.request(opponent, TerminalVisualTrigger.DEPARTURE, clock, listOf("departure"))
        assertNotNull(gate.captureWindow(opponent))
        assertNull(gate.particleAssignment())
    }
}
