package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.data.BattleCalibration
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class LiveBattlefieldHpAcquisitionTest {
    private val player = ActivePokemonSide.PLAYER
    private val opponent = ActivePokemonSide.OPPONENT
    private val regions = mapOf(
        player to BattleCropBounds(35, 400, 241, 1000),
        opponent to BattleCropBounds(312, 400, 576, 1000),
    )
    private val acquisitionWindows = listOf(
        BattleCropBounds(0, 600, 298, 800),
        BattleCropBounds(255, 600, 633, 800),
    )
    private val wideWindow = BattleCropBounds(0, 400, 672, 1000)

    @Test fun `initial search uses crop midpoints and preserves saved calibration`() {
        val before = regions.toMap()
        val tracker = BattlefieldHpTracker()

        assertEquals(acquisitionWindows, tracker.searchWindows(672, regions))
        assertTrue(tracker.trackingWindows(672, regions).isEmpty())
        assertEquals(before, regions)
    }

    @Test fun `small acquisition bands retain minimum height without crossing crop or screen edges`() {
        val narrowRegions = mapOf(
            player to BattleCropBounds(0, 480, 180, 560),
            opponent to BattleCropBounds(500, 480, 672, 560),
        )
        assertEquals(listOf(
            BattleCropBounds(0, 496, 237, 544),
            BattleCropBounds(443, 496, 672, 544),
        ), BattlefieldHpTracker().searchWindows(672, narrowRegions))

        val tightRegions = narrowRegions.mapValues { (_, bounds) -> bounds.copy(top = 500, bottom = 540) }
        assertEquals(listOf(
            BattleCropBounds(0, 500, 237, 540),
            BattleCropBounds(443, 500, 672, 540),
        ), BattlefieldHpTracker().searchWindows(672, tightRegions))
    }

    @Test fun `both midpoint bars are acquired without a full height recovery scan`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        val bars = listOf(bar(40), bar(400))
        val scans = mutableListOf<BattleCropBounds>()
        val result = tracker.read(0, bars, scans)

        assertEquals(acquisitionWindows, scans)
        assertEquals(mapOf(player to bars[0], opponent to bars[1]), result.measurements)
        assertEquals(setOf(HpBarLocatorOutcome.FOUND), result.outcomes.values.toSet())
        assertFalse(requireNotNull(result.timings).stages.containsKey(HpBarLocatorStage.RECOVERY_SEARCH))
    }

    @Test fun `one or both bars outside midpoint bands recover in the same source frame`() {
        for (bars in listOf(
            listOf(bar(40, 450), bar(400, 900)),
            listOf(bar(40), bar(400, 900)),
        )) {
            val scans = mutableListOf<BattleCropBounds>()
            val result = LiveBattlefieldHpTracker(BattleCalibration()).read(0, bars, scans)

            assertEquals(acquisitionWindows + wideWindow, scans)
            assertEquals(mapOf(player to bars[0], opponent to bars[1]), result.measurements)
            assertEquals(1, requireNotNull(result.timings).stages.getValue(HpBarLocatorStage.RECOVERY_SEARCH).calls)
        }
    }

    @Test fun `provisional narrow partial cannot establish a wrong home size before wide recovery`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        val narrowArtwork = bar(40).copy(right = 206)
        val actual = listOf(bar(40), bar(400, 900))
        val scans = mutableListOf<BattleCropBounds>()
        val acquired = tracker.measureFrameWithDiagnostics(672, 1493, 0, { regions }, { bounds ->
            scans += bounds
            val candidates = if (bounds == wideWindow) actual else listOf(narrowArtwork)
            candidates.filter { bar ->
                bar.left >= bounds.left && bar.right <= bounds.right &&
                    bar.top >= bounds.top && bar.bottom <= bounds.bottom
            }
        }, { true })

        assertEquals(acquisitionWindows + wideWindow, scans)
        assertEquals("The provisional 166px outline cannot reject either real 192px bar",
            mapOf(player to actual[0], opponent to actual[1]), acquired.measurements)

        val trackedScans = mutableListOf<BattleCropBounds>()
        val tracked = tracker.read(33_000_000, actual, trackedScans)
        assertEquals(mapOf(player to actual[0], opponent to actual[1]), tracked.measurements)
        assertEquals(2, trackedScans.size)
        assertFalse("The recovered 192px home should continue tracking without another wide search",
            requireNotNull(tracked.timings).stages.containsKey(HpBarLocatorStage.RECOVERY_SEARCH))
    }

    @Test fun `reset and a new species reacquire only the missing side and retain the other lock`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.observeSpecies(player, "First player", 0)
        tracker.observeSpecies(opponent, "First opponent", 0)
        val initialBars = listOf(bar(40, 450), bar(400, 900))
        tracker.read(0, initialBars)

        tracker.observeSpecies(opponent, "New opponent", 1)
        val changedBars = listOf(initialBars[0], bar(400))
        val changedScans = mutableListOf<BattleCropBounds>()
        val changed = tracker.read(33_000_000, changedBars, changedScans)
        val playerWindow = BattlefieldHpTracker().also { it.assign(672, 0, regions, initialBars) }
            .trackingWindows(672, regions).first()
        assertEquals(listOf(playerWindow, acquisitionWindows[1]), changedScans)
        assertEquals(mapOf(player to changedBars[0], opponent to changedBars[1]), changed.measurements)

        tracker.reset(player)
        val resetBars = listOf(bar(40), changedBars[1])
        val resetScans = mutableListOf<BattleCropBounds>()
        val reset = tracker.read(66_000_000, resetBars, resetScans)
        val opponentWindow = BattlefieldHpTracker().also { it.assign(672, 0, regions, changedBars) }
            .trackingWindows(672, regions).last()
        assertEquals(listOf(acquisitionWindows[0], opponentWindow), resetScans)
        assertEquals(mapOf(player to resetBars[0], opponent to resetBars[1]), reset.measurements)
    }

    @Test fun `unchanged or older species observation does not discard an existing off center lock`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.observeSpecies(player, "Player", 0)
        tracker.observeSpecies(opponent, "Opponent", 0)
        val bars = listOf(bar(40, 450), bar(400, 900))
        tracker.read(0, bars)
        tracker.observeSpecies(opponent, "Opponent", 20)
        tracker.observeSpecies(opponent, "Outdated opponent", 10)
        val scans = mutableListOf<BattleCropBounds>()

        val result = tracker.read(33_000_000, bars, scans)

        val expected = BattlefieldHpTracker().also { it.assign(672, 0, regions, bars) }
            .trackingWindows(672, regions)
        assertEquals(expected, scans)
        assertEquals(mapOf(player to bars[0], opponent to bars[1]), result.measurements)
    }

    @Test fun `source resize discards old off center locks and uses new midpoint windows`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.read(0, listOf(bar(40, 450), bar(400, 900)))
        val bars = listOf(bar(40), bar(400))
        val scans = mutableListOf<BattleCropBounds>()

        val result = tracker.read(33_000_000, bars, scans, width = 700)

        assertEquals(BattlefieldHpTracker().searchWindows(700, regions), scans)
        assertEquals(mapOf(player to bars[0], opponent to bars[1]), result.measurements)
        assertFalse(requireNotNull(result.timings).stages.containsKey(HpBarLocatorStage.RECOVERY_SEARCH))
    }

    @Test fun `acquired bars follow camera movement outside the original midpoint band`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.read(0, listOf(bar(40), bar(400)))

        for (step in 1..3) {
            val bars = listOf(bar(40, 690 + 40 * step), bar(400, 690 - 40 * step))
            val scans = mutableListOf<BattleCropBounds>()
            val result = tracker.read(step * 100_000_000L, bars, scans)

            assertEquals(mapOf(player to bars[0], opponent to bars[1]), result.measurements)
            assertEquals(2, scans.size)
            assertFalse(requireNotNull(result.timings).stages.containsKey(HpBarLocatorStage.RECOVERY_SEARCH))
        }
    }

    @Test fun `midpoint search does not invent ownership or accept obstructed bars`() {
        val centered = LiveBattlefieldHpTracker(BattleCalibration()).read(0, listOf(bar(240)))
        assertTrue(centered.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.UNASSIGNED_CANDIDATES), centered.outcomes.values.toSet())

        val hidden = LiveBattlefieldHpTracker(BattleCalibration()).read(0, listOf(bar(40), bar(400)), canObserve = { false })
        assertTrue(hidden.measurements.isEmpty())
        assertEquals(setOf(HpBarLocatorOutcome.OBSCURED_CANDIDATES), hidden.outcomes.values.toSet())
    }

    @Test fun `centered recipient requires recent uniquely owned motion even during acquisition of the missing side`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.read(0, listOf(bar(400)))
        for (step in 1..4) {
            val moving = bar(400 - 40 * step)
            val result = tracker.read(step * 100_000_000L, listOf(moving))
            assertEquals(mapOf(opponent to moving), result.measurements)
        }
        tracker.read(500_000_000, emptyList())
        val stale = tracker.read(1_000_000_000, listOf(bar(240)))
        assertTrue("An old centered lock cannot assign a newly seen bar", stale.measurements.isEmpty())
    }

    @Test fun `midpoint acquisition intentionally excludes unrelated off band candidate without changing assignment rules`() {
        val actual = listOf(bar(40), bar(400))
        val unrelated = bar(220, 450)
        val allCandidates = actual + unrelated
        assertTrue("The wide three-candidate scene remains ambiguous", BattlefieldHpTracker()
            .assign(672, 0, regions, allCandidates).isEmpty())

        val scans = mutableListOf<BattleCropBounds>()
        val result = LiveBattlefieldHpTracker(BattleCalibration()).read(0, allCandidates, scans)

        assertEquals(acquisitionWindows, scans)
        assertEquals(mapOf(player to actual[0], opponent to actual[1]), result.measurements)
    }

    @Test fun `recorded source pixels retain full end caps and both sides after midpoint acquisition or recovery`() {
        val recordedRegions = mapOf(
            player to BattleCropBounds(35, 367, 241, 1080),
            opponent to BattleCropBounds(312, 381, 576, 863),
        )
        val before = recordedRegions.toMap()
        for (frame in listOf("055", "089")) {
            val source = ImageIO.read(File("src/test/resources/battle/recorded-evidence/2026-10-12-1546/frame-$frame.png"))
            val result = LiveBattlefieldHpTracker(BattleCalibration()).measureFrameWithDiagnostics(
                source.width, source.height, 0, { recordedRegions }, { bounds ->
                    val width = bounds.right - bounds.left
                    val height = bounds.bottom - bounds.top
                    val pixels = source.getRGB(bounds.left, bounds.top, width, height, null, 0, width)
                    ActiveHpBarMeasurer.measureAll(width, height, source.width * 2 / 7, pixels).map {
                        it.copy(left = it.left + bounds.left, right = it.right + bounds.left,
                            top = it.top + bounds.top, bottom = it.bottom + bounds.top)
                    }
                }, { true },
            )

            assertEquals("Both recorded sides must remain measurable in frame $frame", setOf(player, opponent), result.measurements.keys)
            val own = result.measurements.getValue(player)
            val enemy = result.measurements.getValue(opponent)
            assertTrue(own.centerX < enemy.centerX)
            if (frame == "055") {
                assertTrue("The full cap beyond saved X=576 must be retained: $enemy", enemy.right > 576)
                assertEquals(192f, enemy.width.toFloat(), 5f)
                assertTrue(enemy.filledFraction > .96f)
            }
        }
        assertEquals("Acquisition and recovery never rewrite saved calibration", before, recordedRegions)
    }

    @Test fun `same species reentry clears old centered ownership and rejects boundary pixels`() {
        val tracker = LiveBattlefieldHpTracker(BattleCalibration())
        tracker.observeSpecies(opponent, "Sealeo", 1)
        tracker.read(2, listOf(bar(400)))
        tracker.read(3, listOf(bar(360)))
        tracker.read(4, listOf(bar(320)))
        tracker.appearanceBoundary(opponent, 10)
        tracker.observeSpecies(opponent, "Sealeo", 10)
        assertTrue(tracker.read(9,listOf(bar(240))).measurements.isEmpty())
        assertTrue(tracker.read(11,listOf(bar(240))).measurements.isEmpty())
        assertEquals(bar(400),tracker.read(300_000_000,listOf(bar(400))).measurements[opponent])
        assertTrue(tracker.read(12,listOf(bar(240))).measurements.isEmpty())
    }

    private fun LiveBattlefieldHpTracker.read(
        at: Long,
        bars: List<ActiveHpBarMeasurement>,
        scans: MutableList<BattleCropBounds> = mutableListOf(),
        width: Int = 672,
        canObserve: (ActiveHpBarMeasurement) -> Boolean = { true },
    ) = measureFrameWithDiagnostics(width, 1493, at, { regions }, { bounds ->
        scans += bounds
        bars.filter { bar ->
            bar.left >= bounds.left && bar.right <= bounds.right &&
                bar.top >= bounds.top && bar.bottom <= bounds.bottom
        }
    }, canObserve)

    private fun bar(left: Int, top: Int = 690) =
        ActiveHpBarMeasurement(left, top, left + 192, top + 17, .8f, .95f)
}
