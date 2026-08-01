package com.example.overdex.battle.debug.accessibility

import com.example.overdex.battle.debug.observatory.AccessibilityProbeNode
import com.example.overdex.battle.debug.observatory.EvidenceEvent
import com.example.overdex.battle.debug.observatory.RectData
import com.example.overdex.battle.debug.observatory.MatchMetadata
import android.view.accessibility.AccessibilityEvent
import kotlinx.serialization.Serializable

/**
 * Represents a discrete event captured by the [AccessibilityProbeService].
 * 
 * This model preserves the structural state of the UI at the time of the event,
 * including the node tree and event metadata.
 * 
 * @property eventType The type of accessibility event (e.g., TYPE_WINDOW_STATE_CHANGED).
 * @property packageName The package name of the application that produced the event.
 * @property className The class name of the UI component associated with the event.
 * @property text The text content associated with the event.
 * @property contentDescription The accessibility content description.
 * @property bounds The screen coordinates of the event source.
 * @property viewIdResourceName The resource ID of the view (if available).
 * @property nodeTree A snapshot of the UI hierarchy at the time of the event.
 * @property rawEventData A map of additional raw metadata from the original [AccessibilityEvent].
 */
@Serializable
data class AccessibilityProbeEvent(
    override val sequenceNumber: Long,
    override val timestamp: Long,
    override val relativeTimestamp: Long,
    override val sourceName: String = "AccessibilityProbe",
    val eventType: String,
    val packageName: String?,
    val className: String?,
    val text: List<String>,
    val contentDescription: String?,
    val bounds: RectData?,
    val viewIdResourceName: String?,
    val nodeTree: AccessibilityProbeNode?,
    val rawEventData: Map<String, String> = emptyMap()
) : EvidenceEvent

/**
 * A summary of the evidence collected during an accessibility probe Match.
 */
@Serializable
data class ObservatorySummary(
    val totalEvents: Int,
    val nodesWithText: Int,
    val nodesWithContentDescription: Int,
    val nodesWithBounds: Int,
    val metadata: MatchMetadata? = null
)

@Serializable
data class ExportData(
    val summary: ObservatorySummary,
    val events: List<AccessibilityProbeEvent>
)
