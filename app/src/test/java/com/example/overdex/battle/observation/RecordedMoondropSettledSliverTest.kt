package com.example.overdex.battle.observation

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional, read-only check of complete bars retained by the outcome crop. */
class RecordedMoondropSettledSliverTest {
    @Test fun `retained Vaporeon outcome crops preserve live sliver without inventing zero`() {
        val path = System.getenv("OVERDEX_SETTLED_SLIVER_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val file = File(requireNotNull(path))
        fun hash() = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val before = hash()
        assertEquals("2349f44eb481bb4b521253a4e9572647a003f5116f473a0177e38f91c90f8653", before)
        val samples = listOf(
            Triple("1681122f01b29cd19b271e02a7121f543118aa659a26cb356a2a3b68a754211a", 337, 531),
            Triple("56e675fde6d363bf8e8d6268a70999610dc9654ad66ee9a2998486dc1928b639", 338, 530),
        )
        ZipFile(file).use { zip ->
            samples.forEach { (sha, left, right) ->
                val bytes = zip.getInputStream(zip.getEntry("artifacts/crops/sha256/$sha.png")).readBytes()
                assertEquals(sha, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                val image = ImageIO.read(ByteArrayInputStream(bytes))
                val territory = BattleCropBounds(100, 641, 564, 858)
                val sample = requireNotNull(TrackedTerminalHpBarMeasurer.measureComplete(672, 1493,
                    ActiveHpBarMeasurement(left, 644, right, 663, .02f, .9f), territory) { x, y ->
                    image.getRGB(x - territory.left, y - territory.top)
                })
                println("retained $sha: $sample")
                assertTrue("The retained full bar contains surviving red", sample.liveColumns > 0)
                assertFalse("The red sliver cannot be treated as orange-only", sample.orangeOnly)
                assertNotEquals(HpBarFillColor.EMPTY, sample.hpSample?.fillColor)
            }
        }
        assertEquals(before, hash())
    }
}
