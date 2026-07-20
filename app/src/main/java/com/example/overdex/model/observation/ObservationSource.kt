package com.example.overdex.model.observation

/**
 * Defines the mechanism used to capture or generate an [Observation].
 */
enum class ObservationSource {
    /** Text extracted from images via Optical Character Recognition. */
    OCR,
    /** Information extracted from audio signals. */
    AUDIO,
    /** Factual information manually entered by the user. */
    MANUAL,
    /** Synthetic data used for testing and UI development. */
    PROTOTYPE,
    /** Visual features detected using machine learning (e.g., icons, status effects). */
    AI_VISION
}
