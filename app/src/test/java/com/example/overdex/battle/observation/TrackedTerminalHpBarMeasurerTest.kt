package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedTerminalHpBarMeasurerTest {
    private val anchor = ActiveHpBarMeasurement(40, 80, 240, 96, .05f, .95f)

    @Test fun `one visible empty frame is retained while a vanished bar stays unavailable`() {
        val red = measure(bar(fillWidth = 9))
        assertNotNull(red)
        assertEquals(HpBarFillColor.RED, red!!.fillColor)
        assertTrue(red.measurement.filledFraction in .03f.. .07f)

        val empty = measure(bar(fillWidth = 0))
        assertNotNull(empty)
        assertEquals(HpBarFillColor.EMPTY, empty!!.fillColor)
        assertEquals(0f, empty.measurement.filledFraction, 0f)
        assertEquals(anchor.left, empty.measurement.left)
        assertEquals(anchor.right, empty.measurement.right)
        assertEquals(anchor.top, empty.measurement.top)
        assertEquals(anchor.bottom, empty.measurement.bottom)

        assertNull(measure { _, _ -> DARK })
    }

    @Test fun `a single interior column of pale red remains positive hp`() {
        val sample = measure(bar(fillWidth = 1, fill = PALE_RED))
        assertNotNull(sample)
        assertEquals(HpBarFillColor.RED, sample!!.fillColor)
        assertTrue(sample.measurement.filledFraction > 0f)
        assertTrue(sample.measurement.filledFraction < .02f)
    }

    @Test fun `classification reports literal green and does not derive red from a short fraction`() {
        assertNull(measure(bar(fillWidth = 12, fill = GREEN)))
        assertEquals(HpBarFillColor.GREEN,
            TrackedTerminalHpBarMeasurer.measureFillColor(400, 300, anchor, bar(fillWidth = 150, fill = GREEN)))
    }

    @Test fun `a detached red particle in a dark rectangle cannot become low hp or zero`() {
        val outline = bar(fillWidth = 0)
        assertNull(measure { x, y ->
            if (x in 130..135 && y in 84..92) PALE_RED else outline(x, y)
        })
    }

    @Test fun `a sub-column coloured particle blocks an empty conclusion`() {
        val outline = bar(fillWidth = 0)
        assertNull(measure { x, y -> if (x == 100 && y == 88) PALE_RED else outline(x, y) })
    }

    @Test fun `a dim red sliver cannot disappear into the dark empty-bar interior`() {
        val outline = bar(fillWidth = 0)
        assertNull(measure { x, y ->
            if (x == 42 && y in 84..92) 0xff96645a.toInt() else outline(x, y)
        })
    }

    @Test fun `two horizontal outlines without vertical end caps cannot become empty`() {
        assertNull(measure { x, y ->
            if ((y == anchor.top || y == anchor.bottom) && x in anchor.left until anchor.right) WHITE else DARK
        })
    }

    @Test fun `white flash and bright scenery inside an intact rectangle are unavailable`() {
        val outline = bar(fillWidth = 0)
        for (interior in listOf(WHITE, SKY, 0xffced9c4.toInt())) {
            assertNull(measure { x, y ->
                if (x in anchor.left + 2 until anchor.right - 2 && y in anchor.top + 2 until anchor.bottom - 1)
                    interior else outline(x, y)
            })
        }
    }

    @Test fun `both paired outlines and caps must share a valid border state`() {
        val outline = bar(fillWidth = 0)
        assertNull(measure { x, y ->
            if ((x == anchor.left || x == anchor.right - 1) && y in anchor.top + 1 until anchor.bottom)
                ORANGE else outline(x, y)
        })
        assertNotNull(measure(bar(fillWidth = 0, border = ORANGE)))
        assertNotNull(measure(bar(fillWidth = 0, border = BLUE)))
    }

    @Test fun `a tiny vertical displacement is measured in original source coordinates`() {
        val sample = measure(bar(fillWidth = 6, dy = 4))
        assertNotNull(sample)
        assertEquals(anchor.top + 4, sample!!.measurement.top)
        assertEquals(anchor.bottom + 4, sample.measurement.bottom)
        assertEquals(anchor.left, sample.measurement.left)
        assertEquals(anchor.right, sample.measurement.right)
    }

    @Test fun `large camera displacement never expands the terminal search`() {
        assertNull(measure(bar(fillWidth = 0, dy = 30)))
    }

    @Test fun `only a tiny rectangle is read once regardless of frame dimensions`() {
        val source = bar(fillWidth = 0)
        val coordinates = mutableSetOf<Pair<Int, Int>>()
        val sample = TrackedTerminalHpBarMeasurer.measure(1080, 2400, anchor) { x, y ->
            assertTrue("Repeated source read", coordinates.add(x to y))
            assertTrue(x in 37..242)
            assertTrue(y in 70..106)
            source(x, y)
        }
        assertNotNull(sample)
        assertTrue(coordinates.size <= 206 * 37)
        assertTrue(coordinates.size < 1080 * 2400 / 100)
    }

    @Test fun `invalid or clipped anchors never read any source pixel`() {
        val invalid = listOf(
            anchor.copy(left = 0), anchor.copy(right = 400),
            anchor.copy(top = -1), anchor.copy(bottom = 300),
            anchor.copy(right = anchor.left + 20), anchor.copy(bottom = anchor.top + 3),
        )
        for (candidate in invalid) {
            assertNull(TrackedTerminalHpBarMeasurer.measure(400, 300, candidate) { _, _ ->
                error("An invalid anchor must not be sampled")
            })
        }
    }

    @Test fun `interrupted red fill does not become a false endpoint`() {
        val source = bar(fillWidth = 14)
        assertNull(measure { x, y ->
            if (x in 80..105 && y in 84..92) PALE_RED else source(x, y)
        })
    }

    @Test fun `contiguous orange damage remainder is excluded from red remaining hp`() {
        val source = bar(fillWidth = 14)
        val sample = measure { x, y ->
            if (x in 56..110 && y in 84..92) ORANGE else source(x, y)
        }
        assertNotNull(sample)
        assertEquals(HpBarFillColor.RED, sample!!.fillColor)
        assertEquals(14f / 196f, sample.measurement.filledFraction, .001f)
    }

    @Test fun `a detached orange particle cannot be excused as the damage remainder`() {
        val source = bar(fillWidth = 14)
        assertNull(measure { x, y ->
            if (x in 90..110 && y in 84..92) ORANGE else source(x, y)
        })
    }

    @Test fun `a bounded dark prefix before a real red band survives the thick inner border`() {
        val sample = measure { x, y ->
            val outline = bar(fillWidth = 0)
            if (x in 46..60 && y in 84..92) PALE_RED else outline(x, y)
        }
        assertNotNull(sample)
        assertEquals(HpBarFillColor.RED, sample!!.fillColor)
        assertTrue(sample.measurement.filledFraction in .08f.. .11f)
    }

    @Test fun `a red band beyond the bounded prefix remains a detached particle`() {
        val outline = bar(fillWidth = 0)
        assertNull(measure { x, y ->
            if (x in 51..65 && y in 84..92) PALE_RED else outline(x, y)
        })
    }

    @Test fun `neutral olive end cap antialiasing does not become yellow hp`() {
        val outline = bar(fillWidth = 0)
        val sample = measure { x, y ->
            if (x == 237 && y in 84..92) 0xff97a38d.toInt() else outline(x, y)
        }
        assertNotNull(sample)
        assertEquals(HpBarFillColor.EMPTY, sample!!.fillColor)
        assertEquals(0f, sample.measurement.filledFraction, 0f)
    }

    @Test fun `a literal yellow end column is not an empty bar`() {
        val outline = bar(fillWidth = 0)
        assertNull(measure { x, y ->
            if (x == 237 && y in 84..92) 0xfff7ee59.toInt() else outline(x, y)
        })
    }

    private fun measure(pixel: (Int, Int) -> Int): TrackedTerminalHpBarSample? =
        TrackedTerminalHpBarMeasurer.measure(400, 300, anchor, pixel)

    private fun bar(
        fillWidth: Int,
        fill: Int = PALE_RED,
        border: Int = WHITE,
        dy: Int = 0,
    ): (Int, Int) -> Int = { x, y ->
        when {
            (y == anchor.top + dy || y == anchor.bottom + dy) && x in anchor.left until anchor.right -> border
            (x == anchor.left || x == anchor.right - 1) && y in anchor.top + dy..anchor.bottom + dy -> border
            x in anchor.left + 2 until anchor.left + 2 + fillWidth &&
                y in anchor.top + dy + 2 until anchor.bottom + dy - 1 -> fill
            else -> DARK
        }
    }

    companion object {
        private val WHITE = 0xffeeeeee.toInt()
        private val DARK = 0xff4a573c.toInt()
        private val PALE_RED = 0xffec8575.toInt()
        private val GREEN = 0xff1effbc.toInt()
        private val ORANGE = 0xffff8a20.toInt()
        private val BLUE = 0xff4488ff.toInt()
        private val SKY = 0xff88bbf4.toInt()
    }
}
