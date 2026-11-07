package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.data.BattleCalibration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class HpBarLocatorTimingsTest {
    @Test fun `nested search times include but do not add to extraction and geometry`() {
        var elapsed = 0L
        var cpu = 100L
        val probe = HpBarLocatorTimingProbe({ elapsed }, { cpu }, cpu, 25)

        probe.measure(HpBarLocatorStage.LOCAL_SEARCH) {
            elapsed += 10; cpu += 4
            probe.measure(HpBarLocatorStage.PIXEL_EXTRACTION) { elapsed += 5; cpu += 3 }
            probe.measure(HpBarLocatorStage.GEOMETRY_DETECTION) { elapsed += 30; cpu += 25 }
            elapsed += 2; cpu += 1
        }
        probe.measure(HpBarLocatorStage.ASSIGNMENT) { elapsed += 4; cpu += 2 }
        val result = probe.finish()

        assertEquals(25L, result.lockWaitNanos)
        assertEquals(35L, result.threadCpuNanos)
        assertEquals(HpBarStageTiming(47, 33, 1), result.stages[HpBarLocatorStage.LOCAL_SEARCH])
        assertEquals(HpBarStageTiming(5, 3, 1), result.stages[HpBarLocatorStage.PIXEL_EXTRACTION])
        assertEquals(HpBarStageTiming(30, 25, 1), result.stages[HpBarLocatorStage.GEOMETRY_DETECTION])
        assertEquals(HpBarStageTiming(4, 2, 1), result.stages[HpBarLocatorStage.ASSIGNMENT])
    }

    @Test fun `multiple scan windows aggregate their own stage duration and retain call count`() {
        var elapsed = 0L
        var cpu = 100L
        val probe = HpBarLocatorTimingProbe({ elapsed }, { cpu }, cpu, 0)

        probe.measure(HpBarLocatorStage.GEOMETRY_DETECTION) { elapsed += 40; cpu += 30 }
        probe.measure(HpBarLocatorStage.GEOMETRY_DETECTION) { elapsed += 50; cpu += 35 }

        assertEquals(HpBarStageTiming(90, 65, 2), probe.finish().stages[HpBarLocatorStage.GEOMETRY_DETECTION])
    }

    @Test fun `unknown cpu during a stage stays unknown across later successful samples`() {
        var elapsed = 0L
        var cpu: Long? = 100L
        val probe = HpBarLocatorTimingProbe({ elapsed }, { cpu }, cpu, 0)

        probe.measure(HpBarLocatorStage.GEOMETRY_DETECTION) { elapsed += 20; cpu = null }
        cpu = 150L
        probe.measure(HpBarLocatorStage.GEOMETRY_DETECTION) { elapsed += 30; cpu = 160L }
        val result = probe.finish()

        assertEquals(HpBarStageTiming(50, null, 2), result.stages[HpBarLocatorStage.GEOMETRY_DETECTION])
        assertEquals("Whole-call start/end clock readings are still available", 60L, result.threadCpuNanos)
    }

    @Test fun `regressing cpu clock cannot become a negative or invented cpu duration`() {
        var cpu = 100L
        val probe = HpBarLocatorTimingProbe({ 0L }, { cpu }, cpu, 0)
        probe.measure(HpBarLocatorStage.ASSIGNMENT) { cpu = 99L }

        assertNull(probe.finish().threadCpuNanos)
        assertNull(probe.finish().stages.getValue(HpBarLocatorStage.ASSIGNMENT).cpuNanos)
    }

    @Test fun `timing never swallows a recognition exception`() {
        var elapsed = 0L
        val probe = HpBarLocatorTimingProbe({ elapsed }, { null }, null, 0)
        val expected = IllegalStateException("original scan failure")
        try {
            probe.measure(HpBarLocatorStage.GEOMETRY_DETECTION) { elapsed += 10; throw expected }
            fail("The original failure must escape unchanged")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }
        assertEquals(HpBarStageTiming(10, null, 1), probe.finish().stages[HpBarLocatorStage.GEOMETRY_DETECTION])
    }

    @Test fun `tracker measures monitor wait separately from the search after acquisition`() {
        val requested = CountDownLatch(1)
        val firstClockRead = AtomicBoolean(true)
        val elapsed = AtomicLong(0L)
        val result = AtomicReference<HpBarFrameDiagnostics>()
        val failure = AtomicReference<Throwable>()
        val tracker = LiveBattlefieldHpTracker(
            BattleCalibration(),
            elapsedTimeNanos = {
                elapsed.get().also { if (firstClockRead.compareAndSet(true, false)) requested.countDown() }
            },
            threadCpuTimeNanos = { 100L },
        )
        val worker = thread(start = false, name = "hp-locator-lock-timing-test") {
            try {
                result.set(tracker.measureFrameWithDiagnostics(
                    672, 1493, 0,
                    resolveRegions = { ActivePokemonSide.entries.associateWith { BattleCropBounds(0, 367, 672, 1080) } },
                    scan = { elapsed.addAndGet(50); emptyList() },
                    canObserve = { true },
                ))
            } catch (error: Throwable) {
                failure.set(error)
            }
        }

        synchronized(tracker) {
            worker.start()
            assertTrue("Worker must reach the monitor request", requested.await(5, TimeUnit.SECONDS))
            val blockedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (worker.state != Thread.State.BLOCKED && System.nanoTime() < blockedDeadline) Thread.yield()
            assertEquals("Worker must wait on the occupied tracker monitor", Thread.State.BLOCKED, worker.state)
            elapsed.set(700L)
        }
        worker.join(5_000)
        assertFalse("Worker must complete once the monitor is released", worker.isAlive)
        failure.get()?.let { throw AssertionError("Locator worker failed", it) }
        val timing = requireNotNull(result.get().timings)
        assertEquals(700L, timing.lockWaitNanos)
        assertEquals(HpBarStageTiming(50, 0, 1), timing.stages[HpBarLocatorStage.RECOVERY_SEARCH])
        assertEquals(0L, timing.threadCpuNanos)
    }
}
