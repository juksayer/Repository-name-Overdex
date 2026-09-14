package com.example.overdex.battle.archive

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        // 1. Serialize the supplied archive with MatchArchiveSerializer before opening the ZIP stream.
        val timelineJson = MatchArchiveSerializer.serialize(archive)
        val artifacts = archive.articles.mapNotNull { (it.payload as? ArchivedCropCaptured)?.let { crop ->
            ArchivedArtifactEntry(crop.artifactPath, crop.sha256, crop.byteCount, crop.mediaType)
        } }.distinctBy { it.relativePath }.sortedBy { it.relativePath }
        val artifactBytes = artifacts.associateWith { artifact ->
            val root = artifactRepositoryRoot
                ?: throw IllegalArgumentException("Crop artifacts require an artifact repository root.")
            requireValidArtifactPath(artifact)
            val file = File(root, artifact.relativePath)
            if (!file.isFile || file.length() != artifact.byteCount || sha256(file.readBytes()) != artifact.sha256) {
                throw IllegalArgumentException("Missing or invalid crop artifact: ${artifact.relativePath}")
            }
            file.readBytes()
        }

        // 2. Build a MatchArchiveManifest from that archive.
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
            zipStream.write(timelineJson.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()

            artifacts.forEach { artifact ->
                zipStream.putNextEntry(ZipEntry(artifact.relativePath))
                zipStream.write(artifactBytes.getValue(artifact))
                zipStream.closeEntry()
            }
        }

        // 6. Return the same manifest after successfully finishing the ZIP.
        return manifest
    }

    private fun requireValidArtifactPath(artifact: ArchivedArtifactEntry) {
        require(artifact.relativePath == "artifacts/crops/sha256/${artifact.sha256}.png") {
            "Invalid crop artifact reference: ${artifact.relativePath}"
        }
        require(artifact.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid crop artifact hash." }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
