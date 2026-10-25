package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveHpBarPersistentSmallLossTest {
    private fun recorded(name: String): List<Pair<Long, ActiveHpBarMeasurement>> =
        requireNotNull(javaClass.getResource("/battle/hp/2026-10-25-fire-fang/$name.tsv"))
            .readText().lineSequence().filter { it.firstOrNull()?.isDigit() == true }.map { line ->
                val row = line.split('\t')
                row[0].toLong() to ActiveHpBarMeasurement(
                    row[1].toInt(), row[2].toInt(), row[3].toInt(), row[4].toInt(),
                    row[5].toFloat(), row[6].toFloat()
                )
            }.toList()

    private fun bar(fillPixels: Int, x: Int = 40, y: Int = 440, width: Int = 192) =
        ActiveHpBarMeasurement(x, y, x + width, y + 18, fillPixels / 188f, .9f)

    @Test fun `all five recorded Aegislash small plateaus remain separate observed losses`() {
        for (case in 1..5) {
            val detector = ActiveHpBarDamageTickDetector()
            val samples = recorded("pixel-small-step-$case")
            val ticks = samples.mapNotNull { (at, bar) -> detector.accept(bar, at) }
            assertEquals("case $case: $ticks", 2, ticks.size)
            assertEquals(3f / 188f, ticks[0].lostFraction, .0001f)
            assertEquals(5f / 188f, ticks[1].lostFraction, .0001f)
            // The first candidate reading stays the source time, including the
            // case whose first reading was four pixels low before it settled.
            val firstSmall = samples.first { (_, bar) ->
                kotlin.math.abs(samples.first().second.filledFraction - bar.filledFraction - 3f / 188f) < .0001f
            }.first
            assertEquals(firstSmall, ticks[0].observedAtNanos)
            assertEquals(samples.first().second.filledFraction, ticks.first().beforeFraction, .0001f)
            assertEquals(samples.last().second.filledFraction, ticks.last().afterFraction, .0001f)
        }
    }

    @Test fun `complete Aegislash history retains all five small plateaus`() {
        val detector = ActiveHpBarDamageTickDetector()
        val samples = recorded("pixel-aegislash-complete")
        val ticks = samples.mapNotNull { (at, bar) -> detector.accept(bar, at) }
        assertEquals(40, ticks.size)
        for (case in 1..5) {
            val window = recorded("pixel-small-step-$case")
            val firstSmall = window.first { (_, bar) ->
                kotlin.math.abs(window.first().second.filledFraction - bar.filledFraction - 3f / 188f) < .0001f
            }.first
            val tick = ticks.single { it.observedAtNanos == firstSmall }
            assertEquals(3f / 188f, tick.lostFraction, .0001f)
        }
    }

    @Test fun `first paired match undamaged Mawile has no ticks with geometry on either phone`() {
        for (device in listOf("pixel", "moondrop")) {
            val samples = recorded("$device-undamaged-mawile")
            assertEquals(if (device == "pixel") 274 else 1437, samples.size)
            val detector = ActiveHpBarDamageTickDetector()
            val ticks = samples.mapNotNull { (at, bar) -> detector.accept(bar, at) }
            assertEquals("$device undamaged Mawile: $ticks", emptyList<ActiveHpBarDamageTickMeasurement>(), ticks)
        }
    }

    @Test fun `scalar measurements retain the pixel uncertainty floor even for a long plateau`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(150f / 188f, .9f, 0L, 192)
        for (ms in listOf(100L, 250L, 450L, 700L)) {
            assertNull(detector.accept(147f / 188f, .9f, ms * 1_000_000, 192))
        }
    }

    @Test fun `small loss requires three readings and duration while vertical bobbing is allowed`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(bar(150), 0)
        assertNull(detector.accept(bar(147, y = 435), 100_000_000))
        assertNull(detector.accept(bar(147, y = 448), 500_000_000))
        val tick = detector.accept(bar(147, y = 443), 600_000_000)!!
        assertEquals(100_000_000L, tick.observedAtNanos)
        assertEquals(3f / 188f, tick.lostFraction, .0001f)
        assertNull(detector.accept(bar(147), 800_000_000))
    }

    @Test fun `two consecutive three pixel losses count once each`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(bar(150), 0)
        for (ms in listOf(100L, 250L)) assertNull(detector.accept(bar(147), ms * 1_000_000))
        val first = detector.accept(bar(147), 450_000_000)!!
        assertNull(detector.accept(bar(147), 800_000_000))
        for (ms in listOf(1000L, 1200L)) assertNull(detector.accept(bar(144), ms * 1_000_000))
        val second = detector.accept(bar(144), 1400_000_000)!!
        assertEquals(100_000_000L, first.observedAtNanos)
        assertEquals(1000_000_000L, second.observedAtNanos)
        assertEquals(first.afterFraction, second.beforeFraction, .0001f)
        assertEquals(3f / 188f, second.lostFraction, .0001f)
    }

    @Test fun `one pixel horizontal or width change invalidates a small loss candidate`() {
        for (changed in listOf(bar(147, x = 41), bar(147, width = 193))) {
            val detector = ActiveHpBarDamageTickDetector()
            detector.accept(bar(150), 0)
            detector.accept(bar(147), 100_000_000)
            assertNull(detector.accept(changed, 250_000_000))
            assertNull(detector.accept(bar(147), 450_000_000))
            assertNull(detector.accept(bar(147), 600_000_000))
            assertEquals(450_000_000L, detector.accept(bar(147), 800_000_000)!!.observedAtNanos)
        }
    }

    @Test fun `unchanged HP may refresh settled entry bounds without resetting the HP baseline`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(bar(150, x = 39, width = 193), 0)
        assertNull(detector.accept(bar(150), 100_000_000))
        assertNull(detector.accept(bar(150), 300_000_000))
        detector.accept(bar(147), 500_000_000)
        detector.accept(bar(147), 700_000_000)
        val tick = detector.accept(bar(147), 900_000_000)!!
        assertEquals(500_000_000L, tick.observedAtNanos)
        assertEquals(150f / 188f, tick.beforeFraction, .0001f)
        assertEquals(3f / 188f, tick.lostFraction, .0001f)
    }

    @Test fun `a rebound invalidates the previous small plateau`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(bar(150), 0)
        detector.accept(bar(147), 100_000_000)
        assertNull(detector.accept(bar(150), 250_000_000))
        assertNull(detector.accept(bar(147), 450_000_000))
        assertNull(detector.accept(bar(147), 600_000_000))
        assertEquals(450_000_000L, detector.accept(bar(147), 800_000_000)!!.observedAtNanos)
    }

    @Test fun `long gap reacquires rather than finishing a small loss`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(bar(150), 0)
        detector.accept(bar(147), 100_000_000)
        detector.accept(bar(147), 250_000_000)
        assertNull(detector.accept(bar(147), 1600_000_000))
        assertNull(detector.accept(bar(147), 1800_000_000))
        assertNull(detector.accept(bar(147), 2000_000_000))
        detector.accept(bar(144), 2200_000_000)
        detector.accept(bar(144), 2400_000_000)
        assertEquals(2200_000_000L, detector.accept(bar(144), 2600_000_000)!!.observedAtNanos)
    }

    @Test fun `reset and relocation both discard the old combatant small candidate`() {
        for (relocate in listOf(false, true)) {
            val detector = ActiveHpBarDamageTickDetector()
            detector.accept(bar(150), 0)
            detector.accept(bar(147), 100_000_000)
            detector.accept(bar(147), 250_000_000)
            val x = if (relocate) 100 else 40
            if (!relocate) detector.reset()
            assertNull(detector.accept(bar(147, x), 450_000_000))
            assertNull(detector.accept(bar(147, x), 600_000_000))
            assertNull(detector.accept(bar(147, x), 800_000_000))
            detector.accept(bar(144, x), 1000_000_000)
            detector.accept(bar(144, x), 1200_000_000)
            assertEquals(1000_000_000L, detector.accept(bar(144, x), 1400_000_000)!!.observedAtNanos)
        }
    }

    @Test fun `ordinary large loss cannot retroactively certify an unsettled small loss`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(bar(150), 0)
        detector.accept(bar(147), 100_000_000)
        detector.accept(bar(147), 250_000_000)
        assertNull(detector.accept(bar(138), 500_000_000))
        val tick = detector.accept(bar(138), 650_000_000)!!
        assertEquals(500_000_000L, tick.observedAtNanos)
        assertEquals(12f / 188f, tick.lostFraction, .0001f)
        assertNull(detector.accept(bar(138), 800_000_000))
    }

    @Test fun `three quick samples alone do not establish a small loss`() {
        val detector = ActiveHpBarDamageTickDetector()
        detector.accept(bar(150), 0)
        for (ms in listOf(100L, 150L, 200L)) assertNull(detector.accept(bar(147), ms * 1_000_000))
        assertNull(detector.accept(bar(150), 300_000_000))
    }
}
