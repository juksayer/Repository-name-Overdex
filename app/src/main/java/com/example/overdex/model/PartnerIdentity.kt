package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Represents a permanently linked partner trainer.
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