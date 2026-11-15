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
}
