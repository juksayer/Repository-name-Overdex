package com.example.overdex.battle.debug.accessibility

import android.content.Context
import com.example.overdex.battle.debug.observatory.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Singleton manager for the Accessibility Probe evidence source.
 * 
 * This manager coordinates the capture of UI events from the [AccessibilityProbeService]
 * and ensures they are recorded both locally (for summary reporting) and globally
 * via the [ObservationRecorder].
 */
object AccessibilityProbeManager : EvidenceSource {
    override val name = "AccessibilityProbe"

    private var isRecording = false
    private val events = mutableListOf<AccessibilityProbeEvent>()
    private val sequenceCounter = AtomicLong(0)
    private var startTimeMillis: Long = 0
    private var syncMatchId: String? = null
    private var metadata: MatchMetadata? = null

    private val json = Json { prettyPrint = true }

    /** Returns true if the probe is currently recording events. */
    fun isActive() = isRecording

    override fun startRecording(syncMatchId: String?) {
        clear()
        this.syncMatchId = syncMatchId
        this.startTimeMillis = System.currentTimeMillis()
        this.isRecording = true
    }

    override fun stopRecording() {
        this.isRecording = false
    }

    override fun clear() {
        events.clear()
        sequenceCounter.set(0)
        syncMatchId = null
    }

    override fun getEvents(): List<EvidenceEvent> = events.toList()

    fun onEventReceived(event: AccessibilityProbeEvent) {
        if (isRecording) {
            events.add(event)
            
            // Delegate to central recorder
            val payload = AccessibilityPayload(
                eventType = event.eventType,
                packageName = event.packageName,
                className = event.className,
                text = event.text,
                contentDescription = event.contentDescription,
                bounds = event.bounds,
                viewIdResourceName = event.viewIdResourceName,
                nodeTree = event.nodeTree,
                rawEventData = event.rawEventData
            )
            ObservationRecorder.record(EvidenceSourceType.ACCESSIBILITY, payload)
        }
    }

    fun nextSequenceNumber() = sequenceCounter.incrementAndGet()
    
    fun getRelativeTimestamp(absoluteTimestamp: Long): Long {
        return absoluteTimestamp - startTimeMillis
    }

    fun captureMetadata(context: Context) {
        ObservationRecorder.startRecording(context)
        // We'll keep a copy of metadata for the independent summary too
        // In a real app we'd expose this through ObservationRecorder
    }

    fun getSummary(): ObservatorySummary {
        var textCount = 0
        var descCount = 0
        var boundsCount = 0
        
        events.forEach { event ->
            event.nodeTree?.let { root ->
                val stats = calculateNodeStats(root)
                textCount += stats.t
                descCount += stats.d
                boundsCount += stats.b
            }
        }

        return ObservatorySummary(
            totalEvents = events.size,
            nodesWithText = textCount,
            nodesWithContentDescription = descCount,
            nodesWithBounds = boundsCount,
            metadata = metadata
        )
    }

    private data class NodeStats(val t: Int, val d: Int, val b: Int)

    private fun calculateNodeStats(node: AccessibilityProbeNode): NodeStats {
        var t = if (!node.text.isNullOrBlank()) 1 else 0
        var d = if (!node.contentDescription.isNullOrBlank()) 1 else 0
        var b = 1
        
        node.children.forEach { child ->
            val childStats = calculateNodeStats(child)
            t += childStats.t
            d += childStats.d
            b += childStats.b
        }
        return NodeStats(t, d, b)
    }

    fun exportAsJson(context: Context): File? {
        val summary = getSummary()
        val exportData = ExportData(summary, events.toList())
        val jsonString = json.encodeToString(exportData)
        
        val file = File(context.getExternalFilesDir(null), "accessibility_probe_$startTimeMillis.json")
        file.writeText(jsonString)
        return file
    }
}
