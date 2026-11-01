package com.example.overdex.battle.preview

import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.RealityArticleArchiveMapper
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DirectoryCalibrationFrameTest {
    @Test fun `a frames prefix alone never establishes full source coverage`() {
        val trusted = article()
        assertNotNull(DirectoryCalibrationFrameReference.from(trusted))
        assertNull(DirectoryCalibrationFrameReference.from(trusted.copy(sourceId = "TEAM_SELECT_PLAYER_ROSTER_SLOT_1_CAPTURE")))
        val crop = trusted.payload as ArchivedCropCaptured
        assertNull(DirectoryCalibrationFrameReference.from(trusted.copy(payload = crop.copy(cropName = "PlayerHpEvidenceCrop"))))
        assertNull(DirectoryCalibrationFrameReference.from(trusted.copy(payload = crop.copy(
            artifactPath = "artifacts/crops/sha256/$HASH.png"))))
        assertNull(DirectoryCalibrationFrameReference.from(trusted.copy(payload = crop.copy(cropRight = 101))))
    }

    @Test fun `live and archive complete frame provenance agrees without borrowing current crop geometry`() {
        val live = RealityArticle(ArticleId("original"), 123, 999, SourceId("OPPONENT_HP_EVIDENCE_CAPTURE"),
            CropCaptured(CropArtifactReference("artifacts/frames/sha256/$HASH.png", HASH, 12),
                BattleCropProvenance("OpponentHpEvidenceCrop", 100, 200, BattleCropBounds(10, 20, 90, 80))),
            matchId = MatchId("battle"), monotonicTimeNanos = 100_000)
        val archived = RealityArticleArchiveMapper.createArchive(MatchId("battle"), listOf(live)).articles.single()
        val source = requireNotNull(DirectoryCalibrationFrameReference.from(live))
        assertEquals(source, DirectoryCalibrationFrameReference.from(archived))
        assertEquals("battle", source.matchId)
        assertEquals("original", source.articleId)
        assertEquals(123L, source.capturedAtMillis)
        assertEquals(BattleCropBounds(10, 20, 90, 80), source.provenance.bounds)
    }

    @Test fun `source bitmap dimensions must match full frame provenance instead of cropped bytes`() {
        val reference = requireNotNull(DirectoryCalibrationFrameReference.from(article()))
        assertTrue(DirectoryCalibrationFramePixels.validDimensions(100, 200, reference.provenance))
        assertFalse(DirectoryCalibrationFramePixels.validDimensions(80, 60, reference.provenance))
        assertFalse(DirectoryCalibrationFramePixels.validDimensions(200, 100, reference.provenance))
        assertFalse(DirectoryCalibrationFramePixels.validDimensions(0, 200))
        assertFalse(DirectoryCalibrationFramePixels.validDimensions(8192, 8192))
    }

    @Test fun `transparent masked atlases and partial opacity cannot become editable source territory`() {
        val opaque = Array(3) { IntArray(4) { 0xff121212.toInt() } }
        assertTrue(covered(opaque))
        opaque[1][2] = 0
        assertFalse(covered(opaque))
        opaque[1][2] = 0xfe121212.toInt()
        assertFalse(covered(opaque))
        // Even a one-pixel missing strip outside the selected historical crop
        // must remain unavailable rather than being panned into as real pixels.
        opaque[1][2] = 0xff121212.toInt()
        opaque[2][3] = 0
        assertFalse(covered(opaque))
    }

    @Test fun `saved reference reads stay bounded and inside the application repository`() {
        val parent = Files.createTempDirectory("directory-calibration-frame").toFile()
        try {
            val root = File(parent, "app").also { it.mkdirs() }
            val reference = File(root, "calibration_reference/latest_battle.png").also {
                requireNotNull(it.parentFile).mkdirs(); it.writeBytes(byteArrayOf(1, 2, 3))
            }
            assertArrayEquals(byteArrayOf(1, 2, 3), DirectoryCalibrationFrameLoader.readSavedReference(root, reference))
            val outside = File(parent, "outside.png").also { it.writeBytes(byteArrayOf(1)) }
            assertNull(DirectoryCalibrationFrameLoader.readSavedReference(root, outside))
            java.io.RandomAccessFile(reference, "rw").use { it.setLength(16L * 1024 * 1024 + 1) }
            assertNull(DirectoryCalibrationFrameLoader.readSavedReference(root, reference))
        } finally { parent.deleteRecursively() }
    }

    private fun covered(pixels: Array<IntArray>) = DirectoryCalibrationFramePixels.hasCompleteCoverage(
        pixels.first().size, pixels.size) { y, row -> pixels[y].copyInto(row) }

    private fun article() = ArchivedRealityArticle("frame", "battle", 123, 999,
        "OPPONENT_HP_EVIDENCE_CAPTURE", ArchivedCropCaptured("artifacts/frames/sha256/$HASH.png", HASH,
            12, "image/png", "OpponentHpEvidenceCrop", 100, 200, 10, 20, 90, 80), emptyList(), null, null, emptyList())

    private companion object { const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
}
