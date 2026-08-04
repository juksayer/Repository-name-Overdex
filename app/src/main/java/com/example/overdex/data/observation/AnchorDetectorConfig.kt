package com.example.overdex.data.observation

/**
 * Configuration parameters for visual anchor detection.
 * 
 * @property searchMinY The normalized top boundary for the search area.
 * @property searchMaxY The normalized bottom boundary for the search area.
 * @property searchMinX The normalized left boundary for the search area.
 * @property searchMaxX The normalized right boundary for the search area.
 * @property scanX The normalized horizontal position for initial pixel scanning.
 * @property brightnessThreshold The minimum average pixel brightness to trigger blob detection.
 */
data class AnchorDetectorConfig(
    val searchMinY: Float = 0.4f,
    val searchMaxY: Float = 0.95f,
    val searchMinX: Float = 0.02f,
    val searchMaxX: Float = 0.30f,
    val scanX: Float = 0.10f,
    val brightnessThreshold: Int = 100
) {
    companion object {
        val DEFAULT = AnchorDetectorConfig()
    }
}
