package com.example.overdex.battle.observation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.overdex.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Debug-only recorder for high-rate countdown capture bursts.
 * Manages per-session resources to ensure isolation and reliable cleanup.
 */
object CountdownBurstRecorder {
    private const val TAG = "COUNTDOWN_BURST"
    private const val QUEUE_CAPACITY = 8

    private var applicationContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Per-session state tracking
    private val sessionStates = ConcurrentHashMap<String, BurstSessionState>()

    @Serializable
    data class BurstManifest(
        val sessionId: String,
        val triggerTimestamp: Long,
        val cropRect: RectData,
        val trainerInactivePokemonCropRect: RectData,
        val sourceDimensions: Dimensions,
        val frames: List<BurstFrameRecord>
    )

    @Serializable
    data class BurstFrameRecord(
        val index: Int,
        val receiptTimestamp: Long
    )

    @Serializable
    data class RectData(val left: Int, val top: Int, val right: Int, val bottom: Int)

    @Serializable
    data class Dimensions(val width: Int, val height: Int)

    private class BurstTask(
        val index: Int,
        val countdownBitmap: Bitmap,
        val trainerInactiveBitmap: Bitmap,
        val timestamp: Long
    )

    private class BurstSessionState(
        val sessionId: String,
        val sessionDir: File,
        val trainerInactiveDir: File,
        val triggerTimestamp: Long,
        val cropRect: Rect,
        val trainerInactiveCropRect: Rect,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val channel: Channel<BurstTask>,
        val writerJob: Job,
        val savedFrames: MutableList<BurstFrameRecord> = mutableListOf()
    )

    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG) return
        applicationContext = context.applicationContext
    }

    /**
     * Starts a new burst session, creating a dedicated channel and background writer.
     */
    fun startBurst(
        sessionId: String,
        triggerTimestamp: Long,
        cropRect: Rect,
        trainerInactiveCropRect: Rect,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        if (!BuildConfig.DEBUG || sessionId.isEmpty()) return
        val context = applicationContext ?: return

        val diagnosticsDir = File(context.filesDir, "diagnostics/countdown_samples")
        val sessionDir = File(diagnosticsDir, "session_$sessionId")
        if (!sessionDir.exists()) sessionDir.mkdirs()

        val trainerInactiveDir = File(sessionDir, "trainer_inactive")
        if (!trainerInactiveDir.exists()) trainerInactiveDir.mkdirs()

        val channel = Channel<BurstTask>(QUEUE_CAPACITY)
        val savedFrames = mutableListOf<BurstFrameRecord>()
        
        val writerJob = scope.launch {
            for (task in channel) {
                saveBurstFrame(sessionDir, trainerInactiveDir, task, savedFrames)
            }
        }

        sessionStates[sessionId] = BurstSessionState(
            sessionId, sessionDir, trainerInactiveDir, triggerTimestamp, cropRect, trainerInactiveCropRect,
            sourceWidth, sourceHeight, channel, writerJob, savedFrames
        )
        
        Log.d(TAG, "Started burst session for $sessionId with trainer inactive region: $trainerInactiveCropRect")
    }

    /**
     * Attempts to record a single frame in the burst.
     * Performs the crop itself to ensure single-copy ownership.
     */
    fun record(
        sessionId: String,
        index: Int,
        sourceBitmap: Bitmap,
        cropRect: Rect,
        trainerInactiveCropRect: Rect,
        timestamp: Long
    ) {
        if (!BuildConfig.DEBUG) return
        val state = sessionStates[sessionId] ?: return

        val sourceWidth = sourceBitmap.width
        val sourceHeight = sourceBitmap.height

        // Validate both rectangles against source dimensions
        if (!isValidRect(cropRect, sourceWidth, sourceHeight)) {
            Log.w(TAG, "Skipping frame $index in session $sessionId: invalid countdown cropRect $cropRect for source ${sourceWidth}x${sourceHeight}")
            return
        }
        if (!isValidRect(trainerInactiveCropRect, sourceWidth, sourceHeight)) {
            Log.w(TAG, "Skipping frame $index in session $sessionId: invalid trainerInactiveCropRect $trainerInactiveCropRect for source ${sourceWidth}x${sourceHeight}")
            return
        }

        var countdownCropped: Bitmap? = null
        var trainerInactiveCropped: Bitmap? = null

        try {
            countdownCropped = Bitmap.createBitmap(
                sourceBitmap, 
                cropRect.left, cropRect.top, 
                cropRect.width(), cropRect.height()
            )
            trainerInactiveCropped = Bitmap.createBitmap(
                sourceBitmap,
                trainerInactiveCropRect.left, trainerInactiveCropRect.top,
                trainerInactiveCropRect.width(), trainerInactiveCropRect.height()
            )
            
            val task = BurstTask(index, countdownCropped, trainerInactiveCropped, timestamp)
            val result = state.channel.trySend(task)
            
            if (result.isFailure) {
                Log.w(TAG, "Burst queue full for $sessionId. Dropping frame $index")
                countdownCropped.recycle()
                trainerInactiveCropped.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop or queue burst frame $index", e)
            countdownCropped?.recycle()
            trainerInactiveCropped?.recycle()
        }
    }

    private fun isValidRect(rect: Rect, sourceWidth: Int, sourceHeight: Int): Boolean {
        return rect.left >= 0 && rect.top >= 0 &&
                rect.right <= sourceWidth && rect.bottom <= sourceHeight &&
                rect.width() > 0 && rect.height() > 0
    }

    /**
     * Finalizes the burst, flushing the queue and writing the manifest.
     * This is intended to be called within a NonCancellable block.
     */
    suspend fun finishBurst(sessionId: String) {
        if (!BuildConfig.DEBUG) return
        val state = sessionStates.remove(sessionId) ?: return

        Log.d(TAG, "Finishing burst for $sessionId...")
        
        withContext(NonCancellable) {
            state.channel.close()
            state.writerJob.join() // Wait for flush
            
            writeManifest(state)
        }
        
        Log.d(TAG, "Burst session $sessionId finalized.")
    }

    private fun saveBurstFrame(
        sessionDir: File,
        trainerInactiveDir: File,
        task: BurstTask,
        savedFrames: MutableList<BurstFrameRecord>
    ) {
        try {
            val fileName = String.format(Locale.ROOT, "burst_%03d.png", task.index)
            val countdownFile = File(sessionDir, fileName)
            val trainerInactiveFile = File(trainerInactiveDir, fileName)

            var success = false
            FileOutputStream(countdownFile).use { out ->
                success = task.countdownBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (success) {
                FileOutputStream(trainerInactiveFile).use { out ->
                    success = task.trainerInactiveBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            if (success) {
                synchronized(savedFrames) {
                    savedFrames.add(BurstFrameRecord(task.index, task.timestamp))
                }
            } else {
                Log.e(TAG, "Failed to compress burst frame ${task.index}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving burst frame ${task.index}", e)
        } finally {
            task.countdownBitmap.recycle()
            task.trainerInactiveBitmap.recycle()
        }
    }

    private fun writeManifest(state: BurstSessionState) {
        try {
            val manifest = BurstManifest(
                sessionId = state.sessionId,
                triggerTimestamp = state.triggerTimestamp,
                cropRect = RectData(
                    state.cropRect.left, state.cropRect.top, 
                    state.cropRect.right, state.cropRect.bottom
                ),
                trainerInactivePokemonCropRect = RectData(
                    state.trainerInactiveCropRect.left, state.trainerInactiveCropRect.top,
                    state.trainerInactiveCropRect.right, state.trainerInactiveCropRect.bottom
                ),
                sourceDimensions = Dimensions(state.sourceWidth, state.sourceHeight),
                frames = state.savedFrames.toList()
            )
            
            val jsonString = Json.encodeToString(manifest)
            val manifestFile = File(state.sessionDir, "burst_manifest.json")
            manifestFile.writeText(jsonString)
            
            Log.d(TAG, "Wrote burst manifest with ${state.savedFrames.size} frames for ${state.sessionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write burst manifest for ${state.sessionId}", e)
        }
    }
}
