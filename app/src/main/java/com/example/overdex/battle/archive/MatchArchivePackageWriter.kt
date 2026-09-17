package com.example.overdex.battle.archive

import java.io.OutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Writer for packaging a [MatchArchive] into a `.odxmatch` ZIP container.
 */
object MatchArchivePackageWriter {

    const val MANIFEST_ENTRY_NAME = "manifest.json"
    const val TIMELINE_ENTRY_NAME = "timeline.json"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Serializes the supplied archive, builds a manifest, and writes both entries
     * into the provided [OutputStream] as a ZIP package.
     *
     * @returns The generated [MatchArchiveManifest].
     */
    fun write(
        archive: MatchArchive,
        output: OutputStream,
        artifactRepositoryRoot: File? = null
    ): MatchArchiveManifest {
        val artifacts = archive.articles.mapNotNull { article ->
            when (val payload = article.payload) {
                is ArchivedCropCaptured -> ArchivedArtifactEntry(
                    payload.artifactPath, payload.sha256, payload.byteCount, payload.mediaType
                )
                is ArchivedAudioCaptured -> ArchivedArtifactEntry(
                    payload.artifactPath, payload.sha256, payload.byteCount, payload.mediaType
                )
                else -> null
            }
        }.distinctBy { it.relativePath }.sortedBy { it.relativePath }
        // Build the manifest before writing. Artifact bytes themselves stay on disk until streamed.
        val manifest = MatchArchiveManifest(
            matchId = archive.matchId,
            articleCount = archive.articles.size,
            timelineEntry = TIMELINE_ENTRY_NAME,
            artifacts = artifacts
        )
        val manifestJson = json.encodeToString(manifest)

        // 3, 4, 5. Write ZIP package containing manifest.json and timeline.json encoded as UTF-8.
        ZipOutputStream(output).use { zipStream ->
            zipStream.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
            zipStream.write(manifestJson.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()

            zipStream.putNextEntry(ZipEntry(TIMELINE_ENTRY_NAME))
            MatchArchiveSerializer.serializeTo(archive, zipStream)
            zipStream.closeEntry()

            artifacts.forEach { artifact ->
                zipStream.putNextEntry(ZipEntry(artifact.relativePath))
                copyValidatedArtifact(artifact, artifactRepositoryRoot, zipStream)
                zipStream.closeEntry()
            }
        }

        // 6. Return the same manifest after successfully finishing the ZIP.
        return manifest
    }

    private fun requireValidArtifactPath(artifact: ArchivedArtifactEntry) {
        val isCrop = artifact.relativePath == "artifacts/crops/sha256/${artifact.sha256}.png" &&
            artifact.mediaType == "image/png"
        val isAudio = artifact.relativePath == "artifacts/audio/sha256/${artifact.sha256}.wav" &&
            artifact.mediaType == "audio/wav"
        require(isCrop || isAudio) { "Invalid artifact reference: ${artifact.relativePath}" }
        require(artifact.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid artifact hash." }
    }

    private fun copyValidatedArtifact(
        artifact: ArchivedArtifactEntry,
        artifactRepositoryRoot: File?,
        output: OutputStream
    ) {
        val root = artifactRepositoryRoot
            ?: throw IllegalArgumentException("Crop artifacts require an artifact repository root.")
        requireValidArtifactPath(artifact)
        val file = File(root, artifact.relativePath)
        if (!file.isFile || file.length() != artifact.byteCount) {
            throw IllegalArgumentException("Missing or invalid crop artifact: ${artifact.relativePath}")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (actualHash != artifact.sha256) {
            throw IllegalArgumentException("Missing or invalid crop artifact: ${artifact.relativePath}")
        }
    }
}
