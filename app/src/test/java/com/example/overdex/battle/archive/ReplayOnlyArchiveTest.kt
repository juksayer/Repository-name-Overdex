package com.example.overdex.battle.archive

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReplayOnlyArchiveTest {
    private val crop = article("image", ArchivedCropCaptured(
        "artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64), 400,
        "image/png", "OpponentActiveSpeciesTextCrop", 1080, 2400, 800, 180, 1000, 220
    ))
    private val audio = article("sound", ArchivedAudioCaptured(
        "artifacts/audio/sha256/${"b".repeat(64)}.wav", "b".repeat(64), 600,
        "audio/wav", 16000, 1, 1000000000, "BATTLE_HUD"
    ))
    private val raw = article("ocr", ArchivedRawText("Raic u")).copy(
        evidenceReferences = listOf("image"), confidence = .55f, monotonicTimeNanos = 12000000000
    )
    private val original = MatchArchive(matchId = "match", articles = listOf(crop, audio, raw))

    @Test fun `replay only retains exact articles references and uncertainty without needing any media files`() {
        val replayOnly = original.forExport(MatchArchiveExportMode.TIMELINE_ONLY)
        assertEquals(original.articles, replayOnly.articles)
        assertFalse(replayOnly.mediaIncluded)
        val output = ByteArrayOutputStream()
        val manifest = MatchArchivePackageWriter.write(replayOnly, output)
        assertEquals(3, manifest.archiveFormatVersion)
        assertEquals(2, manifest.artifacts.size)
        assertFalse(manifest.mediaIncluded)
        assertEquals(listOf("manifest.json", "timeline.json"), entryNames(output.toByteArray()))
        for (policy in MatchArchivePackageReader.ArtifactPolicy.values()) {
            assertEquals(replayOnly, MatchArchivePackageReader.read(output.toByteArray().inputStream(), artifactPolicy = policy))
        }
        assertTrue(original.mediaIncluded)
    }

    @Test fun `reexport cannot claim omitted media was restored`() {
        val replayOnly = original.forExport(MatchArchiveExportMode.TIMELINE_ONLY)
        assertFalse(replayOnly.forExport(MatchArchiveExportMode.FULL_FORENSIC).mediaIncluded)
        assertFalse(replayOnly.forExport(MatchArchiveExportMode.COMPACT_CITED_EVIDENCE).mediaIncluded)
    }

    @Test fun `serialization agrees for streaming and string writers`() {
        val replayOnly = original.forExport(MatchArchiveExportMode.TIMELINE_ONLY)
        val stream = ByteArrayOutputStream()
        MatchArchiveSerializer.serializeTo(replayOnly, stream)
        assertEquals(replayOnly, MatchArchiveSerializer.deserialize(stream.toString("UTF-8")))
        assertEquals(replayOnly, MatchArchiveSerializer.deserialize(MatchArchiveSerializer.serialize(replayOnly)))
    }

    @Test fun `legacy archive still requires its image for full verification`() {
        val manifest = MatchArchiveManifest(
            matchId = "match", articleCount = 1,
            artifacts = listOf(ArchivedArtifactEntry(
                (crop.payload as ArchivedCropCaptured).artifactPath, "a".repeat(64), 400, "image/png"
            ))
        )
        val output = packageBytes(manifest, original.copy(articles = listOf(crop)))
        assertThrows(IllegalArgumentException::class.java) {
            MatchArchivePackageReader.read(output.inputStream(), artifactPolicy = MatchArchivePackageReader.ArtifactPolicy.IMPORT_AND_VERIFY)
        }
    }

    @Test fun `omission declaration must agree with timeline and require new format`() {
        val replayOnly = original.forExport(MatchArchiveExportMode.TIMELINE_ONLY)
        val output = ByteArrayOutputStream()
        val manifest = MatchArchivePackageWriter.write(replayOnly, output)
        for (invalid in listOf(manifest.copy(mediaIncluded = true), manifest.copy(archiveFormatVersion = 2))) {
            assertThrows(IllegalArgumentException::class.java) {
                MatchArchivePackageReader.read(packageBytes(invalid, replayOnly).inputStream())
            }
        }
    }

    @Test fun `replay only cannot smuggle source media past full verification`() {
        val replayOnly = original.forExport(MatchArchiveExportMode.TIMELINE_ONLY)
        val output = ByteArrayOutputStream()
        val manifest = MatchArchivePackageWriter.write(replayOnly, output)
        val bytes = packageBytes(manifest, replayOnly, (crop.payload as ArchivedCropCaptured).artifactPath)
        assertThrows(IllegalArgumentException::class.java) {
            MatchArchivePackageReader.read(bytes.inputStream(), artifactPolicy = MatchArchivePackageReader.ArtifactPolicy.IMPORT_AND_VERIFY)
        }
    }

    private fun packageBytes(manifest: MatchArchiveManifest, archive: MatchArchive, extra: String? = null): ByteArray =
        ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                mapOf("manifest.json" to Json.encodeToString(manifest), "timeline.json" to MatchArchiveSerializer.serialize(archive))
                    .forEach { (name, body) -> zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry() }
                extra?.let { zip.putNextEntry(ZipEntry(it)); zip.write(byteArrayOf(1)); zip.closeEntry() }
            }
        }.toByteArray()

    private fun entryNames(bytes: ByteArray) = ZipInputStream(bytes.inputStream()).use { zip ->
        buildList { while (true) { val entry = zip.nextEntry ?: break; add(entry.name); zip.closeEntry() } }
    }

    private fun article(id: String, payload: ArchivedTestimonyPayload) =
        ArchivedRealityArticle(id, "match", 100, 110, "witness", payload, emptyList(), null, null, null)
}
