package com.example.overdex.battle.debug.accessibility

import com.example.overdex.battle.debug.observatory.AccessibilityProbeNode
import com.example.overdex.battle.debug.observatory.EvidenceEvent
import com.example.overdex.battle.debug.observatory.RectData
import com.example.overdex.battle.debug.observatory.SessionMetadata
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
    val rawEventData: Map<String, String> = emptyMap()
) : EvidenceEvent

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
