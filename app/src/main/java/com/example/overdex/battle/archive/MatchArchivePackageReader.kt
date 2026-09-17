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

    // Complete matches now preserve raw crops and cue-centered audio, so 16 MiB
    // is no longer a viable archive ceiling.
    private const val MAX_TOTAL_BYTES = 256 * 1024 * 1024 // 256 MiB

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
        val stagingRoot = kotlin.io.path.createTempDirectory("odxmatch-read-").toFile()
        val stagedArtifacts = linkedMapOf<String, File>()

        try {
            input.use { rawInput ->
                ZipInputStream(rawInput).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.isDirectory) throw IllegalArgumentException("Archive contains unexpected directory: ${entry.name}")
                        when (entry.name) {
                            MANIFEST_ENTRY_NAME -> {
                                if (manifest != null) throw IllegalArgumentException("Duplicate manifest entry.")
                                val bytes = readEntryBytes(zis) { totalBytesRead += it }
                                manifest = decodeManifest(decodeUtf8(bytes, entry.name))
                            }
                            TIMELINE_ENTRY_NAME -> {
                                if (archive != null) throw IllegalArgumentException("Duplicate timeline entry.")
                                val bytes = readEntryBytes(zis) { totalBytesRead += it }
                                archive = MatchArchiveSerializer.deserialize(decodeUtf8(bytes, entry.name))
                            }
                            else -> {
                                if (stagedArtifacts.containsKey(entry.name)) throw IllegalArgumentException("Duplicate archive entry: ${entry.name}")
                                val staged = File(stagingRoot, "artifact-${stagedArtifacts.size}")
                                readEntryToFile(zis, staged) { totalBytesRead += it }
                                stagedArtifacts[entry.name] = staged
                            }
                        }
                        if (totalBytesRead > MAX_TOTAL_BYTES) throw IllegalArgumentException("Archive uncompressed size exceeds limit of $MAX_TOTAL_BYTES bytes.")
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val finalManifest = manifest ?: throw IllegalArgumentException("Missing $MANIFEST_ENTRY_NAME in archive.")
            val finalArchive = archive ?: throw IllegalArgumentException("Missing $TIMELINE_ENTRY_NAME in archive.")
            validateIntegrity(finalManifest, finalArchive)
            validateArtifacts(finalManifest, finalArchive, stagedArtifacts)
            if (artifactRepositoryRoot != null) importArtifacts(artifactRepositoryRoot, stagedArtifacts)
            return finalArchive
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun readEntryBytes(input: InputStream, onBytesRead: (Long) -> Unit): ByteArray =
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                onBytesRead(read.toLong())
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }

    private fun readEntryToFile(input: InputStream, target: File, onBytesRead: (Long) -> Unit) {
        target.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                onBytesRead(read.toLong())
                output.write(buffer, 0, read)
            }
        }
    }

    private fun importArtifacts(repositoryRoot: File, stagedArtifacts: Map<String, File>) {
        val cropStore = FileCropArtifactStore(repositoryRoot)
        stagedArtifacts.forEach { (path, staged) ->
            if (path.startsWith("artifacts/crops/")) {
                val reference = cropStore.preserveEncodedPng(staged.readBytes())
                    ?: throw IllegalArgumentException("Unable to preserve imported crop artifact: $path")
                if (reference.relativePath != path) throw IllegalArgumentException("Imported crop artifact path mismatch: $path")
            } else {
                val output = File(repositoryRoot, path)
                output.parentFile?.mkdirs()
                if (!output.exists()) staged.copyTo(output)
            }
        }
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
        artifactFiles: Map<String, File>
    ) {
        val referenced = archive.articles.mapNotNull { article ->
            when (val payload = article.payload) {
                is ArchivedCropCaptured -> ArchivedArtifactEntry(payload.artifactPath, payload.sha256, payload.byteCount, payload.mediaType)
                is ArchivedAudioCaptured -> ArchivedArtifactEntry(payload.artifactPath, payload.sha256, payload.byteCount, payload.mediaType)
                else -> null
            }
        }.distinctBy { it.relativePath }.sortedBy { it.relativePath }
        val declared = manifest.artifacts.sortedBy { it.relativePath }
        if (referenced != declared) throw IllegalArgumentException("Crop artifact manifest does not match Timeline references.")
        if (artifactFiles.keys != declared.map { it.relativePath }.toSet()) {
            throw IllegalArgumentException("Archive crop artifact entries do not match the manifest.")
        }
        declared.forEach { artifact ->
            val validCrop = artifact.relativePath == "artifacts/crops/sha256/${artifact.sha256}.png" && artifact.mediaType == "image/png"
            val validAudio = artifact.relativePath == "artifacts/audio/sha256/${artifact.sha256}.wav" && artifact.mediaType == "audio/wav"
            require(validCrop || validAudio) { "Invalid artifact reference: ${artifact.relativePath}" }
            val file = artifactFiles.getValue(artifact.relativePath)
            if (file.length() != artifact.byteCount || sha256(file) != artifact.sha256) {
                throw IllegalArgumentException("Crop artifact hash verification failed: ${artifact.relativePath}")
            }
        }
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
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
