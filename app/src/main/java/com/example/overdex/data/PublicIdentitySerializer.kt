package com.example.overdex.data

import com.example.overdex.model.PublicTrainerIdentity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Responsible for converting PublicTrainerIdentity to and from JSON.
 */
object PublicIdentitySerializer {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(identity: PublicTrainerIdentity): String {
        return json.encodeToString(identity)
    }

    fun deserialize(jsonString: String): PublicTrainerIdentity {
        return json.decodeFromString(jsonString)
    }
}
