package com.example.overdex.battle.archive

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Handles JSON serialization and deserialization for [MatchArchive] objects.
 */
object MatchArchiveSerializer {

    private const val CURRENT_SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    /**
     * Serializes a [MatchArchive] to a JSON string.
     * Rejects archives whose schema version does not match [CURRENT_SCHEMA_VERSION].
     */
    fun serialize(archive: MatchArchive): String {
        requireSupportedSchema(archive)
        return json.encodeToString(archive)
    }

    /** Writes an archive directly to its destination without retaining its JSON in memory. */
    fun serializeTo(archive: MatchArchive, output: OutputStream) {
        requireSupportedSchema(archive)
        // Android's JSON runtime does not expose the JVM stream encoder.  Each
        // article is still independently serialized, so a complete recording
        // never needs a second, whole-timeline JSON string in memory.
        val writer = OutputStreamWriter(output, Charsets.UTF_8)
        writer.write("{\"schemaVersion\":")
        writer.write(archive.schemaVersion.toString())
        writer.write(",\"matchId\":")
        writer.write(json.encodeToString(archive.matchId))
        writer.write(",\"articles\":[")
        archive.articles.forEachIndexed { index, article ->
            if (index > 0) writer.write(','.code)
            writer.write(json.encodeToString(ArchivedRealityArticle.serializer(), article))
        }
        writer.write("]}")
        writer.flush()
    }

    /**
     * Deserializes a [MatchArchive] from a JSON string.
     * Rejects archives whose schema version does not match [CURRENT_SCHEMA_VERSION].
     */
    fun deserialize(jsonString: String): MatchArchive {
        val archive = json.decodeFromString<MatchArchive>(jsonString)
        if (archive.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema version: ${archive.schemaVersion}. Expected $CURRENT_SCHEMA_VERSION.")
        }
        return archive
    }

    private fun requireSupportedSchema(archive: MatchArchive) {
        if (archive.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema version: ${archive.schemaVersion}. Expected $CURRENT_SCHEMA_VERSION.")
        }
    }
}
