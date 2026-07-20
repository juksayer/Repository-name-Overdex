package com.example.overdex.battle.debug.observatory

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton coordinator for recording evidence during an observation session.
 * 
 * Acting as a "Flight Recorder," the [ObservationRecorder] captures a high-fidelity
 * stream of visual, system, and decision-making events. This recording can be
 * exported for analysis, replay, or debugging the observation pipeline.
 */
object ObservationRecorder {
    private var isRecording = false
    private var sessionId: String? = null
    private var startTime: Long = 0
    private var metadata: SessionMetadata? = null
    
    private val events = mutableListOf<RecordedEvent>()
    private val sequenceCounter = AtomicLong(0)

    private var _lastRecording: ObservationRecording? = null

    /**
     * Initializes a new recording session and captures device metadata.
     * 
     * @param context Android context used to capture screen resolution and density.
     */
    fun startRecording(context: Context) {
        if (isRecording) return
        
        clear()
        this.sessionId = UUID.randomUUID().toString()
        this.startTime = System.currentTimeMillis()
        this.metadata = captureMetadata(context)
        this.isRecording = true
    }

    private fun captureMetadata(context: Context): SessionMetadata {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        display.getMetrics(metrics)

        return SessionMetadata(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.SDK_INT,
            screenResolution = "${metrics.widthPixels}x${metrics.heightPixels}",
            displayDensity = metrics.density,
            refreshRate = try { display.refreshRate } catch (_: Exception) { null },
            orientation = context.resources.configuration.orientation,
            startTimeMillis = this.startTime
        )
    }

    fun stopRecording(): ObservationRecording? {
        if (!isRecording) return null
        
        val recording = ObservationRecording(
            sessionId = sessionId ?: "",
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            metadata = metadata,
            events = events.toList()
        )
        
        _lastRecording = recording
        isRecording = false
        return recording
    }

    fun record(sourceType: EvidenceSourceType, payload: RecordedPayload) {
        if (!isRecording) return
        
        val timestamp = System.currentTimeMillis()
        val event = RecordedEvent(
            sessionId = sessionId ?: "",
            sequenceNumber = sequenceCounter.incrementAndGet(),
            timestamp = timestamp,
            relativeTimestamp = timestamp - startTime,
            sourceType = sourceType,
            payload = payload
        )
        
        events.add(event)
    }

    fun clear() {
        events.clear()
        sequenceCounter.set(0)
        sessionId = null
        startTime = 0
        metadata = null
        isRecording = false
    }

    // Query API
    fun isRecording() = isRecording
    fun currentSessionId() = sessionId
    fun eventCount() = events.size
    fun getEvents() = events.toList()
    fun getLastRecording() = _lastRecording
}
