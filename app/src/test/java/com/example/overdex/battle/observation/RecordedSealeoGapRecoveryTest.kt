package com.example.overdex.battle.observation

import java.util.Properties
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

/** Original video pixels in the gap between archived red and empty observations. */
class RecordedSealeoGapRecoveryTest {
    @Test fun `recovered pixel crops retain their video provenance`() {
        val hashes = mapOf(
            "red" to "795edcee1a183228fbf999cf95e6f62ceb633d19730aea97ff0b922fd828823e",
            "orange" to "22be3e002fee4830ec60bd1d0d5ecd3f1d63d133490433f65e8d21de490491ff",
            "orange-later" to "72abd5c1d91916d07110b14e17d2c239a600b1a00654f72799fcdad36eeb8ebd",
            "background-later" to "394d108db3e43338c9c2c7ef919a30a667f821d05f1a20679bdb48e68fa71f90",
        )
        for ((name, expected) in hashes) {
            val bytes = requireNotNull(javaClass.getResourceAsStream("/battle/hp/2026-11-14-sealeo-recovered/$name.png")).readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(expected, actual)
        }
    }

    private fun read(name: String, pixel: (Int, Int, Int) -> Int = { _, _, c -> c }): CompleteHpBarSample? {
        val base = "/battle/hp/2026-11-14-sealeo-recovered/$name"
        val p = Properties().apply { load(requireNotNull(this@RecordedSealeoGapRecoveryTest.javaClass.getResourceAsStream("$base.properties"))) }
        fun n(key: String) = p.getProperty(key).toInt()
        val image = ImageIO.read(requireNotNull(javaClass.getResource("$base.png")))
        return TrackedTerminalHpBarMeasurer.measureComplete(n("sourceWidth"), n("sourceHeight"),
            ActiveHpBarMeasurement(n("left"), n("top"), n("right"), n("bottom"), .1f, .9f)) { x, y ->
            check(x in n("cropLeft") until n("cropRight") && y in n("cropTop") until n("cropBottom"))
            pixel(x, y, image.getRGB(x - n("cropLeft"), y - n("cropTop")))
        }
    }

    @Test fun `recovered whole bars reveal transition without inventing an empty measurement`() {
        val red = requireNotNull(read("red"))
        assertTrue(red.liveColumns > 0)
        assertFalse(red.orangeOnly)
        for (name in listOf("orange", "orange-later")) {
            val sample = requireNotNull(read(name))
            assertTrue("Recorded orange damage pixels remain available", sample.damageColumns > 0)
            assertTrue("Colored background remains unresolved", sample.unmaskedUncertainColumns > 0)
            assertFalse("The whole bar is not conclusively orange", sample.orangeOnly)
            assertNull("Orange does not invent a measured zero", sample.hpSample)
        }
    }

    @Test fun `a tiny live pixel in either recovered orange bar remains protected`() {
        for ((name, y) in listOf("orange" to 738, "orange-later" to 732)) {
            for (color in listOf(0xffe84d40.toInt(), 0xffe8db40.toInt(), 0xff40e8ab.toInt())) {
                val sample = requireNotNull(read(name) { x, py, original ->
                    if (x == 600 && py == y) color else original
                })
                assertTrue("A single bright surviving pixel is retained", sample.liveColumns > 0)
                assertFalse(sample.orangeOnly)
                assertNotEquals(HpBarFillColor.EMPTY, sample.hpSample?.fillColor)
            }
        }
    }

    @Test fun `one live pixel at the left of the orange trail blocks orange only`() {
        for ((name, y) in listOf("orange" to 738, "orange-later" to 732)) {
            for (color in listOf(0xffe84d40.toInt(), 0xffe8db40.toInt(), 0xff40e8ab.toInt())) {
                val sample = requireNotNull(read(name) { x, py, original ->
                    if (x == 473 && py == y) color else original
                })
                assertTrue("The surviving pixel on the left is retained", sample.liveColumns > 0)
                assertTrue("The orange trail remains visible", sample.damageColumns > 0)
                assertFalse(sample.orangeOnly)
                assertNotEquals(HpBarFillColor.EMPTY, sample.hpSample?.fillColor)
            }
        }
    }

    @Test fun `nonlethal Sealeo sliver survives a stray orange column on its left`() {
        val base = "/battle/hp/2026-11-13-complete-bar/p_sealeo_nonlethal_charge-41.299011"
        val image = ImageIO.read(requireNotNull(javaClass.getResource("$base.png")))
        val p = Properties().apply { load(requireNotNull(this@RecordedSealeoGapRecoveryTest.javaClass.getResourceAsStream("$base.properties"))) }
        fun n(key: String) = p.getProperty(key).toInt()
        val anchor = ActiveHpBarMeasurement(n("left"), n("top"), n("right"), n("bottom"), .1f, .9f)
        fun pixel(x: Int, y: Int) = image.getRGB(x - n("cropLeft"), y - n("cropTop"))
        val sample = requireNotNull(TrackedTerminalHpBarMeasurer.measureComplete(
            n("sourceWidth"), n("sourceHeight"), anchor, pixelAt = ::pixel))
        assertTrue("The real red survivor is preserved", sample.liveColumns > 0)
        assertTrue("The real damage trail is present", sample.damageColumns > 0)
        assertFalse("A surviving Sealeo cannot be orange-only", sample.orangeOnly)
        val tracker = CompleteHpBarTrack()
        val side = com.example.overdex.battle.custody.ActivePokemonSide.OPPONENT
        tracker.accept(side, sample.copy(damageColumns = 0), 1, 0)
        val mixed = requireNotNull(tracker.accept(side, sample, 50_000_001, 0))
        assertEquals("LIVE_COLOR", mixed.status)
        assertTrue(mixed.sample.liveColumns > 0 && mixed.sample.damageColumns > 0)
        assertNull("Damage color does not start an orange-only candidate", mixed.candidateSince)

        // Reproduce the exploratory median-per-column test on the original
        // source rectangle; a lone orange-colored column precedes the red.
        val inset = maxOf(3, kotlin.math.round((n("bottom") - n("top")) * .15).toInt())
        var firstLive: Int? = null
        var firstOrange: Int? = null
        for (x in (n("left") + inset) until (n("right") - inset)) {
            val values = (n("top") + inset until n("bottom") - inset).map { pixel(x, it) }
            fun median(shift: Int): Int {
                val sorted = values.map { it ushr shift and 255 }.sorted()
                return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
            }
            val hsv = java.awt.Color.RGBtoHSB(median(16), median(8), median(0), null)
            val hue = hsv[0] * 180f
            if (hsv[1] * 255 >= 100 && hsv[2] * 255 >= 160) {
                if (hue >= 8f && hue < 24f && firstOrange == null) firstOrange = x
                if ((hue < 8f || hue >= 174f || hue >= 24f && hue < 95f) && firstLive == null) firstLive = x
            }
        }
        assertTrue("One orange pixel-column before live fill must not erase the survivor",
            requireNotNull(firstOrange) < requireNotNull(firstLive))
    }

    @Test fun `a dim surviving pixel can match background and must stay unresolved`() {
        val reference = ImageIO.read(requireNotNull(javaClass.getResource(
            "/battle/hp/2026-11-14-sealeo-recovered/background-later.png")))
        // The fitted multipliers are a diagnostic for these specific video frames,
        // not a general classifier or evidence that this later reference was live.
        val examples = listOf(
            Triple("orange", 733, 0xff4f562b.toInt()) to doubleArrayOf(.50588235, .46853147, .45833333),
            Triple("orange-later", 727, 0xff3c5a42.toInt()) to doubleArrayOf(.47311828, .48046756, .47027027),
        )
        for ((spec, multipliers) in examples) {
            val (name, y, survivor) = spec
            val referencePixel = reference.getRGB(503 - 450, y - 690)
            for ((i, shift) in listOf(16, 8, 0).withIndex()) {
                val observed = survivor ushr shift and 255
                val background = (referencePixel ushr shift and 255) * multipliers[i]
                assertTrue("Dim live color can look like background", kotlin.math.abs(observed - background) <= 20)
            }
            fun modified(inject: Boolean) = read(name) { x, py, original ->
                when {
                    x == 503 && py == y && inject -> survivor
                    x == 503 && py in y..(y + 9) -> 0xff333333.toInt()
                    else -> original
                }
            }!!
            val cleanColumn = modified(false)
            val survivorColumn = modified(true)
            assertTrue("The protected one-pixel uncertainty must be retained",
                survivorColumn.unmaskedUncertainColumns > cleanColumn.unmaskedUncertainColumns)
            assertFalse(survivorColumn.orangeOnly)
            assertNotEquals(HpBarFillColor.EMPTY, survivorColumn.hpSample?.fillColor)
        }
    }
}
