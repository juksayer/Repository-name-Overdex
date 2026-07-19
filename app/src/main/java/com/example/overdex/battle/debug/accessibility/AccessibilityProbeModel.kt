package com.example.overdex.battle.debug.accessibility

import com.example.overdex.battle.debug.observatory.EvidenceEvent
import kotlinx.serialization.Serializable

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
    val rawEventData: Map<String, String> = emptyMap() // Simplified for serialization stability
) : EvidenceEvent

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

@Serializable
data class RectData(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class SessionMetadata(
    val deviceModel: String,
    val androidVersion: Int,
    val screenResolution: String,
    val displayDensity: Float,
    val refreshRate: Float?,
    val orientation: Int,
    val startTimeMillis: Long
)

@Serializable
data class ObservatorySummary(
    val totalEvents: Int,
    val nodesWithText: Int,
    val nodesWithContentDescription: Int,
    val nodesWithBounds: Int,
    val metadata: SessionMetadata? = null
)

@Serializable
data class ExportData(
    val summary: ObservatorySummary,
    val events: List<AccessibilityProbeEvent>
)
