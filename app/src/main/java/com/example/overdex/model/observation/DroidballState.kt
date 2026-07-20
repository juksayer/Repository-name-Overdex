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
    /** Searching for visual anchors or features. */
    SEARCHING,

    /** Observation confirmed with high confidence. */
    CONFIRMED,

    /** Actively aligning to a visual anchor. */
    ALIGNING,

    /** Communicating or synchronizing with external sources. */
    SYNCING,

    /** A system error has occurred. */
    ERROR,

    /** System is idle or de-energized. */
    IDLE
}

/**
 * A presentation model representing Droidball's current state.
 * 
 * DroidballObservationState is derived from the Observation Layer.
 * The Observation Layer decides; Droidball reflects.
 * This model is used exclusively for visualization and does not contain observation logic.
 * 
 * @property lifecycle The current phase of Droidball's deployment.
 * @property indicator The semantic indicator of what Droidball is currently doing.
 * @property targetRegionId The identifier of the screen region currently being observed.
 * @property confidence The system's certainty regarding the current observation task.
 */
data class DroidballObservationState(
    val lifecycle: DroidballLifecycleState = DroidballLifecycleState.DOCKED,
    val indicator: DroidballIndicator = DroidballIndicator.IDLE,
    val targetRegionId: String? = null,
    val confidence: Float = 0f
)
