package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A transportable, shareable representation of a trainer profile.
 * 
 * Contains only information safe to expose to other Overdex installations during
 * linking and chat.
 * 
 * @property trainerId The unique persistent ID of the trainer.
 * @property displayName The public name shown to others.
 * @property avatarSeed Used for generating a consistent avatar visual.
 * @property exportedAt Timestamp when this identity packet was generated.
 */
@Serializable
data class PublicTrainerIdentity(
    val trainerId: String,
    val displayName: String?,
    val avatarSeed: Long,
    @Serializable(with = InstantSerializer::class)
    val exportedAt: Instant,
    val protocolVersion: Int
)
