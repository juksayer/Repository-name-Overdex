package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A transportable, shareable representation of a trainer containing only 
 * information safe to expose to other Overdex installations.
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
