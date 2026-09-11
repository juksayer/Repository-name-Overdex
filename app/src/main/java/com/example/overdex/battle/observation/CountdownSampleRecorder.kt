package com.example.overdex.battle.observation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.overdex.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only recorder for saving countdown OCR crops and metadata.
 */
object CountdownSampleRecorder {
    private const val TAG = "COUNTDOWN_SAMPLE"
    private const val MAX_SESSIONS = 3
    private const val QUEUE_CAPACITY = 4

    private var applicationContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<SampleTask>(QUEUE_CAPACITY)
    
    // Active sessions are tracked only for cleanup protection. 
    // Tasks carry their own session state reference.
    private val activeSessions = ConcurrentHashMap<String, SessionState>()

    internal class SessionState(
        val sessionId: String,
        val sessionDir: File,
        @Volatile var isStopped: Boolean = false,
        val pendingCount: AtomicInteger = AtomicInteger(0)
    )

    @Serializable
    data class SampleMetadata(
        val sessionId: String,
        val sequence: Long,
        val receiptTimestamp: Long,
        val cropRect: RectData,
        val sourceDimensions: Dimensions,
        val durationMs: Long,
        val outcome: String,
        val rawValue: String?
    )

    @Serializable
    data class RectData(val left: Int, val top: Int, val right: Int, val bottom: Int)

    @Serializable
    data class Dimensions(val width: Int, val height: Int)

    private class SampleTask(
        val session: SessionState,
        val sequence: Long,
        val bitmap: Bitmap,
        val metadata: SampleMetadata
    )

    init {
        if (BuildConfig.DEBUG) {
            scope.launch {
                for (task in queue) {
                    saveTask(task)
                }
            }
        }
    }

    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG) return
        applicationContext = context.applicationContext
    }

    fun startSession(matchId: String): String {
        if (!BuildConfig.DEBUG) return ""
        val context = applicationContext ?: return ""
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        val uniqueId = UUID.randomUUID().toString().take(8)
        val sessionId = "${matchId}_${timestamp}_$uniqueId"
        
        val diagnosticsDir = File(context.filesDir, "diagnostics/countdown_samples")
        val sessionDir = File(diagnosticsDir, "session_$sessionId")
        
        val state = SessionState(sessionId, sessionDir)
        activeSessions[sessionId] = state
        
        scope.launch {
            cleanupOldSessions()
        }
        
        return sessionId
    }

    fun stopSession(sessionId: String) {
        if (!BuildConfig.DEBUG || sessionId.isEmpty()) return
        val state = activeSessions[sessionId] ?: return
        
        synchronized(state) {
            if (state.isStopped) return
            state.isStopped = true
            if (state.pendingCount.get() == 0) {
                finalizeSession(state)
            }
        }
    }

    private fun finalizeSession(state: SessionState) {
        activeSessions.remove(state.sessionId)
        Log.d(TAG, "Session ${state.sessionId} finalized.")
        scope.launch {
            cleanupOldSessions()
        }
    }

    /**
     * Attempts to record a sample. Coordination of admission and pending count
     * is handled under session lock. Bitmap copying and disk I/O are performed outside the lock.
     */
    fun record(
        sessionId: String,
        sequence: Long,
        bitmap: Bitmap,
        receiptTimestamp: Long,
        cropRect: Rect,
        sourceWidth: Int,
        sourceHeight: Int,
        durationMs: Long,
        outcome: String,
        rawValue: String?
    ) {
        if (!BuildConfig.DEBUG || sessionId.isEmpty()) return
        
        val state = activeSessions[sessionId] ?: return
        
        synchronized(state) {
            if (state.isStopped) {
                Log.w(TAG, "Rejecting record for stopped session $sessionId (seq=$sequence)")
                return
            }
            // Reserve a slot
            state.pendingCount.incrementAndGet()
        }

        var success = false
        var bitmapCopy: Bitmap? = null
        try {
            val config = bitmap.config ?: Bitmap.Config.ARGB_8888
            bitmapCopy = bitmap.copy(config, false)
            
            if (bitmapCopy == null) {
                Log.e(TAG, "Original bitmap copy failed for seq=$sequence")
                decrementAndCheckFinalize(state)
                return
            }

            val metadata = SampleMetadata(
                sessionId = sessionId,
                sequence = sequence,
                receiptTimestamp = receiptTimestamp,
                cropRect = RectData(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom),
                sourceDimensions = Dimensions(sourceWidth, sourceHeight),
                durationMs = durationMs,
                outcome = outcome,
                rawValue = rawValue
            )

            val task = SampleTask(state, sequence, bitmapCopy, metadata)
            
            val result = queue.trySend(task)
            if (result.isFailure) {
                Log.w(TAG, "Queue full. Skipping sample seq=$sequence")
                bitmapCopy.recycle()
                decrementAndCheckFinalize(state)
            } else {
                success = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during record preparation for seq=$sequence", e)
            if (!success) {
                bitmapCopy?.recycle()
                decrementAndCheckFinalize(state)
            }
        }
    }

    private fun decrementAndCheckFinalize(state: SessionState) {
        val remaining = state.pendingCount.decrementAndGet()
        synchronized(state) {
            if (state.isStopped && remaining == 0) {
                finalizeSession(state)
            }
        }
    }

    private fun saveTask(task: SampleTask) {
        val state = task.session
        try {
            if (!state.sessionDir.exists()) {
                state.sessionDir.mkdirs()
            }

            // Save original PNG
            val pngFile = File(state.sessionDir, "sample_${task.sequence}.png")
            var compressSuccess = false
            FileOutputStream(pngFile).use { out ->
                compressSuccess = task.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (!compressSuccess) {
                Log.e(TAG, "Original PNG compression failed for seq=${task.sequence}")
                pngFile.delete()
            } else {
                // Save JSON
                val jsonFile = File(state.sessionDir, "sample_${task.sequence}.json")
                val jsonString = Json.encodeToString(task.metadata)
                jsonFile.writeText(jsonString)
                Log.d(TAG, "Saved sample seq=${task.sequence} to ${state.sessionDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save sample seq=${task.sequence}", e)
        } finally {
            task.bitmap.recycle()
            decrementAndCheckFinalize(state)
        }
    }

    private fun cleanupOldSessions() {
        val context = applicationContext ?: return
        val diagnosticsDir = File(context.filesDir, "diagnostics/countdown_samples")
        if (!diagnosticsDir.exists()) return

        val sessionDirs = diagnosticsDir.listFiles { file -> file.isDirectory && file.name.startsWith("session_") }
            ?: return

        val sortedDirs = sessionDirs.sortedByDescending { it.lastModified() }

        if (sortedDirs.size > MAX_SESSIONS) {
            val dirsToRemove = sortedDirs.drop(MAX_SESSIONS)
            for (dir in dirsToRemove) {
                val dirSessionId = dir.name.removePrefix("session_")
                if (!activeSessions.containsKey(dirSessionId)) {
                    Log.d(TAG, "Cleaning up old session: ${dir.name}")
                    dir.deleteRecursively()
                }
            }
        }
    }
}
