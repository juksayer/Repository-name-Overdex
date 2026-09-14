package com.example.overdex.battle.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MatchArchivePackageReaderTest {

    @Test
    fun `reads writer output unchanged and closes input`() {
        val original = sampleArchive()
        val output = ByteArrayOutputStream()
        MatchArchivePackageWriter.write(original, output)
        val input = TrackingInput(output.toByteArray())

        val restored = MatchArchivePackageReader.read(input)

        assertEquals(original, restored)
        assertTrue(input.wasClosed)
    }

    @Test
    fun `accepts timeline before manifest`() {
        val original = sampleArchive()
        val input = TrackingInput(
            packageBytes(
                "timeline.json" to MatchArchiveSerializer.serialize(original),
                "manifest.json" to manifestJson(articleCount = 1)
            )
        )

        val restored = MatchArchivePackageReader.read(input)

        assertEquals(original, restored)
        assertTrue(input.wasClosed)
    }

    @Test
    fun `rejects incorrect article count and closes input`() {
        val input = TrackingInput(
            packageBytes(
                "manifest.json" to manifestJson(articleCount = 2),
                "timeline.json" to MatchArchiveSerializer.serialize(sampleArchive())
            )
        )

        try {
            MatchArchivePackageReader.read(input)
            fail("Expected article count mismatch to be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                error.message.orEmpty().contains("Article count mismatch")
            )
        }

        assertTrue(input.wasClosed)
    }

    @Test
    fun `packages verifies and restores referenced crop artifacts`() {
        val bytes = byteArrayOf(9, 8, 7, 6)
        val hash = sha256(bytes)
        val path = "artifacts/crops/sha256/$hash.png"
        val archive = MatchArchive(
            matchId = "match-a",
            articles = listOf(
                ArchivedRealityArticle(
                    articleId = "crop-1", matchId = "match-a", perceivedAt = 1L, recordedAt = 1L,
                    sourceId = "COUNTDOWN_CROP_CAPTURE",
                    payload = ArchivedCropCaptured(path, hash, bytes.size.toLong(), "image/png", "CountdownGlyphCrop", 1080, 2400, 1, 2, 3, 4),
                    predecessorIds = emptyList(), confidence = null, sequenceNumber = 1L, evidenceReferences = emptyList()
                )
            )
        )
        val sourceRoot = Files.createTempDirectory("crop-source-").toFile()
        val restoredRoot = Files.createTempDirectory("crop-restored-").toFile()
        try {
            val source = java.io.File(sourceRoot, path)
            source.parentFile!!.mkdirs()
            source.writeBytes(bytes)
            val output = ByteArrayOutputStream()
            MatchArchivePackageWriter.write(archive, output, sourceRoot)

            assertEquals(archive, MatchArchivePackageReader.read(ByteArrayInputStream(output.toByteArray()), restoredRoot))
            assertTrue(java.io.File(restoredRoot, path).isFile)
            assertEquals(hash, sha256(java.io.File(restoredRoot, path).readBytes()))
        } finally {
            sourceRoot.deleteRecursively()
            restoredRoot.deleteRecursively()
        }
    }

    private fun sampleArchive() = MatchArchive(
        matchId = "match-a",
        articles = listOf(
            ArchivedRealityArticle(
                articleId = "article-1",
                matchId = "match-a",
                perceivedAt = 100L,
                recordedAt = 110L,
                sourceId = "SPECIES_WITNESS",
                payload = ArchivedPokemonIdentified("Flabébé"),
                predecessorIds = listOf("outside-this-package"),
                confidence = 0.85f,
                sequenceNumber = 17L,
                evidenceReferences = listOf("frame-17")
            )
        )
    )

    private fun manifestJson(articleCount: Int) = """
        {
          "archiveFormatVersion": 1,
          "archiveType": "overdex-match-archive",
          "matchId": "match-a",
          "timelineEntry": "timeline.json",
          "articleCount": $articleCount
        }
    """.trimIndent()

    private fun packageBytes(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class TrackingInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var wasClosed = false
            private set

        override fun close() {
            wasClosed = true
            super.close()
        }
    }
}
