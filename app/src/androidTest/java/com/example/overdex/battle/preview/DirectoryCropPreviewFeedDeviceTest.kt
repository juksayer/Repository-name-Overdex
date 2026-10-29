package com.example.overdex.battle.preview

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectoryCropPreviewFeedDeviceTest {
    @Test fun frameArtifactUsesExactRecordedRectangleAndPreservesPixelsAfterCollectionStops() = runBlocking {
        withRoot { root ->
            val original = patternedBitmap(8, 6)
            val bounds = BattleCropBounds(2, 1, 6, 4)
            val expected = pixels(original, bounds.left, bounds.top, 4, 3)
            val artifact = savePng(root, "frames", original)
            original.recycle()

            val state = firstPreview(root, listOf(crop("frame-crop", artifact,
                "PlayerHpEvidenceCrop", 8, 6, bounds)))
            val image = state.crops.getValue(DirectoryCropSlot.PLAYER_HP)
            val preview = requireNotNull(image.bitmap)
            assertEquals(4, preview.width)
            assertEquals(3, preview.height)
            assertEquals(CAPTURE_TIME, image.capturedAtMillis)
            assertEquals("frame-crop", image.articleId)
            assertArrayEquals(expected, pixels(preview))
            // first() has cancelled collection. Its published image must remain
            // usable by the LCD; neither upstream cancellation nor file cleanup
            // owns this independently decoded bitmap.
            delay(50)
            assertFalse(preview.isRecycled)
            assertArrayEquals(expected, pixels(preview))
        }
    }

    @Test fun fullSourceAndStandaloneImagesStayOwnedWhileWrongStandaloneGeometryIsUnavailable() = runBlocking {
        withRoot { root ->
            val original = patternedBitmap(4, 3)
            val expected = pixels(original)
            val frame = savePng(root, "frames", original)
            val standalone = savePng(root, "crops", original)
            original.recycle()

            val state = firstPreview(root, listOf(
                crop("whole-frame", frame, "PlayerHpEvidenceCrop", 4, 3, BattleCropBounds(0, 0, 4, 3)),
                // A standalone PNG is already cropped. The absolute rectangle
                // locates its origin but must not be applied to the image again.
                crop("standalone", standalone, "OpponentHpEvidenceCrop", 1080, 2400,
                    BattleCropBounds(100, 200, 104, 203)),
                crop("wrong-size", standalone, "AnnouncementCrop", 1080, 2400,
                    BattleCropBounds(100, 200, 104, 204)),
            ))
            val full = requireNotNull(state.crops.getValue(DirectoryCropSlot.PLAYER_HP).bitmap)
            val separate = requireNotNull(state.crops.getValue(DirectoryCropSlot.OPPONENT_HP).bitmap)
            // Android may return the source object for a full-bounds createBitmap.
            // This verifies that the decoder does not recycle that returned image.
            assertFalse(full.isRecycled)
            assertFalse(separate.isRecycled)
            assertNotSame(full, separate)
            assertArrayEquals(expected, pixels(full))
            assertArrayEquals(expected, pixels(separate))
            val missing = state.crops.getValue(DirectoryCropSlot.ANNOUNCEMENT)
            assertEquals("wrong-size", missing.articleId)
            assertEquals(CAPTURE_TIME, missing.capturedAtMillis)
            assertNull(missing.bitmap)
            delay(50)
            assertFalse(full.isRecycled)
            assertArrayEquals(expected, pixels(full))
        }
    }

    private suspend fun firstPreview(root: File, articles: List<RealityArticle>): DirectoryCropPreviewState {
        val timeline = InMemoryRealityTimeline().also { ledger -> articles.forEach(ledger::append) }
        val source = MatchArchiveSource(MatchId(MATCH_ID), timeline)
        val feed = DirectoryCropPreviewFeed(root, sources = { listOf(source) })
        return withTimeout(5_000) { feed.states().first { it.matchId == MATCH_ID && !it.isLoading } }
    }

    private fun patternedBitmap(width: Int, height: Int) = Bitmap.createBitmap(width, height,
        Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) {
            setPixel(x, y, Color.rgb(x * 31, y * 37, (x + y) * 17))
        }
    }

    private fun pixels(bitmap: Bitmap, x: Int = 0, y: Int = 0,
        width: Int = bitmap.width, height: Int = bitmap.height): IntArray =
        IntArray(width * height).also { bitmap.getPixels(it, 0, width, x, y, width, height) }

    private fun savePng(root: File, kind: String, bitmap: Bitmap): CropArtifactReference {
        val bytes = ByteArrayOutputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            it.toByteArray()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val path = "artifacts/$kind/sha256/$hash.png"
        File(root, path).also { it.parentFile!!.mkdirs() }.writeBytes(bytes)
        return CropArtifactReference(path, hash, bytes.size.toLong())
    }

    private fun crop(id: String, artifact: CropArtifactReference, name: String,
        width: Int, height: Int, bounds: BattleCropBounds): RealityArticle = RealityArticle(
        id = ArticleId(id), perceivedAt = CAPTURE_TIME, recordedAt = CAPTURE_TIME + 100,
        sourceId = SourceId("PREVIEW_DEVICE_TEST"),
        payload = CropCaptured(artifact, BattleCropProvenance(name, width, height, bounds)),
        matchId = MatchId(MATCH_ID), monotonicTimeNanos = 1_000_000_000L,
    )

    private suspend fun withRoot(block: suspend (File) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val root = Files.createTempDirectory(cache.toPath(), "directory-crop-device-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    private companion object {
        const val MATCH_ID = "directory-preview-device-test"
        const val CAPTURE_TIME = 1_793_300_000_000L
    }
}
