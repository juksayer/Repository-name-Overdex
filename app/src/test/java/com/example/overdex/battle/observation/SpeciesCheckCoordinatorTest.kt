package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import org.junit.Assert.*
import org.junit.Test

class SpeciesCheckCoordinatorTest {
    private var time = 10_000_000_000L
    private val reports = mutableListOf<SpeciesCheckMeasured>()
    private val references = mutableListOf<List<String>>()
    private val checks = SpeciesCheckCoordinator({ time }) { report, refs -> reports += report; references += refs }
    private val side = ActivePokemonSide.OPPONENT

    @Test fun `confirmation requires distinct agreeing frames and delivery includes queue time`() {
        checks.request(side, "ENTRY", "cue", time - 100_000_000)
        val window = checks.windowFor(side, time)!!
        assertNull(checks.read(side, window.id, "Sneasel", "frame1"))
        assertNull(checks.read(side, window.id, "Sneasel", "frame1"))
        time += 150_000_000
        assertEquals(listOf("cue", "frame1", "frame2"), checks.read(side, window.id, "Sneasel", "frame2"))
        assertFalse(checks.captureEnabled(side))
        time += 50_000_000
        checks.delivered(side, "Sneasel", "identity")
        assertEquals("IDENTITY_DELIVERED", reports.last().status)
        assertEquals(300_000_000L, reports.last().elapsedNanos)
    }

    @Test fun `priority badge read confirms immediately and closes its capture window`() {
        checks.request(side, "ENTRY", "cue")
        val window = checks.windowFor(side, time)!!

        assertEquals(
            listOf("cue", "frame1"),
            checks.acceptImmediate(side, window.id, "Sneasel", "frame1")
        )
        assertFalse(checks.captureEnabled(side))
        assertEquals("CONFIRMED", reports.last().status)
        assertEquals("Sneasel", reports.last().speciesName)
    }

    @Test fun `priority badge read rejects a crop from a superseded window`() {
        checks.request(side, "RECOVERY", null)
        val stale = checks.windowFor(side, time)!!
        checks.request(side, "ENTRY", "entry")

        assertNull(checks.acceptImmediate(side, stale.id, "Sneasel", "stale-crop"))
        assertTrue(checks.isChecking(side))
    }

    @Test fun `priority OCR may finish late when its crop was captured inside the window`() {
        checks.request(side, "ENTRY", "sneasel-entry")
        val window = checks.windowFor(side, time)!!
        val capturedAt = time + 200_000_000L
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS + 1L
        checks.tick()

        assertEquals(
            listOf("sneasel-entry", "sneasel-crop"),
            checks.acceptImmediate(
                side,
                window.id,
                "Sneasel",
                "sneasel-crop",
                capturedAt
            )
        )
        assertEquals("CONFIRMED", reports.last().status)
    }

    @Test fun `late OCR from an older window cannot replace a newer accepted species`() {
        checks.request(side, "ENTRY", "sneasel-entry")
        val old = checks.windowFor(side, time)!!
        val oldCapturedAt = time + 100_000_000L
        time += 200_000_000L
        checks.request(side, "ENTRY", "sealeo-entry")
        val current = checks.windowFor(side, time)!!
        val currentCapturedAt = time + 100_000_000L

        assertNotNull(checks.acceptImmediate(side, current.id, "Sealeo", "sealeo-crop", currentCapturedAt))
        assertNull(checks.acceptImmediate(side, old.id, "Sneasel", "sneasel-crop", oldCapturedAt))
    }

    @Test fun `missed target keeps a bounded grace period`() {
        checks.request(side, "COUNTDOWN", "three")
        time += 1_000_000_000
        checks.request(side, "COUNTDOWN", "two")
        time += 500_000_000
        checks.tick()
        assertEquals(listOf("OPENED", "TIMED_OUT"), reports.map { it.status })
        assertTrue(checks.captureEnabled(side))
        time += SpeciesCheckCoordinator.TARGET_NANOS
        checks.tick()
        assertEquals(1, reports.count { it.status == "TIMED_OUT" })
        assertEquals("EXPIRED", reports.last().status)
        assertFalse(checks.isChecking(side))
    }

    @Test fun `entry supersedes recovery and rejects older in-flight OCR`() {
        checks.captureEnabled(side)
        val old = checks.windowFor(side, time)!!
        time += 100_000_000
        checks.request(side, "ENTRY", "new-entry")
        assertNull(checks.windowFor(side, old.openedAt))
        assertNull(checks.read(side, old.id, "Sneasel", "old-frame"))
        assertEquals(listOf("OPENED", "SUPERSEDED", "OPENED"), reports.map { it.status })
    }

    @Test fun `switch faint and cry cues supersede an unrelated recovery check`() {
        listOf("SWITCH", "FAINT", "CRY").forEachIndexed { index, reason ->
            if (!checks.isChecking(side)) checks.captureEnabled(side)
            time += 100_000_000L
            checks.request(side, reason, "cue-$index")
            assertEquals(reason, reports.last().reason)
            assertEquals("OPENED", reports.last().status)
        }
    }

    @Test fun `contradictory reading restarts agreement and is reported`() {
        checks.request(side, "ENTRY", "entry")
        val id = checks.windowFor(side, time)!!.id
        checks.read(side, id, "Sneasel", "one")
        assertNull(checks.read(side, id, "Sealeo", "two"))
        assertEquals("DISAGREEMENT", reports.last().status)
        assertEquals(listOf("one", "two"), references.last())
        assertNotNull(checks.read(side, id, "Sealeo", "three"))
    }

    @Test fun `timeout does not manufacture an identity from one reading`() {
        checks.request(side, "CRY", "cry")
        val id = checks.windowFor(side, time)!!.id
        checks.read(side, id, "Sneasel", "one")
        time += SpeciesCheckCoordinator.TARGET_NANOS
        checks.tick()
        assertEquals("TIMED_OUT", reports.last().status)
        assertNotNull(checks.read(side, id, "Sneasel", "two"))
        assertEquals("CONFIRMED", reports.last().status)
    }

    @Test fun `expired recovery rests before opening another OCR window`() {
        checks.captureEnabled(side)
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS
        assertFalse(checks.captureEnabled(side))
        assertEquals("EXPIRED", reports.last().status)
        time += SpeciesCheckCoordinator.RECOVERY_INTERVAL_NANOS
        assertTrue(checks.captureEnabled(side))
        assertEquals("OPENED", reports.last().status)
    }

    @Test fun `completed sides rest while the unresolved side keeps checking`() {
        checks.captureEnabled(side)
        checks.captureEnabled(ActivePokemonSide.PLAYER)
        val id = checks.windowFor(side, time)!!.id
        checks.read(side, id, "Sneasel", "one")
        checks.read(side, id, "Sneasel", "two")
        assertFalse(checks.captureEnabled(side))
        assertTrue(checks.captureEnabled(ActivePokemonSide.PLAYER))
        time += SpeciesCheckCoordinator.RECOVERY_INTERVAL_NANOS
        assertTrue(checks.captureEnabled(side))
    }

    @Test fun `stopping prevents recovery and later triggers`() {
        checks.captureEnabled(side)
        checks.stop()
        time += 100_000_000_000
        checks.request(side, "ENTRY", "late")
        assertFalse(checks.captureEnabled(side))
        assertEquals("STOPPED", reports.last().status)
    }

    @Test fun `restarting closes incomplete work but allows a fresh window`() {
        checks.request(side, "ENTRY", "old-entry")
        val stale = checks.windowFor(side, time)!!
        checks.read(side, stale.id, "Sneasel", "old-frame")

        checks.restartObservationAttempt()

        assertEquals("RESTARTED", reports.last().status)
        assertNull(checks.read(side, stale.id, "Sneasel", "late-old-frame"))
        checks.request(side, "ENTRY", "new-entry")
        assertEquals("new-entry", checks.windowFor(side, time)?.triggerId)
        assertFalse(checks.isStopped())
    }

    @Test fun `a different entry supersedes an earlier entry still being read`() {
        checks.request(side, "ENTRY", "first")
        val first = checks.windowFor(side, time)!!
        time += 100_000_000
        checks.request(side, "ENTRY", "second")
        assertNull(checks.read(side, first.id, "Sneasel", "stale"))
        assertEquals("second", checks.windowFor(side, time)!!.triggerId)
    }

}
