package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Handles JSON serialization for [MatchRecording] objects.
 */
object RecordingSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serializes a recording to a JSON string. */
    fun serialize(recording: MatchRecording): String {
        return json.encodeToString(recording)
    }

    /** Deserializes a recording from a JSON string. */
    fun deserialize(jsonString: String): MatchRecording {
        return json.decodeFromString(jsonString)
    }
}
