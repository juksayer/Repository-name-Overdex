package com.example.overdex.model

enum class ObservationType {
    OCR_TEXT,
    IMAGE_MATCH,
    TEMPLATE_MATCH
}

/**
 * Defines a specific region on the screen for observation.
 * Uses normalized coordinates (0.0 to 1.0) for resolution independence.
 */
data class CaptureRegion(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val type: ObservationType
)
