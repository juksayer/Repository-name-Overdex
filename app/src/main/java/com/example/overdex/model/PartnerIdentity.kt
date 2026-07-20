package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Represents a permanently linked partner trainer.
 * 
 * @property trainerId The unique persistent ID of the partner.
 * @property displayName The public name chosen by the partner.
 * @property linkedAt Time when the link was established.
 * @property companionSpecies The current Pokémon species chosen as the partner's mascot.
 */
@Serializable
data class PartnerIdentity(
    val trainerId: String,
    val displayName: String?,
    val avatarSeed: Long,
    @Serializable(with = InstantSerializer::class)
    val linkedAt: Instant,
    val protocolVersion: Int,

    // Temporary until trainers can choose their companion.
    val companionSpecies: String = "Shadow Gardevoir"
)
