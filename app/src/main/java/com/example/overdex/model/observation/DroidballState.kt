package com.example.overdex.model.observation

import androidx.compose.ui.graphics.Color

/**
 * Defines the logical lifecycle states of Droidball.
 */
enum class DroidballLifecycleState {
    /**
     * Droidball is inside the ODX-FI Service Drawer.
     */
    DOCKED,

    /**
     * Droidball is outside and actively participating in an observation.
     */
    DEPLOYED,

    /**
     * Droidball is deployed but currently has no observation target.
     */
    IDLE
}

/**
 * Semantic indicators for Droidball's state. 
 * The presentation layer (UI) maps these to specific colors (e.g. Amber, Green).
 */
enum class DroidballIndicator {
    /**
     * Searching, Preparing, Waiting.
     */
    SEARCHING,

    /**
     * Observation confirmed, Confidence established.
     */
    CONFIRMED,

    /**
     * Observation active, Anchor alignment.
     */
    ALIGNING,

    /**
     * Communication, Synchronization.
     */
    SYNCING,

    /**
     * Genuine system fault.
     */
    ERROR,

    /**
     * De-energized.
     */
    IDLE
}

/**
 * A presentation model representing Droidball's current state.
 * 
 * DroidballObservationState is a presentation model derived from the Observation Layer.
 * The Observation Layer decides; Droidball reflects.
 * This model is used exclusively for visualization and does not contain observation logic.
 */
data class DroidballObservationState(
    val lifecycle: DroidballLifecycleState = DroidballLifecycleState.DOCKED,
    val indicator: DroidballIndicator = DroidballIndicator.IDLE,
    val targetRegionId: String? = null,
    val confidence: Float = 0f
)
