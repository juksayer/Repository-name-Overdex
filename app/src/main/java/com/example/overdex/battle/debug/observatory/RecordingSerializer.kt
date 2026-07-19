package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Handles JSON serialization for Observation Recordings.
 */
object RecordingSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(recording: ObservationRecording): String {
        return json.encodeToString(recording)
    }

    fun deserialize(jsonString: String): ObservationRecording {
        return json.decodeFromString(jsonString)
    }
}
