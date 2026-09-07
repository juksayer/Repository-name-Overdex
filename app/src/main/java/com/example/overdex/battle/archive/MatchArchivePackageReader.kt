package com.example.overdex.battle.archive

import com.example.overdex.battle.archive.MatchArchivePackageWriter.MANIFEST_ENTRY_NAME
import com.example.overdex.battle.archive.MatchArchivePackageWriter.TIMELINE_ENTRY_NAME
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

/**
 * Reader for decoding and validating `.odxmatch` ZIP archives.
 *
 * This component enforces strict schema compliance, data integrity, and resource
 * constraints during the decoding process.
 */
object MatchArchivePackageReader {

    private const val MAX_TOTAL_BYTES = 16 * 1024 * 1024 // 16 MiB

    private val manifestJson = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Reads a [MatchArchive] from the provided [InputStream] ZIP container.
     *
     * This method closes the supplied [input] stream upon completion or failure.
     *
     * @param input The stream containing ZIP-compressed archive data.
     * @return The decoded and validated [MatchArchive].
     * @throws IllegalArgumentException if the archive is malformed, missing required entries,
     *         violates integrity constraints, or exceeds size limits.
     */
    fun read(input: InputStream): MatchArchive {
        var manifest: MatchArchiveManifest? = null
        var archive: MatchArchive? = null

        var totalBytesRead = 0L

        input.use { rawInput ->
            ZipInputStream(rawInput).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.isDirectory) {
                        throw IllegalArgumentException("Archive contains unexpected directory: ${entry.name}")
                    }

                    val content = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (zis.read(buffer).also { read = it } != -1) {
                        totalBytesRead += read
                        if (totalBytesRead > MAX_TOTAL_BYTES) {
                            throw IllegalArgumentException("Archive uncompressed size exceeds limit of $MAX_TOTAL_BYTES bytes.")
                        }
                        content.write(buffer, 0, read)
                    }

                    val jsonString = try {
                        val decoder = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                        decoder.decode(ByteBuffer.wrap(content.toByteArray())).toString()
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Malformed UTF-8 in entry: ${entry.name}", e)
                    }

                    when (entry.name) {
                        MANIFEST_ENTRY_NAME -> {
                            if (manifest != null) throw IllegalArgumentException("Duplicate manifest entry.")
                            manifest = decodeManifest(jsonString)
                        }
                        TIMELINE_ENTRY_NAME -> {
                            if (archive != null) throw IllegalArgumentException("Duplicate timeline entry.")
                            archive = MatchArchiveSerializer.deserialize(jsonString)
                        }
                        else -> {
                            throw IllegalArgumentException("Unexpected entry in archive: ${entry.name}")
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        // 1. Verify both files were present
        val finalManifest = manifest ?: throw IllegalArgumentException("Missing $MANIFEST_ENTRY_NAME in archive.")
        val finalArchive = archive ?: throw IllegalArgumentException("Missing $TIMELINE_ENTRY_NAME in archive.")

        // 2. Integrity Checks
        validateIntegrity(finalManifest, finalArchive)

        return finalArchive
    }

    private fun decodeManifest(jsonString: String): MatchArchiveManifest {
        val manifest = manifestJson.decodeFromString<MatchArchiveManifest>(jsonString)
        
        if (manifest.archiveFormatVersion != 1) {
            throw IllegalArgumentException("Unsupported archive format version: ${manifest.archiveFormatVersion}")
        }
        if (manifest.archiveType != "overdex-match-archive") {
            throw IllegalArgumentException("Invalid archive type: ${manifest.archiveType}")
        }
        if (manifest.timelineEntry != TIMELINE_ENTRY_NAME) {
            throw IllegalArgumentException("Unexpected timeline entry name in manifest: ${manifest.timelineEntry}")
        }
        
        return manifest
    }

    private fun validateIntegrity(manifest: MatchArchiveManifest, archive: MatchArchive) {
        if (manifest.matchId != archive.matchId) {
            throw IllegalArgumentException("Match ID mismatch: Manifest has ${manifest.matchId}, Timeline has ${archive.matchId}")
        }

        if (manifest.articleCount != archive.articles.size) {
            throw IllegalArgumentException("Article count mismatch: Manifest expected ${manifest.articleCount}, found ${archive.articles.size}")
        }

        val seenIds = mutableSetOf<String>()
        archive.articles.forEach { article ->
            if (article.matchId != archive.matchId) {
                throw IllegalArgumentException("Article ${article.articleId} contains mismatched Match ID: ${article.matchId}")
            }
            if (!seenIds.add(article.articleId)) {
                throw IllegalArgumentException("Duplicate Article ID found in archive: ${article.articleId}")
            }
        }
    }
}
