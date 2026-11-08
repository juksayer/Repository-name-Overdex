package com.example.overdex.battle.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.BattleCalibrationReferenceStore
import com.example.overdex.data.CalibrationScreenshotLibrary
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CancellationException

enum class DirectoryCalibrationFrameOrigin { RECORDED_MATCH_FRAME, SAVED_BATTLE_REFERENCE }

/** The device's full display bounds, independent of application insets or source downscaling. */
data class DirectoryCalibrationScreenGeometry(val width: Int, val height: Int) {
    val isKnown: Boolean get() = width > 0 && height > 0

    fun accepts(sourceWidth: Int, sourceHeight: Int): Boolean = isKnown &&
        CalibrationScreenshotLibrary.sameShape(width, height, sourceWidth, sourceHeight)
}

/** A complete source image, never a cropped image stretched into new territory. */
data class DirectoryCalibrationFrame(
    val bitmap: Bitmap,
    val origin: DirectoryCalibrationFrameOrigin,
    val matchId: String? = null,
    val articleId: String? = null,
    val capturedAtMillis: Long? = null,
    /** Actual capture dimensions; absence never grants permission to edit this phone's crops. */
    val sourceGeometry: DirectoryCalibrationScreenGeometry? = null,
) {
    fun isEditableOn(screen: DirectoryCalibrationScreenGeometry): Boolean =
        !bitmap.isRecycled && sourceGeometry?.let { source ->
            screen.accepts(source.width, source.height) && source.accepts(bitmap.width, bitmap.height)
        } == true
}

internal data class DirectoryCalibrationFrameReference(
    val matchId: String,
    val articleId: String,
    val capturedAtMillis: Long,
    val artifact: CropArtifactReference,
    val provenance: BattleCropProvenance,
) {
    companion object {
        // This capture explicitly supplies sourceFrame to preserveFrameCrop.
        // An arbitrary artifacts/frames PNG may instead be a partial crop atlas.
        private const val COMPLETE_FRAME_SOURCE = "OPPONENT_HP_EVIDENCE_CAPTURE"

        fun from(article: RealityArticle): DirectoryCalibrationFrameReference? {
            val crop = article.payload as? CropCaptured ?: return null
            val match = article.matchId?.value ?: return null
            return create(match, article.id.value, article.perceivedAt, article.sourceId.id,
                crop.artifact, crop.cropProvenance)
        }

        fun from(article: ArchivedRealityArticle): DirectoryCalibrationFrameReference? {
            val crop = article.payload as? ArchivedCropCaptured ?: return null
            return create(article.matchId, article.articleId, article.perceivedAt, article.sourceId,
                CropArtifactReference(crop.artifactPath, crop.sha256, crop.byteCount, crop.mediaType),
                BattleCropProvenance(crop.cropName, crop.sourceWidth, crop.sourceHeight,
                    BattleCropBounds(crop.cropLeft, crop.cropTop, crop.cropRight, crop.cropBottom)))
        }

        private fun create(match: String, id: String, at: Long, source: String,
            artifact: CropArtifactReference, provenance: BattleCropProvenance): DirectoryCalibrationFrameReference? {
            val bounds = provenance.bounds
            if (source != COMPLETE_FRAME_SOURCE || provenance.cropName != "OpponentHpEvidenceCrop" ||
                !artifact.relativePath.startsWith("artifacts/frames/") ||
                !DirectoryCalibrationFramePixels.validDimensions(provenance.sourceWidth, provenance.sourceHeight) ||
                bounds.left < 0 || bounds.top < 0 || bounds.right > provenance.sourceWidth ||
                bounds.bottom > provenance.sourceHeight || bounds.right <= bounds.left || bounds.bottom <= bounds.top) return null
            return DirectoryCalibrationFrameReference(match, id, at, artifact, provenance)
        }
    }
}

/** Pure geometry/coverage checks also reject legacy transparent crop atlases. */
internal object DirectoryCalibrationFramePixels {
    fun validDimensions(width: Int, height: Int, expected: BattleCropProvenance? = null): Boolean =
        width > 0 && height > 0 && width.toLong() * height <= 16_777_216L &&
            (expected == null || (width == expected.sourceWidth && height == expected.sourceHeight))

    fun hasCompleteCoverage(width: Int, height: Int, readRow: (Int, IntArray) -> Unit): Boolean {
        if (!validDimensions(width, height)) return false
        val row = IntArray(width)
        for (y in 0 until height) {
            readRow(y, row)
            if (row.any { it ushr 24 != 255 }) return false
        }
        return true
    }
}

internal object DirectoryCalibrationFrameLoader {
    private const val MAX_IMAGE_BYTES = 16L * 1024 * 1024

    fun load(repositoryRoot: File, reference: DirectoryCalibrationFrameReference?, archiveFile: File?,
        screen: DirectoryCalibrationScreenGeometry): DirectoryCalibrationFrame? {
        if (!screen.isKnown) return null
        if (reference != null && screen.accepts(reference.provenance.sourceWidth, reference.provenance.sourceHeight)) {
            val bytes = DirectoryCropArchiveBytes.readLocal(repositoryRoot, reference.artifact)
                ?: archiveFile?.let { DirectoryCropArchiveBytes.read(it, listOf(reference.artifact))[reference.artifact.relativePath] }
            val bitmap = bytes?.let { decodeComplete(it, screen, reference.provenance) }
            if (bitmap != null) return DirectoryCalibrationFrame(bitmap, DirectoryCalibrationFrameOrigin.RECORDED_MATCH_FRAME,
                reference.matchId, reference.articleId, reference.capturedAtMillis,
                DirectoryCalibrationScreenGeometry(reference.provenance.sourceWidth, reference.provenance.sourceHeight))
        }
        val saved = BattleCalibrationReferenceStore(repositoryRoot).latest()
        val bitmap = saved?.let { readSavedReference(repositoryRoot, it) }?.let { decodeComplete(it, screen) } ?: return null
        // The reference store has no immutable match/time association. Do not
        // borrow the current preview's attribution for a different saved image.
        return DirectoryCalibrationFrame(bitmap, DirectoryCalibrationFrameOrigin.SAVED_BATTLE_REFERENCE,
            sourceGeometry = DirectoryCalibrationScreenGeometry(bitmap.width, bitmap.height))
    }

    internal fun readSavedReference(repositoryRoot: File, file: File): ByteArray? = try {
        if (!file.canonicalFile.toPath().startsWith(repositoryRoot.canonicalFile.toPath()) ||
            !file.isFile || file.length() !in 1..MAX_IMAGE_BYTES) null
        else file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var remaining = MAX_IMAGE_BYTES + 1
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
            output.toByteArray().takeIf { it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }

    private fun decodeComplete(bytes: ByteArray, screen: DirectoryCalibrationScreenGeometry,
        expected: BattleCropProvenance? = null): Bitmap? {
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
        if (!DirectoryCalibrationFramePixels.validDimensions(dimensions.outWidth, dimensions.outHeight, expected) ||
            !screen.accepts(dimensions.outWidth, dimensions.outHeight)) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val complete = !bitmap.hasAlpha() || DirectoryCalibrationFramePixels.hasCompleteCoverage(bitmap.width, bitmap.height) { y, row ->
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
        if (complete) return bitmap
        bitmap.recycle()
        return null
    }
}
