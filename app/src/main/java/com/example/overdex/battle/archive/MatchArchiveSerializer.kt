package com.example.overdex.battle.archive

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

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
        if (archive.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported schema version: ${archive.schemaVersion}. Expected $CURRENT_SCHEMA_VERSION.")
        }
        return json.encodeToString(archive)
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
}
