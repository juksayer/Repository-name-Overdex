package com.example.overdex.model

/**
 * Defines the type of observation to be performed on a region.
 */
enum class ObservationType {
    /** Region contains text to be extracted via OCR. */
    OCR_TEXT,
    /** Region contains an image to be compared against a known set. */
    IMAGE_MATCH,
    /** Region is used as a template for more complex structural matching. */
    TEMPLATE_MATCH
}

/**
 * Defines a specific region on the screen for observation.
 * 
 * Uses normalized coordinates (0.0 to 1.0) to ensure resolution independence
 * across different Android devices.
 * 
 * @property id The unique identifier of the region (e.g., "SpeciesName").
 * @property x The normalized horizontal start position.
 * @property y The normalized vertical start position.
 * @property width The normalized width of the region.
 * @property height The normalized height of the region.
 * @property type The kind of observation intended for this region.
 */
data class CaptureRegion(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val type: ObservationType
)
