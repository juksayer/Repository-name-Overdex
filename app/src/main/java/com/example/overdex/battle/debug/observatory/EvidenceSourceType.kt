package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * Categorizes the origin or nature of an evidence signal in the observatory.
 */
@Serializable
enum class EvidenceSourceType {
    /** Visual captures and recognition results from the screen. */
    VISION,
    /** Structural UI events from Android Accessibility services. */
    ACCESSIBILITY,
    /** Latency, frame-rate, and other performance metrics. */
    TIMING,
    /** Lifecycle and state changes within Overdex. */
    SYSTEM,
    /** Observations explicitly entered by a user or developer. */
    MANUAL,
    /** Logic outcomes and belief resolutions from the observation engines. */
    DECISION
}
