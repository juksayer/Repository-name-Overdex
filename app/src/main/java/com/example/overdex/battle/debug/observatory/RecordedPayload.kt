package com.example.overdex.battle.debug.observatory

import kotlinx.serialization.Serializable

/**
 * Base interface for all evidence payloads recorded by the Observation Recorder.
 * Ensures type safety and clean serialization for diverse signal types.
 */
@Serializable
sealed interface RecordedPayload

/**
 * Payload implementation for Accessibility evidence.
 */
@Serializable
data class AccessibilityPayload(
    val eventType: String,
    val packageName: String?,
    val className: String?,
    val text: List<String>,
    val contentDescription: String?,
    val bounds: RectData?,
    val viewIdResourceName: String?,
    val nodeTree: AccessibilityProbeNode?,
    val rawEventData: Map<String, String> = emptyMap()
) : RecordedPayload

@Serializable
data class AccessibilityProbeNode(
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val clickable: Boolean,
    val focusable: Boolean,
    val visible: Boolean,
    val enabled: Boolean,
    val bounds: RectData,
    val children: List<AccessibilityProbeNode> = emptyList()
)
