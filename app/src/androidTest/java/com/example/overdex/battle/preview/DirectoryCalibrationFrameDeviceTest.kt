package com.example.overdex.battle.preview

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.ArchivedRealityArticle
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Source fidelity checks use real Android PNG decoding, including atlas alpha. */
@RunWith(AndroidJUnit4::class)
class DirectoryCalibrationFrameDeviceTest {
    @Test fun foreignCapturedFrameAndForeignSavedFallbackCannotBecomePixelEditBackgrounds() = withRoot { root ->
        val source = Bitmap.createBitmap(672, 1493, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val reference = save(root, source, 672, 1493)
        saveReference(root, source)
        val savedBytes = File(root, "calibration_reference/latest_battle.png").readBytes()
        val pixel = DirectoryCalibrationScreenGeometry(1080, 2280)

        assertNull(DirectoryCalibrationFrameLoader.load(root, reference, null, pixel))
        assertNull(DirectoryCalibrationFrameLoader.load(root, null, null, pixel))
        assertNull(DirectoryCalibrationFrameLoader.load(root, reference, null, DirectoryCalibrationScreenGeometry(0, 0)))
        assertArrayEquals("Rejecting a foreign image must not rewrite its pixels or user settings", savedBytes,
            File(root, "calibration_reference/latest_battle.png").readBytes())
        source.recycle()
    }

    @Test fun downscaledPixelReferenceIsAcceptedOnlyForItsOwnDisplayShape() = withRoot { root ->
        val source = Bitmap.createBitmap(672, 1419, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        saveReference(root, source)
        val pixel = DirectoryCalibrationScreenGeometry(1080, 2280)
        val loaded = requireNotNull(DirectoryCalibrationFrameLoader.load(root, null, null, pixel))

        assertTrue(loaded.isEditableOn(pixel))
        assertFalse(loaded.isEditableOn(DirectoryCalibrationScreenGeometry(1080, 2400)))
        assertFalse(loaded.copy(sourceGeometry = null).isEditableOn(pixel))
        assertEquals(DirectoryCalibrationScreenGeometry(672, 1419), loaded.sourceGeometry)
        assertNull(DirectoryCalibrationFrameLoader.load(root, null, null, DirectoryCalibrationScreenGeometry(1080, 2400)))
        loaded.bitmap.recycle(); source.recycle()
    }

    @Test fun trustedCaptureLoadsTheEntireVerifiedSourceInsteadOfOnlyItsHistoricalCrop() = withRoot { root ->
        val source = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE); setPixel(95, 190, Color.RED)
        }
        val reference = save(root, source)
        val loaded = requireNotNull(DirectoryCalibrationFrameLoader.load(root, reference, null, DirectoryCalibrationScreenGeometry(100, 200)))
        assertEquals(DirectoryCalibrationFrameOrigin.RECORDED_MATCH_FRAME, loaded.origin)
        assertEquals(100, loaded.bitmap.width)
        assertEquals(200, loaded.bitmap.height)
        assertEquals("Pixels beyond the old crop remain real source pixels", Color.RED, loaded.bitmap.getPixel(95, 190))
        assertEquals("battle", loaded.matchId)
        assertEquals("recorded", loaded.articleId)
        assertEquals(123L, loaded.capturedAtMillis)
        loaded.bitmap.recycle(); source.recycle()
    }

    @Test fun partialAtlasFallsBackToClearlyUnattributedSavedReference() = withRoot { root ->
        val atlas = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888).apply {
            for (y in 20 until 80) for (x in 10 until 90) setPixel(x, y, Color.GREEN)
        }
        val reference = save(root, atlas)
        val saved = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        saveReference(root, saved)
        val loaded = requireNotNull(DirectoryCalibrationFrameLoader.load(root, reference, null, DirectoryCalibrationScreenGeometry(100, 200)))
        assertEquals(DirectoryCalibrationFrameOrigin.SAVED_BATTLE_REFERENCE, loaded.origin)
        assertEquals(80, loaded.bitmap.width)
        assertEquals(Color.YELLOW, loaded.bitmap.getPixel(79, 159))
        assertNull(loaded.matchId); assertNull(loaded.articleId); assertNull(loaded.capturedAtMillis)
        loaded.bitmap.recycle(); saved.recycle(); atlas.recycle()
    }

    @Test fun aCropSizedPngCannotClaimItsMissingSurroundingsAsAFullSource() = withRoot { root ->
        val cropped = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val reference = save(root, cropped)
        assertNull(DirectoryCalibrationFrameLoader.load(root, reference, null, DirectoryCalibrationScreenGeometry(100, 200)))
        cropped.recycle()
    }

    private fun save(root: File, bitmap: Bitmap, sourceWidth: Int = 100, sourceHeight: Int = 200): DirectoryCalibrationFrameReference {
        val bytes = png(bitmap)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val path = "artifacts/frames/sha256/$hash.png"
        File(root, path).also { requireNotNull(it.parentFile).mkdirs(); it.writeBytes(bytes) }
        return requireNotNull(DirectoryCalibrationFrameReference.from(ArchivedRealityArticle("recorded", "battle", 123, 999,
            "OPPONENT_HP_EVIDENCE_CAPTURE", ArchivedCropCaptured(path, hash, bytes.size.toLong(), "image/png",
                "OpponentHpEvidenceCrop", sourceWidth, sourceHeight, 10, 20, 90, 80), emptyList(), null, null, emptyList())))
    }

    private fun saveReference(root: File, bitmap: Bitmap) {
        File(root, "calibration_reference/latest_battle.png").also {
            requireNotNull(it.parentFile).mkdirs(); it.writeBytes(png(bitmap))
        }
    }

    private fun png(bitmap: Bitmap) = ByteArrayOutputStream().use {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.toByteArray()
    }

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("directory-calibration-native").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
