package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.data.BattleCalibration
import org.junit.Assert.*
import org.junit.Test

class HpBarDepartureCheckPolicyTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val own = ActiveHpBarMeasurement(40, 600, 232, 617, .4f, .99f)
    private val enemy = own.copy(left = 400, right = 592, top = 400, bottom = 417)
    private var clock = 0L
    private fun policy() = HpBarDepartureCheckPolicy(now = { clock })
    private fun diag(ownBar: ActiveHpBarMeasurement? = own, enemyBar: ActiveHpBarMeasurement? = enemy,
        absent: HpBarLocatorOutcome = HpBarLocatorOutcome.NO_CANDIDATE,
        candidates: List<ActiveHpBarMeasurement>? = null): HpBarFrameDiagnostics {
        val bars = listOfNotNull(ownBar?.let { player to it }, enemyBar?.let { opponent to it }).toMap()
        return HpBarFrameDiagnostics(bars, ActivePokemonSide.entries.associateWith {
            if (it in bars) HpBarLocatorOutcome.FOUND else absent
        }, candidates = candidates)
    }
    private fun frame(policy: HpBarDepartureCheckPolicy, atMs: Long, located: HpBarFrameDiagnostics = diag(),
        observable: (BattleCropBounds) -> Boolean = { true }, width: Int = 672, height: Int = 1493): Set<ActivePokemonSide> {
        clock = atMs * MS
        return policy.observe(width, height, clock, located, observable)
    }

    @Test fun `two missing ordinary observations request affected count without needing a zero frame`() {
        val policy = policy()
        assertTrue(frame(policy, 0).isEmpty())
        assertTrue(frame(policy, 100, diag(ownBar = null)).isEmpty())
        assertTrue(frame(policy, 179, diag(ownBar = null)).isEmpty())
        assertEquals(setOf(player), frame(policy, 180, diag(ownBar = null)))
        assertTrue(frame(policy, 250, diag(ownBar = null)).isEmpty())
    }

    @Test fun `both missing or one unowned centered bar cannot support departure check`() {
        for (observed in listOf(diag(null, null), diag(null, enemy.copy(left = 250, right = 442)))) {
            val policy = policy()
            frame(policy, 0)
            assertTrue(frame(policy, 100, observed).isEmpty())
            assertTrue(frame(policy, 200, observed).isEmpty())
        }
    }

    @Test fun `prior zero has its own resource cue and cannot seed departure hint`() {
        val policy = policy()
        frame(policy, 0, diag(own.copy(filledFraction = 0f)))
        assertTrue(frame(policy, 100, diag(ownBar = null)).isEmpty())
        assertTrue(frame(policy, 200, diag(ownBar = null)).isEmpty())
    }

    @Test fun `overlay or retained badge covering any previous bar portion cannot create a hint`() {
        val policy = policy()
        frame(policy, 0)
        val overlayCoversOwn: (BattleCropBounds) -> Boolean = { bounds -> bounds.left != own.left }
        assertTrue(frame(policy, 100, diag(ownBar = null), overlayCoversOwn).isEmpty())
        assertTrue(frame(policy, 200, diag(ownBar = null), overlayCoversOwn).isEmpty())
        // Once uncovered the absence must establish its own two observations.
        assertTrue(frame(policy, 250, diag(ownBar = null)).isEmpty())
        assertEquals(setOf(player), frame(policy, 350, diag(ownBar = null)))
    }

    @Test fun `obscured invalid or throttled locator outcomes never stand in for disappearance`() {
        for (outcome in listOf(HpBarLocatorOutcome.OBSCURED_CANDIDATES, HpBarLocatorOutcome.INVALID_CALIBRATION,
                HpBarLocatorOutcome.RECOVERY_SCAN_THROTTLED, HpBarLocatorOutcome.OUT_OF_ORDER_FRAME)) {
            val policy = policy()
            frame(policy, 0)
            assertTrue(frame(policy, 100, diag(ownBar = null, absent = outcome)).isEmpty())
            assertTrue(frame(policy, 200, diag(ownBar = null, absent = outcome)).isEmpty())
        }
    }

    @Test fun `unassigned geometry requires existing candidate proof that no own compatible bar remains`() {
        for (candidates in listOf(null, listOf(own, enemy))) {
            val policy = policy()
            frame(policy, 0)
            val unassigned = diag(ownBar = null, absent = HpBarLocatorOutcome.UNASSIGNED_CANDIDATES, candidates = candidates)
            assertTrue(frame(policy, 100, unassigned).isEmpty())
            assertTrue(frame(policy, 200, unassigned).isEmpty())
        }
    }

    @Test fun `default overlapping vertical bands retain independent absence proof without another scan`() {
        val policy = policy()
        val tracker = LiveBattlefieldHpTracker(BattleCalibration(), elapsedTimeNanos = { clock }, threadCpuTimeNanos = { null })
        val regions = mapOf(player to BattleCropBounds(0, 350, 336, 1300),
            opponent to BattleCropBounds(336, 350, 672, 1300))
        var scans = 0
        fun located(atMs: Long, candidates: List<ActiveHpBarMeasurement>): HpBarFrameDiagnostics {
            clock = atMs * MS
            return tracker.measureFrameWithDiagnostics(672, 1493, clock, { regions }, { scans++; candidates }, { true })
        }
        frame(policy, 0, located(0, listOf(own, enemy)))
        val first = located(100, listOf(enemy))
        assertEquals(HpBarLocatorOutcome.UNASSIGNED_CANDIDATES, first.outcomes[player])
        assertEquals(listOf(enemy), first.candidates)
        val afterFirstScan = scans
        assertTrue(frame(policy, 100, first).isEmpty())
        assertEquals(afterFirstScan, scans)
        val second = located(200, listOf(enemy))
        val afterSecondScan = scans
        assertEquals(setOf(player), frame(policy, 200, second))
        assertEquals(afterSecondScan, scans)
    }

    @Test fun `stale source and out of order observations cannot extend a missing episode`() {
        val policy = policy()
        frame(policy, 0)
        frame(policy, 100, diag(ownBar = null))
        clock = 200 * MS
        assertTrue(policy.observe(672, 1493, 100 * MS, diag(ownBar = null)) { true }.isEmpty())
        clock = 800 * MS
        assertTrue(policy.observe(672, 1493, 200 * MS, diag(ownBar = null)) { true }.isEmpty())
        assertTrue(frame(policy, 801, diag(ownBar = null)).isEmpty())
        frame(policy, 900)
        frame(policy, 1000, diag(ownBar = null))
        assertEquals(setOf(player), frame(policy, 1100, diag(ownBar = null)))
    }

    @Test fun `dimension changes drop old source geometry rather than checking a scaled stale position`() {
        val policy = policy()
        frame(policy, 0)
        frame(policy, 100, diag(ownBar = null))
        assertTrue(frame(policy, 200, diag(ownBar = null), width = 1080, height = 2400).isEmpty())
        assertTrue(frame(policy, 300, diag(ownBar = null), width = 1080, height = 2400).isEmpty())
    }

    @Test fun `one request per absence and cooldown survives brief reacquisition flashes`() {
        val policy = policy()
        frame(policy, 0)
        frame(policy, 100, diag(ownBar = null))
        assertEquals(setOf(player), frame(policy, 200, diag(ownBar = null)))
        frame(policy, 300)
        assertTrue(frame(policy, 400, diag(ownBar = null)).isEmpty())
        assertTrue(frame(policy, 500, diag(ownBar = null)).isEmpty())
        frame(policy, 3300)
        frame(policy, 3400, diag(ownBar = null))
        assertEquals(setOf(player), frame(policy, 3500, diag(ownBar = null)))
    }

    @Test fun `charged camera pauses hints and actual ordinary return after used permits fresh reseeding`() {
        val policy = policy()
        frame(policy, 0)
        clock = 50 * MS
        policy.prepare(clock)
        frame(policy, 100)
        assertTrue(frame(policy, 200, diag(ownBar = null)).isEmpty())
        assertTrue(frame(policy, 300, diag(ownBar = null)).isEmpty())
        clock = 500 * MS
        policy.impact(clock)
        assertTrue(frame(policy, 550, diag(ownBar = null)).isEmpty())
        frame(policy, 600)
        frame(policy, 700, diag(ownBar = null))
        assertEquals(setOf(player), frame(policy, 800, diag(ownBar = null)))
    }

    @Test fun `immediate post charge KO without ordinary low bar return intentionally abstains`() {
        val policy = policy()
        frame(policy, 0)
        clock = 50 * MS
        policy.prepare(clock)
        clock = 500 * MS
        policy.impact(clock)
        // The opponent can faint to an immediate Incinerate before its normal
        // low bar returns. Pre-charge geometry is not a fresh departure witness.
        assertTrue(frame(policy, 550, diag(enemyBar = null)).isEmpty())
        assertTrue(frame(policy, 650, diag(enemyBar = null)).isEmpty())
        assertTrue(frame(policy, 2000, diag(enemyBar = null)).isEmpty())
        assertTrue(frame(policy, 3600, diag(enemyBar = null)).isEmpty())
    }

    @Test fun `phase timeout result and manual reset all require a newly observed ordinary bar`() {
        val policy = policy()
        frame(policy, 0)
        clock = 50 * MS
        policy.prepare(clock)
        assertTrue(frame(policy, 15100, diag(ownBar = null)).isEmpty())
        frame(policy, 15200)
        clock = 15250 * MS
        policy.stop(clock)
        assertTrue(frame(policy, 15300, diag(ownBar = null)).isEmpty())
        clock = 15400 * MS
        policy.begin(clock)
        assertTrue(policy.observe(672, 1493, 15300 * MS, diag()) { true }.isEmpty())
        assertTrue(frame(policy, 15500, diag(ownBar = null)).isEmpty())
        frame(policy, 15600)
        frame(policy, 15700, diag(ownBar = null))
        assertEquals(setOf(player), frame(policy, 15800, diag(ownBar = null)))
    }
    private companion object { const val MS = 1_000_000L }
}
