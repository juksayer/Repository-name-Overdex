package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * Metadata capturing the device environment and session parameters.
 */
@Serializable
data class SessionMetadata(
    val deviceModel: String,
    val androidVersion: Int,
    val screenResolution: String,
    val displayDensity: Float,
    val refreshRate: Float?,
    val orientation: Int,
    val startTimeMillis: Long
)

@Serializable
data class RectData(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
