package com.example.overdex.battle.observation

import java.io.File
import java.security.MessageDigest
import java.util.Properties
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Native-resolution video crops; reference material remains on the backup drive. */
class RecordedMoondropFullBarTransitionTest {
    @Test fun `recorded complete bars and one-pixel survivors remain distinct`() {
        val path = System.getenv("OVERDEX_SETTLED_BAR_FRAMES")
        assumeTrue(!path.isNullOrBlank())
        val root = File(requireNotNull(path))
        val samples = listOf(
            "sealeo-10332592" to "ef941fd5f7b8d024b5c10fda3694c555ff21143d6a1c4b691f11da74f621ba63",
            "sealeo-10467142" to "a8e6a4f411b14f896cfca665e4492e4da0d80ccc6edbd122d24ae6298a6ac2d6",
            "vaporeon-15070162" to "c2089923b8f88d1dc62ddc13caa6c76d5ba0c69cc5c697c1dc20cb667124812c",
            "vaporeon-15084291" to "07a71ecb4b4b9aff2c6ede8b628d79f3f298cab724dc5975c09b7492ddb2db2c",
        )
        samples.forEachIndexed { index, (name, expected) ->
            val file = File(root, "$name.png")
            val bytes = file.readBytes()
            assertEquals(expected, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            val info = Properties().apply { File(root, "$name.properties").inputStream().use(::load) }
            fun n(key: String) = info.getProperty(key).toInt()
            val image = ImageIO.read(file)
            fun measure(inject: Boolean) = TrackedTerminalHpBarMeasurer.measureComplete(n("sourceWidth"), n("sourceHeight"),
                ActiveHpBarMeasurement(n("left"), n("top"), n("right"), n("bottom"), .02f, .9f)) { x, y ->
                assertTrue(x in n("cropLeft") until n("cropRight") && y in n("cropTop") until n("cropBottom"))
                if (inject && x == n("left") + 8 && y == (n("top") + n("bottom")) / 2) 0xffe84d40.toInt()
                else image.getRGB(x - n("cropLeft"), y - n("cropTop"))
            }
            val original = requireNotNull(measure(false))
            println("native $name: $original")
            if (index % 2 == 0) assertTrue("Visible red must remain live-colored", original.liveColumns > 0)
            assertNotEquals(HpBarFillColor.EMPTY, original.hpSample?.fillColor)
            val survivor = requireNotNull(measure(true))
            assertFalse("One synthetic live pixel must prevent orange-only", survivor.orangeOnly)
            assertTrue(survivor.liveColumns > 0 || survivor.unmaskedUncertainColumns > 0)
            assertNotEquals(HpBarFillColor.EMPTY, survivor.hpSample?.fillColor)
            assertEquals(expected, MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
        }
    }
}
