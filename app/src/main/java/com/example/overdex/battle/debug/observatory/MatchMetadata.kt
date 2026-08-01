package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * Metadata capturing the device environment and Match parameters.
 * 
 * This information is used during replay and analysis to ensure that coordinate
 * calculations and timing metrics are interpreted correctly for the specific
 * hardware that produced the recording.
 * 
 * @property deviceModel The manufacturer and model of the device.
 * @property androidVersion The Android API level (SDK_INT).
 * @property screenResolution The dimensions of the screen in pixels (e.g., "1080x2400").
 * @property displayDensity The logical density of the display (DPI).
 * @property refreshRate The display's refresh rate in Hz, if available.
 * @property orientation The screen orientation (e.g., Portrait, Landscape).
 * @property startTimeMillis The absolute system time when the Match metadata was captured.
 */
@Serializable
data class MatchMetadata(
    val deviceModel: String,
    val androidVersion: Int,
    val screenResolution: String,
    val displayDensity: Float,
    val refreshRate: Float?,
    val orientation: Int,
    val startTimeMillis: Long
)

/**
 * A serializable, platform-independent representation of a rectangular region.
 */
@Serializable
data class RectData(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
