package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * Enumeration of evidence source types.
 * This is an initial set and will be expanded as new observation signals are integrated.
 */
@Serializable
enum class EvidenceSourceType {
    VISION,
    ACCESSIBILITY,
    TIMING,
    SYSTEM,
    MANUAL,
    CONFIDENCE // Placeholder for future use
}
