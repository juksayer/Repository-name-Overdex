package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.PublicTrainerIdentity
import com.example.overdex.model.TrainerIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/**
 * Manages the local trainer's identity, including persistence and public export.
 */
class TrainerRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("overdex_trainer_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _identity = MutableStateFlow<TrainerIdentity?>(null)
    /** Reactive flow of the local trainer's identity. */
    val identity: StateFlow<TrainerIdentity?> = _identity.asStateFlow()

    /**
     * Packages the trainer's sensitive identity into a shareable [PublicTrainerIdentity].
     */
    fun exportPublicIdentity(): PublicTrainerIdentity {
        val current = getIdentity()
        return PublicTrainerIdentity(
            trainerId = current.trainerId.toString(),
            displayName = current.displayName,
            avatarSeed = current.avatarSeed,
            exportedAt = Instant.now(),
            protocolVersion = 1
        )
    }

    init {
        _identity.value = getIdentityFromPrefs()
    }

    private fun getIdentityFromPrefs(): TrainerIdentity {
        val storedJson = prefs.getString("trainer_identity", null)
        return if (storedJson != null) {
            try {
                json.decodeFromString<TrainerIdentity>(storedJson)
            } catch (e: Exception) {
                generateAndSaveIdentity()
            }
        } else {
            generateAndSaveIdentity()
        }
    }

    fun getIdentity(): TrainerIdentity = _identity.value ?: getIdentityFromPrefs()

    private fun generateAndSaveIdentity(): TrainerIdentity {
        val newIdentity = TrainerIdentity(
            displayName = null,
            trainerId = UUID.randomUUID(),
            createdAt = Instant.now(),
            avatarSeed = java.util.Random().nextLong(),
            appVersionWhenCreated = "1.0" // Standardizing on 1.0 for this prototype
        )
        saveIdentity(newIdentity)
        return newIdentity
    }

    fun updateDisplayName(name: String) {
        val current = getIdentity()
        val updated = current.copy(displayName = name)
        saveIdentity(updated)
    }

    fun updatePokemonGoTrainerCode(code: String) {
        val current = getIdentity()
        val updated = current.copy(pokemonGoTrainerCode = code)
        saveIdentity(updated)
    }

    fun saveIdentity(identity: TrainerIdentity) {
        val identityJson = json.encodeToString(TrainerIdentity.serializer(), identity)
        prefs.edit().putString("trainer_identity", identityJson).apply()
        _identity.value = identity
    }
}
