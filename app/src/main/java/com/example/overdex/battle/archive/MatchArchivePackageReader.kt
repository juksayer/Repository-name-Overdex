package com.example.overdex.battle.archive

import com.example.overdex.battle.archive.MatchArchivePackageWriter.MANIFEST_ENTRY_NAME
import com.example.overdex.battle.archive.MatchArchivePackageWriter.TIMELINE_ENTRY_NAME
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import java.security.MessageDigest
import com.example.overdex.battle.artifact.FileCropArtifactStore

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
    fun read(input: InputStream, artifactRepositoryRoot: File? = null): MatchArchive {
        var manifest: MatchArchiveManifest? = null
        var archive: MatchArchive? = null

        var totalBytesRead = 0L
        val artifactBytes = linkedMapOf<String, ByteArray>()

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

                    when (entry.name) {
                        MANIFEST_ENTRY_NAME -> {
                            if (manifest != null) throw IllegalArgumentException("Duplicate manifest entry.")
                            manifest = decodeManifest(decodeUtf8(content.toByteArray(), entry.name))
                        }
                        TIMELINE_ENTRY_NAME -> {
                            if (archive != null) throw IllegalArgumentException("Duplicate timeline entry.")
                            archive = MatchArchiveSerializer.deserialize(decodeUtf8(content.toByteArray(), entry.name))
                        }
                        else -> {
                            if (artifactBytes.put(entry.name, content.toByteArray()) != null) {
                                throw IllegalArgumentException("Duplicate archive entry: ${entry.name}")
                            }
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
        validateArtifacts(finalManifest, finalArchive, artifactBytes)
        if (artifactRepositoryRoot != null) {
            val store = FileCropArtifactStore(artifactRepositoryRoot)
            artifactBytes.forEach { (path, bytes) ->
                val reference = store.preserveEncodedPng(bytes)
                    ?: throw IllegalArgumentException("Unable to preserve imported crop artifact: $path")
                if (reference.relativePath != path) throw IllegalArgumentException("Imported crop artifact path mismatch: $path")
            }
        }

        return finalArchive
    }

    private fun decodeManifest(jsonString: String): MatchArchiveManifest {
        val manifest = manifestJson.decodeFromString<MatchArchiveManifest>(jsonString)
        
        if (manifest.archiveFormatVersion !in 1..2) {
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

    private fun decodeUtf8(bytes: ByteArray, entryName: String): String = try {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: Exception) {
        throw IllegalArgumentException("Malformed UTF-8 in entry: $entryName", e)
    }

    private fun validateArtifacts(
        manifest: MatchArchiveManifest,
        archive: MatchArchive,
        artifactBytes: Map<String, ByteArray>
    ) {
        val referenced = archive.articles.mapNotNull { (it.payload as? ArchivedCropCaptured)?.let { crop ->
            ArchivedArtifactEntry(crop.artifactPath, crop.sha256, crop.byteCount, crop.mediaType)
        } }.sortedBy { it.relativePath }
        val declared = manifest.artifacts.sortedBy { it.relativePath }
        if (referenced != declared) throw IllegalArgumentException("Crop artifact manifest does not match Timeline references.")
        if (artifactBytes.keys != declared.map { it.relativePath }.toSet()) {
            throw IllegalArgumentException("Archive crop artifact entries do not match the manifest.")
        }
        declared.forEach { artifact ->
            require(artifact.relativePath == "artifacts/crops/sha256/${artifact.sha256}.png") {
                "Invalid crop artifact reference: ${artifact.relativePath}"
            }
            val bytes = artifactBytes.getValue(artifact.relativePath)
            if (bytes.size.toLong() != artifact.byteCount || sha256(bytes) != artifact.sha256) {
                throw IllegalArgumentException("Crop artifact hash verification failed: ${artifact.relativePath}")
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

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
