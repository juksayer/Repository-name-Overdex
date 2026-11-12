package com.example.overdex.battle.observation

import org.junit.Assert.*
import org.junit.Test

class ChargedImpactHpBarMeasurerTest {
    private val reference = ActiveHpBarMeasurement(460, 250, 660, 266, .83f, .95f)
    private val centered = ActiveHpBarMeasurement(250, 355, 450, 371, 0f, .95f)
    private fun measure(pixelAt: (Int, Int) -> Int) =
        ChargedImpactHpBarMeasurer.acquire(700, 700, reference, pixelAt)

    @Test fun `finds a single clear empty camera outline far from the normal anchor`() {
        val measured = requireNotNull(measure(bar()))
        assertEquals(HpBarFillColor.EMPTY, measured.fillColor)
        assertEquals(0f, measured.measurement.filledFraction, 0f)
        assertEquals(centered.left, measured.measurement.left)
        assertEquals(centered.top, measured.measurement.top)
        assertNull(TrackedTerminalHpBarMeasurer.measure(700, 700, reference, bar()))
    }

    @Test fun `actual red or green fill is measured without requiring prior red`() {
        val red = requireNotNull(measure(bar(fillWidth = 12, fill = RED)))
        assertEquals(HpBarFillColor.RED, red.fillColor)
        assertTrue(red.measurement.filledFraction > 0f)
        val green = requireNotNull(measure(bar(fillWidth = 150, fill = GREEN)))
        assertEquals(HpBarFillColor.GREEN, green.fillColor)
        assertTrue(green.measurement.filledFraction > .7f)
    }

    @Test fun `missing caps absent bar and flash remain unavailable`() {
        assertNull(measure { _, _ -> DARK })
        val outlined = bar()
        assertNull(measure { x, y -> if (x in centered.left + 2 until centered.right - 2 &&
            y in centered.top + 2 until centered.bottom - 1) WHITE else outlined(x, y) })
        assertNull(measure { x, y -> if (x == centered.left || x == centered.right - 1) DARK else outlined(x, y) })
    }

    @Test fun `orange-only damage remainder remains unknown rather than zero`() {
        assertNull(measure(bar(fillWidth = 12, fill = ORANGE)))
    }

    @Test fun `a central beam and even one surviving red pixel cannot become empty`() {
        val outlined = bar()
        assertNull(measure { x, y -> if (x in 325..355 && y in centered.top + 2 until centered.bottom - 1)
            WHITE else outlined(x, y) })
        assertNull(measure { x, y -> if (x == centered.left + 3 && y == centered.top + 5)
            RED else outlined(x, y) })
    }

    @Test fun `wrong sized or noncentral outlines cannot be assigned from the phase cue`() {
        assertNull(measure(bar(centered.copy(right = 400))))
        assertNull(measure(bar(centered.copy(left = 400, right = 600))))
    }

    @Test fun `two different plausible central bars remain ambiguous`() {
        val first = bar()
        val second = bar(centered.copy(top = 400, bottom = 416))
        assertNull(measure { x, y -> val value = first(x, y); if (value != DARK) value else second(x, y) })
    }

    @Test fun `only the bounded impact rectangle is read and pixels are not reread for verification`() {
        val seen = mutableSetOf<Pair<Int, Int>>()
        val source = bar()
        assertNotNull(measure { x, y ->
            assertTrue(seen.add(x to y))
            assertTrue(x in 208 until 492 && y in 238 until 434)
            source(x, y)
        })
        assertTrue(seen.size <= 284 * 196)
    }

    @Test fun `calibrated territory finds a higher recipient bar outside the former fixed band`() {
        val higher = centered.copy(top = 190, bottom = 206)
        val source = bar(higher, fillWidth = 70, fill = YELLOW)
        assertNull(measure(source))
        val territory = BattleCropBounds(225, 170, 475, 225)
        val sample = requireNotNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, territory) { x, y ->
            assertTrue(x in territory.left until territory.right && y in territory.top until territory.bottom)
            source(x, y)
        })
        assertEquals(higher.top, sample.measurement.top)
        assertEquals(HpBarFillColor.YELLOW, sample.fillColor)
    }

    @Test fun `moving the box cannot silently fall back to the old territory`() {
        val source = bar(fillWidth = 12, fill = RED)
        val moved = BattleCropBounds(225, 170, 475, 225)
        assertNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, moved, source))
        assertNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference,
            BattleCropBounds(225, 355, 475, 365), source)) // Outline clipped by calibration.
    }

    @Test fun `invalid calibrated territory is unavailable without reading source pixels`() {
        for (territory in listOf(BattleCropBounds(-1, 170, 475, 225), BattleCropBounds(225, 170, 701, 225),
                BattleCropBounds(225, 225, 475, 170))) {
            assertNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, territory) { _, _ ->
                fail("Invalid territory must not read pixels"); DARK
            })
        }
    }

    @Test fun `neighboring crops read one complete bar with fill outside the empty portion`() {
        val impact = BattleCropBounds(225, 330, 350, 400)
        val ordinary = BattleCropBounds(350, 330, 475, 400)
        val source = bar(fillWidth = 70, fill = YELLOW)
        // The ordinary region contains only the unfilled right portion. Neither
        // incomplete outline independently supplies a fraction, especially zero.
        assertNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, impact, source))
        assertNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, ordinary, source))
        val shared = requireNotNull(ChargedImpactHpBarMeasurer.sharedTerritory(700, 700, impact, ordinary))
        val sample = requireNotNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, shared, source))
        assertEquals(HpBarFillColor.YELLOW, sample.fillColor)
        assertTrue(sample.measurement.filledFraction in .3f.. .4f)
        val tracked = requireNotNull(TrackedTerminalHpBarMeasurer.measure(700, 700, sample.measurement, shared, source))
        assertEquals(HpBarFillColor.YELLOW, tracked.fillColor)
        assertEquals(sample.measurement.filledFraction, tracked.measurement.filledFraction, .02f)
        // A real complete empty outline remains measurable after the handoff.
        assertEquals(HpBarFillColor.EMPTY,
            ChargedImpactHpBarMeasurer.acquire(700, 700, reference, shared, bar())?.fillColor)
    }

    @Test fun `vertical crop boundary cannot cut the interior away from its outline`() {
        val impact = BattleCropBounds(225, 363, 475, 400)
        val ordinary = BattleCropBounds(225, 330, 475, 363)
        val source = bar(fillWidth = 12, fill = RED)
        assertNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, impact, source))
        assertNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, ordinary, source))
        val shared = requireNotNull(ChargedImpactHpBarMeasurer.sharedTerritory(700, 700, impact, ordinary))
        val sample = requireNotNull(ChargedImpactHpBarMeasurer.acquire(700, 700, reference, shared, source))
        assertEquals(HpBarFillColor.RED, sample.fillColor)
        assertTrue(sample.measurement.filledFraction > 0f)
    }

    @Test fun `sharing cannot bypass missing invalid or unrelated impact calibration`() {
        val impact = BattleCropBounds(225, 330, 350, 400)
        assertNull(ChargedImpactHpBarMeasurer.sharedTerritory(700, 700, null, impact))
        assertNull(ChargedImpactHpBarMeasurer.sharedTerritory(700, 700, impact.copy(left = -1), impact))
        for (other in listOf(null, BattleCropBounds(500, 330, 650, 400),
                BattleCropBounds(350, 400, 475, 450), impact.copy(right = 701))) {
            assertEquals(impact, ChargedImpactHpBarMeasurer.sharedTerritory(700, 700, impact, other))
        }
        assertEquals(BattleCropBounds(225, 330, 475, 410),
            ChargedImpactHpBarMeasurer.sharedTerritory(700, 700, impact, BattleCropBounds(340, 340, 475, 410)))
    }

    private fun bar(shape: ActiveHpBarMeasurement = centered, fillWidth: Int = 0, fill: Int = RED): (Int, Int) -> Int = { x, y ->
        when {
            (y == shape.top || y == shape.bottom) && x in shape.left until shape.right -> WHITE
            (x == shape.left || x == shape.right - 1) && y in shape.top..shape.bottom -> WHITE
            x in shape.left + 2 until shape.left + 2 + fillWidth && y in shape.top + 2 until shape.bottom - 1 -> fill
            else -> DARK
        }
    }

    private companion object {
        const val WHITE = -0x111112
        const val DARK = -0xb5a8c4
        const val RED = -0x137a8b
        const val GREEN = -0xe10044
        const val YELLOW = 0xffe3cf35.toInt()
        const val ORANGE = -0x75e0
    }
}
