package com.example.overdex.battle.archive

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
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
        output: OutputStream
    ): MatchArchiveManifest {
        // 1. Serialize the supplied archive with MatchArchiveSerializer before opening the ZIP stream.
        val timelineJson = MatchArchiveSerializer.serialize(archive)

        // 2. Build a MatchArchiveManifest from that archive.
        val manifest = MatchArchiveManifest(
            matchId = archive.matchId,
            articleCount = archive.articles.size,
            timelineEntry = TIMELINE_ENTRY_NAME
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
        }

        // 6. Return the same manifest after successfully finishing the ZIP.
        return manifest
    }
}
