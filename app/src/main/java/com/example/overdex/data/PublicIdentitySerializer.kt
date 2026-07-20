package com.example.overdex.data

import com.example.overdex.model.PublicTrainerIdentity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Responsible for converting [PublicTrainerIdentity] objects to and from JSON.
 * 
 * This is used when serializing an identity for sharing via QR codes.
 */
object PublicIdentitySerializer {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Converts an identity to a JSON string. */
    fun serialize(identity: PublicTrainerIdentity): String {
        return json.encodeToString(identity)
    }

    /** Parses an identity from a JSON string. */
    fun deserialize(jsonString: String): PublicTrainerIdentity {
        return json.decodeFromString(jsonString)
    }
}
