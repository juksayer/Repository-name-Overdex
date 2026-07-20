package com.example.overdex

/**
 * Defines a static region on the screen used for spatial anchoring.
 * 
 * Coordinates are normalized (0.0 to 1.0) to ensure resolution independence.
 */
data class AnchorRegion(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)
