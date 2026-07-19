package com.example.overdex.battle.debug.accessibility

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.overdex.battle.debug.observatory.EvidenceEvent
import com.example.overdex.battle.debug.observatory.EvidenceSource
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json

/**
 * Singleton manager for the Accessibility Probe evidence source.
 * Handles recording state, sequence numbering, and session metadata.
 */
object AccessibilityProbeManager : EvidenceSource {
    override val name = "AccessibilityProbe"

    private var isRecording = false
    private val events = mutableListOf<AccessibilityProbeEvent>()
    private val sequenceCounter = AtomicLong(0)
    private var startTimeMillis: Long = 0
    private var syncSessionId: String? = null
    private var metadata: SessionMetadata? = null

    private val json = Json { prettyPrint = true }

    fun isActive() = isRecording

    override fun startRecording(syncSessionId: String?) {
        clear()
        this.syncSessionId = syncSessionId
        this.startTimeMillis = System.currentTimeMillis()
        this.isRecording = true
    }

    override fun stopRecording() {
        this.isRecording = false
    }

    override fun clear() {
        events.clear()
        sequenceCounter.set(0)
        syncSessionId = null
    }

    override fun getEvents(): List<EvidenceEvent> = events.toList()

    fun onEventReceived(event: AccessibilityProbeEvent) {
        if (isRecording) {
            events.add(event)
        }
    }

    fun nextSequenceNumber() = sequenceCounter.incrementAndGet()
    
    fun getRelativeTimestamp(absoluteTimestamp: Long): Long {
        return absoluteTimestamp - startTimeMillis
    }

    fun captureMetadata(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)

        metadata = SessionMetadata(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.SDK_INT,
            screenResolution = "${metrics.widthPixels}x${metrics.heightPixels}",
            displayDensity = metrics.density,
            refreshRate = display.refreshRate,
            orientation = context.resources.configuration.orientation,
            startTimeMillis = this.startTimeMillis,
        )
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
