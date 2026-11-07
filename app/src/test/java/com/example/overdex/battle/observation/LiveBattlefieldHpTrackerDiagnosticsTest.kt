package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.data.BattleCalibration
import org.junit.Assert.*
import org.junit.Test

class LiveBattlefieldHpTrackerDiagnosticsTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val regions = mapOf(
        player to BattleCropBounds(35, 367, 241, 1080),
        opponent to BattleCropBounds(312, 381, 576, 863),
    )

    @Test fun `shared source timestamp returns the same measurement and outcome without rescanning`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        val bars = listOf(bar(40), bar(400))
        val first = tracker.read(0, scan = { bars })
        val second = tracker.read(0, scan = { error("A second witness must reuse the pass") })

        assertSame(first.measurements, second.measurements)
        assertSame(first.outcomes, second.outcomes)
        assertTrue(requireNotNull(first.timings).workPerformed)
        assertFalse(requireNotNull(second.timings).workPerformed)
        assertTrue(requireNotNull(second.timings).stages.isEmpty())
        assertEquals(mapOf(player to bars[0], opponent to bars[1]), first.measurements)
        assertEquals(setOf(HpBarLocatorOutcome.FOUND), first.outcomes.values.toSet())
    }

    @Test fun `older source records out of order without replacing the latest shared result`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        val latest = tracker.read(500_000_000, scan = { listOf(bar(40), bar(400)) })
        val old = tracker.read(499_999_999, scan = { error("Old frames cannot rewind the tracker") })

        assertTrue(old.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.OUT_OF_ORDER_FRAME), old.outcomes.values.toSet())
        val cached = tracker.read(500_000_000, scan = { error("Latest result must remain cached") })
        assertSame(latest.measurements, cached.measurements)
        assertSame(latest.outcomes, cached.outcomes)
        assertFalse(requireNotNull(cached.timings).workPerformed)
    }

    @Test fun `initial missing calibration does not scan or claim missing bars`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        val result = tracker.read(0, regionMap = mapOf(player to regions.getValue(player)),
            scan = { error("Invalid calibration must not scan") })

        assertTrue(result.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.INVALID_CALIBRATION), result.outcomes.values.toSet())
    }

    @Test fun `no candidates and a deferred recovery search remain different facts`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        val scanned = mutableListOf<BattleCropBounds>()
        val scan: (BattleCropBounds) -> List<ActiveHpBarMeasurement> = { scanned += it; emptyList() }

        assertEquals(setOf(HpBarLocatorOutcome.NO_CANDIDATE), tracker.read(0, scan = scan).outcomes.values.toSet())
        val narrow = BattlefieldHpTracker().searchWindows(672, regions)
        val wide = BattleCropBounds(0, 367, 672, 1080)
        assertEquals(narrow + wide, scanned)
        val deferred = tracker.read(249_999_999, scan = scan)
        assertEquals("Inspected narrow windows contain no candidate; the wide search is deferred",
            setOf(HpBarLocatorOutcome.NO_CANDIDATE), deferred.outcomes.values.toSet())
        assertEquals(narrow + wide + narrow, scanned)
        assertEquals(0, requireNotNull(deferred.timings).stages[HpBarLocatorStage.RECOVERY_SEARCH]?.calls ?: 0)
        assertEquals(setOf(HpBarLocatorOutcome.NO_CANDIDATE), tracker.read(250_000_000, scan = scan).outcomes.values.toSet())
        assertEquals(narrow + wide + narrow + narrow + wide, scanned)
    }

    @Test fun `tracked sides still scan in their existing local windows before wide recovery`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        val bars = listOf(bar(40), bar(400))
        tracker.read(0, scan = { bars })
        val windows = BattlefieldHpTracker().also { it.assign(672, 0, regions, bars) }
            .trackingWindows(672, regions)
        val scanned = mutableListOf<BattleCropBounds>()
        val result = tracker.read(33_000_000, scan = { bounds ->
            scanned += bounds
            bars.filter { it.left >= bounds.left && it.right <= bounds.right }
        })

        assertEquals(windows, scanned)
        assertEquals(mapOf(player to bars[0], opponent to bars[1]), result.measurements)
        assertEquals(setOf(HpBarLocatorOutcome.FOUND), result.outcomes.values.toSet())
    }

    @Test fun `observable filtering preserves raw candidates for the obscured diagnostic`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        val result = tracker.read(0, scan = { listOf(bar(40), bar(400)) }, canObserve = { false })

        assertTrue(result.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.OBSCURED_CANDIDATES), result.outcomes.values.toSet())
    }

    @Test fun `a lone centered bar remains unassigned instead of acquiring an invented owner`() {
        val centered = bar(240)
        val result = LiveBattlefieldHpTracker(BattleCalibration()).read(0, scan = { listOf(centered) })

        assertTrue(result.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.UNASSIGNED_CANDIDATES), result.outcomes.values.toSet())
    }

    @Test fun `observable unowned geometry prevents claiming that obstruction explains a side`() {
        val hidden = bar(40)
        val unowned = bar(240)
        val result = LiveBattlefieldHpTracker(BattleCalibration()).read(0,
            scan = { listOf(hidden, unowned) }, canObserve = { it != hidden })

        assertTrue(result.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.UNASSIGNED_CANDIDATES), result.outcomes.values.toSet())
    }

    @Test fun `different vertical bands allow one found and one obscured result without guessing ownership`() {
        val separateBands = mapOf(
            player to BattleCropBounds(35, 367, 241, 550),
            opponent to BattleCropBounds(312, 570, 576, 863),
        )
        val own = bar(40).copy(top = 400, bottom = 417)
        val enemy = bar(400)
        val result = LiveBattlefieldHpTracker(BattleCalibration()).read(0, regionMap = separateBands,
            scan = { listOf(own, enemy) }, canObserve = { it != enemy })

        assertEquals(mapOf(player to own), result.measurements)
        assertEquals(HpBarLocatorOutcome.FOUND, result.outcomes[player])
        assertEquals(HpBarLocatorOutcome.OBSCURED_CANDIDATES, result.outcomes[opponent])
    }

    @Test fun `wide recovery diagnostics describe the candidates used for the final assignment`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.read(0, scan = { listOf(bar(40), bar(400)) })
        val scanned = mutableListOf<BattleCropBounds>()
        val result = tracker.read(250_000_000, scan = { bounds ->
            scanned += bounds
            if (bounds.left == 0 && bounds.right == 672) emptyList() else listOf(bar(240))
        })

        assertEquals(3, scanned.size)
        assertEquals(BattleCropBounds(0, 367, 672, 1080), scanned.last())
        assertTrue(result.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.NO_CANDIDATE), result.outcomes.values.toSet())
    }

    @Test fun `resetting a combatant invalidates cached diagnostics and preserves recovery timing behavior`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.read(0, scan = { listOf(bar(40), bar(400)) })
        tracker.reset(player)
        val scanned = mutableListOf<BattleCropBounds>()
        val result = tracker.read(0, scan = { bounds -> scanned += bounds; emptyList() })

        assertEquals("Remaining local lock and missing-side acquisition then immediate wide recovery after reset", 3, scanned.size)
        assertEquals(BattleCropBounds(0, 367, 672, 1080), scanned.last())
        assertTrue(result.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.NO_CANDIDATE), result.outcomes.values.toSet())
    }

    @Test fun `frame size change clears locks and scans the new full calibrated band`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.read(0, scan = { listOf(bar(40), bar(400)) })
        val scanned = mutableListOf<BattleCropBounds>()
        val result = tracker.read(33_000_000, width = 700,
            scan = { bounds -> scanned += bounds; emptyList() })

        assertEquals(BattlefieldHpTracker().searchWindows(700, regions) + BattleCropBounds(0, 367, 700, 1080), scanned)
        assertEquals(setOf(HpBarLocatorOutcome.NO_CANDIDATE), result.outcomes.values.toSet())
    }

    @Test fun `timings separate actual midpoint acquisition recovery and subsequent tracked searches`() {
        var elapsed = 0L
        var cpu = 100L
        val tracker = LiveBattlefieldHpTracker(BattleCalibration(), { elapsed }, { cpu })
        val bars = listOf(bar(40), bar(400))
        val acquired = tracker.read(0, scan = { elapsed += 100; cpu += 40; bars })
        val first = requireNotNull(acquired.timings)
        // The player's Y=600 bar is outside its initial central-third band;
        // the opponent is acquired narrowly, then wide recovery finds both.
        assertEquals(HpBarStageTiming(200, 80, 1), first.stages[HpBarLocatorStage.LOCAL_SEARCH])
        assertEquals(HpBarStageTiming(100, 40, 1), first.stages[HpBarLocatorStage.RECOVERY_SEARCH])
        assertEquals(2, first.stages.getValue(HpBarLocatorStage.ASSIGNMENT).calls)
        assertEquals(120L, first.threadCpuNanos)

        val tracked = tracker.read(33_000_000, scan = { bounds ->
            elapsed += 30; cpu += 10
            bars.filter { it.left >= bounds.left && it.right <= bounds.right }
        })
        val second = requireNotNull(tracked.timings)
        assertEquals(HpBarStageTiming(60, 20, 1), second.stages[HpBarLocatorStage.LOCAL_SEARCH])
        assertFalse(second.stages.containsKey(HpBarLocatorStage.RECOVERY_SEARCH))
        assertEquals(1, second.stages.getValue(HpBarLocatorStage.ASSIGNMENT).calls)
        assertEquals(20L, second.threadCpuNanos)
    }

    @Test fun `cache hit includes only current call lock wait and cpu rather than cached work`() {
        var elapsed = 0L
        var cpu = 100L
        val tracker = LiveBattlefieldHpTracker(BattleCalibration(), { elapsed }, { cpu })
        tracker.read(0, scan = { elapsed += 100; cpu += 40; listOf(bar(40), bar(400)) })
        val hit = requireNotNull(tracker.read(0, scan = { error("No cached scan") }).timings)

        assertFalse(hit.workPerformed)
        assertTrue(hit.stages.isEmpty())
        assertEquals(0L, hit.lockWaitNanos)
        assertEquals(0L, hit.threadCpuNanos)
    }

    @Test fun `unavailable thread clock leaves cpu unknown while preserving elapsed work`() {
        var elapsed = 0L
        val tracker = LiveBattlefieldHpTracker(BattleCalibration(), { elapsed }, { null })
        val timing = requireNotNull(tracker.read(0, scan = { elapsed += 123; emptyList() }).timings)

        assertNull(timing.threadCpuNanos)
        assertEquals(HpBarStageTiming(246, null, 1), timing.stages[HpBarLocatorStage.LOCAL_SEARCH])
        assertEquals(HpBarStageTiming(123, null, 1), timing.stages[HpBarLocatorStage.RECOVERY_SEARCH])
    }

    private fun LiveBattlefieldHpTracker.read(
        at: Long,
        width: Int = 672,
        regionMap: Map<ActivePokemonSide, BattleCropBounds> = regions,
        scan: (BattleCropBounds) -> List<ActiveHpBarMeasurement>,
        canObserve: (ActiveHpBarMeasurement) -> Boolean = { true },
    ) = measureFrameWithDiagnostics(width, 1493, at, { regionMap }, { bounds ->
        // A real crop cannot return a complete bar clipped by the requested window.
        scan(bounds).filter { bar ->
            bar.left >= bounds.left && bar.right <= bounds.right &&
                bar.top >= bounds.top && bar.bottom <= bounds.bottom
        }
    }, canObserve)

    private fun bar(left: Int) = ActiveHpBarMeasurement(left, 600, left + 192, 617, .8f, .95f)
}
