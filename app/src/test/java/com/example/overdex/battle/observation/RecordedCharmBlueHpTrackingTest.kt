package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

/** Video-derived pixels at live capture scale; source PTS and transforms are in provenance.json. */
class RecordedCharmBlueHpTrackingTest {
    private val regions = mapOf(
        ActivePokemonSide.PLAYER to BattleCropBounds(0, 450, 310, 750),
        ActivePokemonSide.OPPONENT to BattleCropBounds(270, 450, 580, 570),
    )

    private fun measurements(name: String, expectedWidth: Int? = 192): List<ActiveHpBarMeasurement> {
        val image = ImageIO.read(requireNotNull(javaClass.getResource("/battle/hp/2026-10-27-charm-blue/$name.png")))
        return ActiveHpBarMeasurer.measureAll(image.width, image.height, expectedWidth,
            image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }

    private fun assigned(name: String, expectedWidth: Int? = 192): ActiveHpBarMeasurement {
        val candidates = measurements(name, expectedWidth).map {
            it.copy(left = it.left + 270, right = it.right + 270, top = it.top + 450, bottom = it.bottom + 450)
        }
        val assigned = BattlefieldHpTracker().assign(672, 0L, regions, candidates)
        assertEquals("$name must be one opponent bar, not competing sky outlines: $candidates",
            setOf(ActivePokemonSide.OPPONENT), assigned.keys)
        return requireNotNull(assigned[ActivePokemonSide.OPPONENT])
    }

    @Test fun `blue sky joins are recovered without duplicate bars or a wider HP denominator`() {
        val expected = mapOf(
            "bar-28.113322" to .973f,
            "bar-28.644667" to .978f,
            "bar-29.684933" to .941f,
            "bar-30.302922" to .941f,
            "bar-31.126744" to .933f,
            "bar-31.486967" to .925f,
            "bar-32.660622" to .897f,
            "bar-35.947722" to .855f,
        )
        for ((name, fraction) in expected) {
            val bar = assigned(name)
            assertTrue("$name left cap: $bar", bar.left in 334..339)
            assertTrue("$name right cap: $bar", bar.right in 523..527)
            assertTrue("$name height: $bar", bar.bottom - bar.top in 17..19)
            assertEquals("$name fill", fraction, bar.filledFraction, .003f)
        }
        // Before the fix this blue frame supplied 200–219 px-wide candidates,
        // reducing an unchanged ~94% fill to 82–90% and preventing assignment.
        val white = assigned("bar-29.684933")
        val blue = assigned("bar-30.302922")
        assertEquals(white.left, blue.left)
        assertEquals(white.right, blue.right)
        assertEquals(white.filledFraction, blue.filledFraction, .001f)
    }

    @Test fun `ordinary crop path also recovers bounded blue bars and abstains from widened sky`() {
        for (name in listOf("bar-28.644667", "bar-31.486967", "bar-35.947722")) {
            val tracked = assigned(name)
            val unanchored = assigned(name, null)
            assertTrue(kotlin.math.abs(tracked.left - unanchored.left) <= 1)
            assertTrue(kotlin.math.abs(tracked.right - unanchored.right) <= 1)
            assertEquals(tracked.filledFraction, unanchored.filledFraction, .002f)
        }
        // Without expected width this frame's different contrast inset remains
        // unresolved. Reporting no HP is preferable to measuring blue scenery.
        assertTrue(measurements("bar-30.302922", null).isEmpty())
    }

    @Test fun `fragmented blue outlines remain unavailable rather than inventing end caps`() {
        for (name in listOf("bar-29.216789", "bar-34.662433")) {
            for (expectedWidth in listOf(192, null)) {
                assertTrue("$name expected=$expectedWidth", measurements(name, expectedWidth).isEmpty())
            }
        }
    }

    @Test fun `recorded sky shadow aura artwork and touch ring never become HP`() {
        for (name in listOf("negative-blue-sky", "negative-shadow-aura", "negative-pokemon-art", "negative-touch-ring")) {
            for (expectedWidth in listOf(192, null)) {
                assertTrue("$name expected=$expectedWidth", measurements(name, expectedWidth).isEmpty())
            }
        }
    }

    @Test fun `recorded idle plateau remains stable and produces no damage`() {
        val detector = ActiveHpBarDamageTickDetector()
        val samples = listOf(
            40_021_411_111L to "bar-40.021411",
            41_519_600_000L to "bar-41.519600",
            42_023_955_556L to "bar-42.023956",
        )
        val ticks = samples.mapNotNull { (at, name) ->
            val bar = assigned(name)
            assertEquals(.814f, bar.filledFraction, .005f)
            detector.accept(bar, at)
        }
        assertEquals(emptyList<ActiveHpBarDamageTickMeasurement>(), ticks)
    }

    @Test fun `full width recovery retains both real bars through blue sky`() {
        val image = ImageIO.read(requireNotNull(javaClass.getResource(
            "/battle/hp/2026-10-27-charm-blue/full-band-30.302922.png")))
        val found = BattlefieldHpTracker().measurePixels(672, 1419, 30_302_922_222L, regions,
            image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
        assertEquals(ActivePokemonSide.entries.toSet(), found.keys)
        val opponent = requireNotNull(found[ActivePokemonSide.OPPONENT])
        assertTrue(opponent.left in 335..336)
        assertEquals(527, opponent.right)
        assertEquals(.941f, opponent.filledFraction, .003f)
        assertEquals(1f, requireNotNull(found[ActivePokemonSide.PLAYER]).filledFraction, .015f)
    }

    @Test fun `unchanged white to blue plateau cannot create damage within the sample gap limit`() {
        val detector = ActiveHpBarDamageTickDetector()
        val white = assigned("bar-29.684933")
        val blue = assigned("bar-30.302922")
        assertEquals(null, detector.accept(white, 29_684_933_333L))
        assertEquals(null, detector.accept(blue, 30_302_922_222L))
        // Repeat only as a synthetic detector control; these times are not
        // additional observed video frames or asserted attack testimony.
        repeat(5) { index ->
            val bar = if (index % 2 == 0) white else blue
            assertEquals(null, detector.accept(bar, 30_402_922_222L + index * 100_000_000L))
        }
    }

    @Test fun `blue pointer interruption cannot turn a near full bar into its short left fragment`() {
        for (name in listOf("bar-36.005678", "bar-36.104333")) {
            // Source pixels contain a 4–5 px interruption followed by another
            // 128 px saturated segment. Neither endpoint is a readable HP loss.
            assertTrue(name, measurements(name).isEmpty())
        }
    }

    @Test fun `compact blue bar has real caps while missing or clipped caps remain rejected`() {
        fun measure(rightCap: Int?, cropped: Boolean = false, fillEnd: Int = 150): List<ActiveHpBarMeasurement> =
            ActiveHpBarMeasurer.measureAll(240, 120, 200) { x, y ->
                when {
                    (y == 40 || y == 50) && x in 20 until (if (cropped) 240 else 220) -> 0xff3388ee.toInt()
                    (x == 20 || x == rightCap) && y in 40..50 -> 0xff3388ee.toInt()
                    x in 23 until fillEnd && y in 42 until 49 -> 0xff1effbc.toInt()
                    else -> 0xff182825.toInt()
                }
            }
        val bar = measure(219).single()
        assertEquals(20, bar.left)
        assertEquals(220, bar.right)
        assertEquals(.65f, bar.filledFraction, .03f)
        assertEquals(.07f, measure(219, fillEnd = 36).single().filledFraction, .02f)
        assertTrue(measure(null).isEmpty())
        assertTrue(measure(null, cropped = true).isEmpty())
    }
}
