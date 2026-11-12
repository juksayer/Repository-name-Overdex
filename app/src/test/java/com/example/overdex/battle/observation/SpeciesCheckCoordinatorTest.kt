package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import org.junit.Assert.*
import org.junit.Test

class SpeciesCheckCoordinatorTest {
    @Test fun `prebattle wake requires a bounded battle cue not a recovery timer`() {
        assertTrue(checks.captureEnabled(side))
        assertFalse(checks.hasBattleCue(side))
        for (reason in listOf("ENTRY", "CRY", "VS", "COUNTDOWN", "MATCH_START", "SWITCH", "FAINT")) {
            checks.restartObservationAttempt()
            checks.request(side, reason, "cue-$reason")
            assertTrue(reason, checks.hasBattleCue(side))
            assertFalse(checks.hasBattleCue(ActivePokemonSide.PLAYER))
            time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS
            assertFalse("Expired $reason must not keep menu OCR running", checks.hasBattleCue(side))
        }
    }

    @Test fun `ignore rescan and stop retire prebattle wake cues`() {
        checks.request(side, "CRY", "cry")
        checks.restartObservationAttempt()
        assertFalse(checks.hasBattleCue(side))
        checks.request(side, "ENTRY", "entry")
        checks.stop()
        assertFalse(checks.hasBattleCue(side))
    }

    @Test fun `confirmed window leaves queued pixels unread but a new entry is eligible`() {
        checks.request(side, "ENTRY", "sneasel")
        val first = checks.windowFor(side, time)!!
        assertTrue(checks.needsReading(side, first.id, time))
        checks.acceptImmediate(side, first.id, "Sneasel", "read", time)
        assertFalse(checks.needsReading(side, first.id, time + 10))
        time += 1_000_000_000
        checks.request(side, "ENTRY", "sealeo")
        val next = checks.windowFor(side, time)!!
        assertTrue(checks.needsReading(side, next.id, time))
        assertFalse(checks.needsReading(side, first.id, time))
    }

    @Test fun `unconfirmed expired window may still yield its preserved evidence`() {
        checks.request(side, "ENTRY", "entry")
        val first = checks.windowFor(side, time)!!
        val capturedAt = time
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS + 1
        checks.tick()
        assertTrue(checks.needsReading(side, first.id, capturedAt))
        checks.stop()
        assertFalse(checks.needsReading(side, first.id, capturedAt))
    }

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
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS + 1L
        checks.tick()
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
        listOf("SWITCH", "FAINT", "CRY").forEach { reason ->
            val localReports = mutableListOf<SpeciesCheckMeasured>()
            val localChecks = SpeciesCheckCoordinator({ time }) { report, _ -> localReports += report }
            localChecks.captureEnabled(side)
            time += 100_000_000L
            localChecks.request(side, reason, "cue-$reason")
            assertEquals(reason, localReports.last().reason)
            assertEquals("OPENED", localReports.last().status)
            assertEquals(listOf("OPENED", "SUPERSEDED", "OPENED"), localReports.map { it.status })
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

    @Test fun `completed sides stop periodic recovery while the unresolved side keeps checking`() {
        checks.captureEnabled(side)
        checks.captureEnabled(ActivePokemonSide.PLAYER)
        val id = checks.windowFor(side, time)!!.id
        checks.read(side, id, "Sneasel", "one")
        checks.read(side, id, "Sneasel", "two")
        assertFalse(checks.captureEnabled(side))
        assertTrue(checks.captureEnabled(ActivePokemonSide.PLAYER))
        time += SpeciesCheckCoordinator.RECOVERY_INTERVAL_NANOS
        assertFalse(checks.captureEnabled(side))
        time += 120_000_000_000L
        assertFalse(checks.captureEnabled(side))
        checks.request(side, "RECOVERY", null)
        assertFalse(checks.captureEnabled(side))
    }

    @Test fun `a charge rechecks known identity once and a missed read does not start periodic recovery`() {
        checks.captureEnabled(side)
        val first = checks.windowFor(side, time)!!
        checks.acceptImmediate(side, first.id, "Sneasel", "first")
        time += 10_000_000_000L
        assertFalse(checks.captureEnabled(side))
        checks.request(side, "CHARGE", "used-move")
        assertTrue(checks.captureEnabled(side))
        assertEquals("CHARGE", checks.windowFor(side, time)?.reason)
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS
        assertFalse(checks.captureEnabled(side))
        time += SpeciesCheckCoordinator.RECOVERY_INTERVAL_NANOS
        assertFalse(checks.captureEnabled(side))
    }

    @Test fun `a replacement entry restores unknown recovery until that appearance is resolved`() {
        checks.captureEnabled(side)
        val first = checks.windowFor(side, time)!!
        checks.acceptImmediate(side, first.id, "Sneasel", "first")
        time += 1_000_000_000L
        checks.request(side, "ENTRY", "new-entry")
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS
        assertFalse(checks.captureEnabled(side))
        time += SpeciesCheckCoordinator.RECOVERY_INTERVAL_NANOS
        assertTrue(checks.captureEnabled(side))
        val recovery = checks.windowFor(side, time)!!
        checks.acceptImmediate(side, recovery.id, "Sealeo", "replacement")
        time += 60_000_000_000L
        assertFalse(checks.captureEnabled(side))
    }

    @Test fun `charge and QTE cues supersede an initial recovery window`() {
        for (reason in listOf("CHARGE", "QTE")) {
            checks.restartObservationAttempt()
            checks.captureEnabled(side)
            time += 100_000_000L
            checks.request(side, reason, "cue-$reason")
            assertEquals(reason, checks.windowFor(side, time)?.reason)
            assertTrue(checks.hasBattleCue(side))
        }
    }

    @Test fun `late earlier evidence cannot retire recovery for a newer unresolved appearance`() {
        checks.request(side, "ENTRY", "first-entry")
        val first = checks.windowFor(side, time)!!
        val capturedAt = time + 100_000_000L
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS
        checks.tick()
        checks.request(side, "ENTRY", "replacement-entry")
        assertNotNull(checks.acceptImmediate(side, first.id, "Sneasel", "old-crop", capturedAt))
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS
        assertFalse(checks.captureEnabled(side))
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

    @Test fun `supporting lifecycle cues preserve an earlier entry crop still being read`() {
        checks.request(side, "ENTRY", "first")
        val first = checks.windowFor(side, time)!!
        val capturedAt = time + 50_000_000L
        time += 100_000_000
        checks.request(side, "ENTRY", "second")
        checks.request(side, "CRY", "supporting-cry")

        assertEquals(first.id, checks.windowFor(side, time)!!.id)
        assertEquals(
            listOf("first", "entry-crop"),
            checks.acceptImmediate(side, first.id, "Vaporeon", "entry-crop", capturedAt)
        )
        assertEquals("Vaporeon", reports.last().speciesName)
    }

    @Test fun `accepted native badge covers an older delayed entry without reopening capture`() {
        checks.request(side, "CHARGE", "charge", time)
        val window = checks.windowFor(side, time)!!
        val capturedAt = time + 100_000_000L
        time += 200_000_000L
        assertNotNull(checks.acceptImmediate(side, window.id, "Stunfisk", "native-badge", capturedAt))
        time += 5_000_000_000L
        val entryAt = capturedAt - 1L

        checks.request(side, "ENTRY", "delayed-entry", entryAt)

        assertEquals(listOf("OPENED", "CONFIRMED", "COVERED"), reports.map { it.status })
        val covered = reports.last()
        assertEquals("ENTRY", covered.reason)
        assertEquals(entryAt, covered.triggerMonotonicNanos)
        assertEquals("Stunfisk", covered.speciesName)
        assertEquals(listOf("delayed-entry", "native-badge"), references.last())
        assertFalse(checks.isChecking(side))
        assertFalse(checks.captureEnabled(side))
        assertFalse(checks.needsReading(side, window.id, capturedAt))
    }

    @Test fun `entry at or after badge capture still opens a new check`() {
        for (offset in listOf(0L, 1L)) {
            checks.restartObservationAttempt()
            checks.request(side, "ENTRY", "first")
            val first = checks.windowFor(side, time)!!
            val capturedAt = time + 100_000_000L
            time += 200_000_000L
            checks.acceptImmediate(side, first.id, "Stunfisk", "badge", capturedAt)

            checks.request(side, "ENTRY", "new-entry", capturedAt + offset)

            assertEquals("OPENED", reports.last().status)
            assertEquals("new-entry", checks.windowFor(side, time)?.triggerId)
            assertTrue(checks.captureEnabled(side))
            assertTrue(checks.windowFor(side, time)!!.id > first.id)
        }
    }

    @Test fun `late recognition does not make an older capture cover a newer entry`() {
        checks.request(side, "ENTRY", "first")
        val first = checks.windowFor(side, time)!!
        val capturedAt = time + 100_000_000L
        val replacementAt = time + 200_000_000L
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS + 1L
        checks.tick()
        assertNotNull(checks.acceptImmediate(side, first.id, "Stunfisk", "old-badge", capturedAt))

        checks.request(side, "ENTRY", "replacement", replacementAt)

        assertEquals("OPENED", reports.last().status)
        assertEquals("replacement", checks.windowFor(side, time)?.triggerId)
        assertTrue(checks.captureEnabled(side))
    }

    @Test fun `badge coverage belongs only to its captured side`() {
        checks.request(side, "ENTRY", "opponent")
        val window = checks.windowFor(side, time)!!
        val capturedAt = time + 100_000_000L
        time += 200_000_000L
        checks.acceptImmediate(side, window.id, "Stunfisk", "opponent-badge", capturedAt)

        checks.request(ActivePokemonSide.PLAYER, "ENTRY", "player-entry", capturedAt - 1L)

        assertEquals("OPENED", reports.last().status)
        assertTrue(checks.isChecking(ActivePokemonSide.PLAYER))
        assertFalse(checks.isChecking(side))
    }

    @Test fun `uncertain legacy implicit or invalid source clocks cannot cover a delayed entry`() {
        for (mode in listOf("uncertain", "legacy", "implicit", "future", "blank crop", "blank species")) {
            checks.restartObservationAttempt()
            checks.request(side, "ENTRY", "first-$mode")
            val window = checks.windowFor(side, time)!!
            val entryAt = time + 50_000_000L
            time += 200_000_000L
            when (mode) {
                "uncertain" -> checks.read(side, window.id, "Stunfisk", "one-frame")
                "legacy" -> {
                    checks.read(side, window.id, "Stunfisk", "one-frame")
                    checks.read(side, window.id, "Stunfisk", "second-frame")
                }
                "implicit" -> checks.acceptImmediate(side, window.id, "Stunfisk", "no-source-clock")
                "future" -> checks.acceptImmediate(side, window.id, "Stunfisk", "future", time + 1L)
                "blank crop" -> checks.acceptImmediate(side, window.id, "Stunfisk", "", time)
                else -> checks.acceptImmediate(side, window.id, "", "blank-species", time)
            }
            time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS + 1L
            checks.tick()

            checks.request(side, "ENTRY", "delayed-$mode", entryAt)

            assertEquals(mode, "OPENED", reports.last().status)
            assertEquals("delayed-$mode", checks.windowFor(side, time)?.triggerId)
        }
    }

    @Test fun `native badge coverage leaves all other battle cue assignments enabled`() {
        for (reason in listOf("FAINT", "SWITCH", "CRY", "CHARGE", "QTE", "MATCH_START", "VS", "COUNTDOWN")) {
            checks.restartObservationAttempt()
            checks.request(side, "ENTRY", "first")
            val window = checks.windowFor(side, time)!!
            val capturedAt = time + 100_000_000L
            time += 200_000_000L
            checks.acceptImmediate(side, window.id, "Stunfisk", "native-badge", capturedAt)

            checks.request(side, reason, "cue-$reason", capturedAt - 1L)

            assertEquals(reason, "OPENED", reports.last().status)
            assertEquals(reason, checks.windowFor(side, time)?.reason)
            assertTrue(reason, checks.captureEnabled(side))
        }
    }

    @Test fun `covered entry does not change the candidate or ownership of a newer pending check`() {
        checks.request(side, "ENTRY", "first")
        val first = checks.windowFor(side, time)!!
        val capturedAt = time + 100_000_000L
        time += 200_000_000L
        checks.acceptImmediate(side, first.id, "Stunfisk", "old-badge", capturedAt)
        time += 100_000_000L
        checks.request(side, "FAINT", "new-faint", time)
        val newer = checks.windowFor(side, time)!!
        assertNull(checks.read(side, newer.id, "Sealeo", "new-frame-one"))

        checks.request(side, "ENTRY", "old-entry", capturedAt - 1L)

        assertEquals("COVERED", reports.last().status)
        assertEquals(newer, checks.windowFor(side, time))
        assertEquals(listOf("new-faint", "new-frame-one", "new-frame-two"),
            checks.read(side, newer.id, "Sealeo", "new-frame-two"))
    }

    @Test fun `covered entry does not expire or otherwise advance a newer pending assignment`() {
        checks.request(side, "ENTRY", "first")
        val first = checks.windowFor(side, time)!!
        val capturedAt = time + 100_000_000L
        time += 200_000_000L
        checks.acceptImmediate(side, first.id, "Stunfisk", "old-badge", capturedAt)
        checks.request(side, "FAINT", "new-faint", time)
        val countBeforeCoverage = reports.size
        time += SpeciesCheckCoordinator.MAX_WINDOW_NANOS + 1L

        checks.request(side, "ENTRY", "delayed-entry", capturedAt - 1L)

        assertEquals(listOf("COVERED"), reports.drop(countBeforeCoverage).map { it.status })
        assertTrue(checks.isChecking(side))
        checks.tick()
        assertFalse(checks.isChecking(side))
        assertEquals("EXPIRED", reports.last().status)
    }

    @Test fun `restart clears badge coverage and stop rejects preserved late acceptance`() {
        checks.request(side, "ENTRY", "first")
        val first = checks.windowFor(side, time)!!
        val capturedAt = time + 100_000_000L
        time += 200_000_000L
        checks.acceptImmediate(side, first.id, "Stunfisk", "old-badge", capturedAt)
        checks.restartObservationAttempt()
        assertNull(checks.acceptImmediate(side, first.id, "Stunfisk", "old-attempt", capturedAt))

        checks.request(side, "ENTRY", "after-restart", capturedAt - 1L)

        assertEquals("OPENED", reports.last().status)
        val current = checks.windowFor(side, time)!!
        checks.stop()
        val reportsAtStop = reports.size
        assertNull(checks.acceptImmediate(side, current.id, "Sealeo", "late-native", time))
        assertNull(checks.read(side, current.id, "Sealeo", "late-legacy"))
        checks.request(side, "ENTRY", "after-stop", capturedAt - 1L)
        assertEquals(reportsAtStop, reports.size)
        assertFalse(checks.isChecking(side))
    }

    @Test fun `entry without a cited source cannot consume badge coverage`() {
        checks.request(side, "ENTRY", "first")
        val window = checks.windowFor(side, time)!!
        val capturedAt = time + 100_000_000L
        time += 200_000_000L
        checks.acceptImmediate(side, window.id, "Stunfisk", "native-badge", capturedAt)

        checks.request(side, "ENTRY", null, capturedAt - 1L)

        assertEquals("OPENED", reports.last().status)
        assertTrue(checks.isChecking(side))
    }

}
