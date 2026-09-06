package com.example.overdex.battle.archive

import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
import java.nio.file.Files
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchArchivePackageWriterTest {

    @Test
    fun `package contains manifest and preserves timeline archive`() {
        val archive = MatchArchive(
            matchId = "match-a",
            articles = listOf(
                ArchivedRealityArticle(
                    articleId = "article-1",
                    matchId = "match-a",
                    perceivedAt = 100L,
                    recordedAt = 110L,
                    sourceId = "SPECIES_WITNESS",
                    payload = ArchivedPokemonIdentified("Flabébé"),
                    predecessorIds = listOf("predecessor-1"),
                    confidence = 0.85f,
                    sequenceNumber = 17L,
                    evidenceReferences = listOf("frame-17")
                )
            )
        )

        val output = ByteArrayOutputStream()
        val returnedManifest = MatchArchivePackageWriter.write(archive, output)

        val file = Files.createTempFile("match-archive-", ".odxmatch").toFile()
        try {
            file.writeBytes(output.toByteArray())

            ZipFile(file).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toList()
                assertEquals(
                    listOf("manifest.json", "timeline.json"),
                    names
                )

                val manifestText = zip.getInputStream(
                    zip.getEntry("manifest.json")
                ).bufferedReader(Charsets.UTF_8).use { it.readText() }

                val timelineText = zip.getInputStream(
                    zip.getEntry("timeline.json")
                ).bufferedReader(Charsets.UTF_8).use { it.readText() }

                val storedManifest =
                    Json.decodeFromString<MatchArchiveManifest>(manifestText)

                assertEquals(returnedManifest, storedManifest)
                assertEquals(1, storedManifest.archiveFormatVersion)
                assertEquals("overdex-match-archive", storedManifest.archiveType)
                assertEquals("match-a", storedManifest.matchId)
                assertEquals("timeline.json", storedManifest.timelineEntry)
                assertEquals(1, storedManifest.articleCount)

                assertEquals(
                    archive,
                    MatchArchiveSerializer.deserialize(timelineText)
                )
            }
        } finally {
            file.delete()
        }
    }
}