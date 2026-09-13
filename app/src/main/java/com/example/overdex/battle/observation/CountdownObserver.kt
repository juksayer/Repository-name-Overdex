package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.overdex.BuildConfig
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.observation.ObservationInput
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.sample
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/**
 * Production observer responsible for observing raw OCR text in the countdown region
 * and submitting raw testimony to the canonical Timeline.
 */
class CountdownObserver(
    private val input: ObservationInput,
    private val calibration: BattleCalibration,
    override val observerId: ObserverId =
        ObserverId("COUNTDOWN_OBSERVER", ObserverSource.SCREEN_CAPTURE),

    override val name: String = "Countdown Observer"
) : Observer {

    private companion object {
        private const val COUNTDOWN_GLYPH_BURST_TAG = "COUNTDOWN_GLYPH_BURST"
    }

    private var scope: CoroutineScope? = null
    private var receiptSequence = 0L
    private var previousReceiptNanoTime = 0L
    private var burstJob: Job? = null
    private var burstTriggered = false

    override fun start(match: Match) {
        if (scope != null) return
        Log.d("COUNTDOWN", "start()")
        receiptSequence = 0L
        previousReceiptNanoTime = 0L
        burstTriggered = false

        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        val sourceId = SourceId(observerId.id)
        match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())

        newScope.launch {
            Log.d("COUNTDOWN", "waiting for frames")
            
            // Localize session-specific state to this collection coroutine
            val currentSessionId = if (BuildConfig.DEBUG) {
                CountdownSampleRecorder.startSession(match.matchId)
            } else ""
            var attemptsInSession = 0

            try {
                input.supply { bitmap ->
                    Log.d("COUNTDOWN", "bitmap received")
                    match.incrementFrameCount()

                    val timestamp = System.currentTimeMillis()
                    val receiptNano = System.nanoTime()
                    receiptSequence++
                    val intervalMs = if (previousReceiptNanoTime == 0L) 0L else (receiptNano - previousReceiptNanoTime) / 1_000_000L
                    previousReceiptNanoTime = receiptNano

                    match.custody.submitInputAvailability(sourceId, true, timestamp)

                    Log.d("COUNTDOWN", "Calibration: ${calibration.isCalibrated()}")
                    if (!calibration.isCalibrated()) {
                        Log.d("COUNTDOWN_TIMING", "seq=$receiptSequence | intervalMs=$intervalMs | Attempt prevented: not calibrated")
                    } else {
                        val region = calibration.countdownRegion
                        val sourceWidth = bitmap.width
                        val sourceHeight = bitmap.height

                        val left = (region.x * sourceWidth)
                            .toInt()
                            .coerceIn(0, sourceWidth - 1)

                        val top = (region.y * sourceHeight)
                            .toInt()
                            .coerceIn(0, sourceHeight - 1)

                        val w = (region.width * sourceWidth)
                            .toInt()
                            .coerceAtMost(sourceWidth - left)

                        val h = (region.height * sourceHeight)
                            .toInt()
                            .coerceAtMost(sourceHeight - top)

                        if (w < 32 || h < 32) {
                            Log.w("CountdownObserver", "Crop dimensions too small for ML Kit: ${w}x${h}")
                        } else {
                            val cropped = try {
                                Bitmap.createBitmap(bitmap, left, top, w, h)
                            } catch (_: Exception) {
                                null
                            }

                            if (cropped != null) {
                                val startTime = System.nanoTime()
                                val recognitionResult = CountdownRecognizer.recognize(cropped)
                                val durationMs = (System.nanoTime() - startTime) / 1_000_000L
                                
                                val value = recognitionResult.value
                                val outcome = when {
                                    value == null -> "FAILURE"
                                    value.isEmpty() -> "EMPTY"
                                    else -> "TEXT"
                                }

                                if (BuildConfig.DEBUG && attemptsInSession < 60) {
                                    attemptsInSession++
                                    
                                    try {
                                        val glyphResult = CountdownGlyphMatcher.match(cropped)
                                        Log.d("COUNTDOWN_GLYPH", "seq=$receiptSequence | candidate=${glyphResult.candidate} | similarity=${String.format("%.3f", glyphResult.similarity)}")
                                    } catch (e: Exception) {
                                        Log.e("COUNTDOWN_GLYPH", "Matcher failure for seq=$receiptSequence", e)
                                    }

                                    CountdownSampleRecorder.record(
                                        sessionId = currentSessionId,
                                        sequence = receiptSequence,
                                        bitmap = cropped,
                                        receiptTimestamp = timestamp,
                                        cropRect = Rect(left, top, left + w, top + h),
                                        sourceWidth = sourceWidth,
                                        sourceHeight = sourceHeight,
                                        durationMs = durationMs,
                                        outcome = outcome,
                                        rawValue = value
                                    )
                                }

                                val escapedText = escapeForLog(value)
                                Log.d("COUNTDOWN_TIMING", "seq=$receiptSequence | intervalMs=$intervalMs | crop=${cropped.width}x${cropped.height} | durationMs=$durationMs | outcome=$outcome | text=\"$escapedText\"")

                                if (value != null) {
                                    match.custody.submitTestimony(
                                        sourceId = sourceId,
                                        payload = RawTestimony(value),
                                        timestamp = timestamp,
                                        confidence = recognitionResult.confidence
                                    )
                                }

                                if (BuildConfig.DEBUG && !burstTriggered && 
                                    value?.contains("VS", ignoreCase = true) == true) {
                                    burstTriggered = true
                                    triggerDiagnosticBurst(currentSessionId, bitmap)
                                }
                            }
                        }
                    }
                }
            } finally {
                // Ensure session shutdown regardless of how collection ends
                if (BuildConfig.DEBUG && currentSessionId.isNotEmpty()) {
                    burstJob?.cancel()
                    CountdownSampleRecorder.stopSession(currentSessionId)
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun triggerDiagnosticBurst(sessionId: String, triggerBitmap: Bitmap) {
        val observerScope = scope ?: return
        
        val sourceWidth = triggerBitmap.width
        val sourceHeight = triggerBitmap.height
        
        // Countdown region
        val region = calibration.countdownRegion
        val left = (region.x * sourceWidth).toInt().coerceIn(0, sourceWidth - 1)
        val top = (region.y * sourceHeight).toInt().coerceIn(0, sourceHeight - 1)
        val w = (region.width * sourceWidth).toInt().coerceAtMost(sourceWidth - left)
        val h = (region.height * sourceHeight).toInt().coerceAtMost(sourceHeight - top)
        val triggerCropRect = Rect(left, top, left + w, top + h)

        // Trainer Inactive Pokémon region
        val inactiveRegion = calibration.trainerInactivePokemonRegion
        val inLeft = (inactiveRegion.x * sourceWidth).toInt().coerceIn(0, sourceWidth - 1)
        val inTop = (inactiveRegion.y * sourceHeight).toInt().coerceIn(0, sourceHeight - 1)
        val inW = (inactiveRegion.width * sourceWidth).toInt().coerceAtMost(sourceWidth - inLeft)
        val inH = (inactiveRegion.height * sourceHeight).toInt().coerceAtMost(sourceHeight - inTop)
        val trainerInactiveCropRect = Rect(inLeft, inTop, inLeft + inW, inTop + inH)

        Log.d("COUNTDOWN_BURST", "Trainer Inactive Pokémon crop rect: $trainerInactiveCropRect")

        burstJob = observerScope.launch {
            Log.d("COUNTDOWN_BURST", "Burst triggered for $sessionId")
            val triggerTime = System.currentTimeMillis()
            
            CountdownBurstRecorder.startBurst(
                sessionId = sessionId,
                triggerTimestamp = triggerTime,
                cropRect = triggerCropRect,
                trainerInactiveCropRect = trainerInactiveCropRect,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )

            var frameIndex = 1
            try {
                // Subscribing independently to the frame stream
                DroidballService.frames
                    .sample(100.milliseconds)
                    .collect { bitmap ->
                        val srcW = bitmap.width
                        val srcH = bitmap.height
                        val isCropValid = triggerCropRect.left >= 0 &&
                                          triggerCropRect.top >= 0 &&
                                          triggerCropRect.right <= srcW &&
                                          triggerCropRect.bottom <= srcH &&
                                          triggerCropRect.width() > 0 &&
                                          triggerCropRect.height() > 0

                        if (isCropValid) {
                            var tempCrop: Bitmap? = null
                            try {
                                tempCrop = Bitmap.createBitmap(
                                    bitmap,
                                    triggerCropRect.left,
                                    triggerCropRect.top,
                                    triggerCropRect.width(),
                                    triggerCropRect.height()
                                )
                                val matchResult = CountdownGlyphMatcher.match(tempCrop)
                                val candidateStr = matchResult.candidate ?: "null"
                                val similarityStr = String.format(Locale.ROOT, "%.3f", matchResult.similarity)
                                Log.d(
                                    COUNTDOWN_GLYPH_BURST_TAG,
                                    "session=$sessionId | frame=$frameIndex | crop=${tempCrop.width}x${tempCrop.height} | candidate=$candidateStr | similarity=$similarityStr"
                                )
                            } catch (e: Exception) {
                                Log.e(COUNTDOWN_GLYPH_BURST_TAG, "Error matching glyph in burst for session $sessionId frame $frameIndex", e)
                            } finally {
                                tempCrop?.recycle()
                            }
                        } else {
                            Log.w(
                                COUNTDOWN_GLYPH_BURST_TAG,
                                "session=$sessionId | frame=$frameIndex | invalidCountdownCrop=${triggerCropRect.left},${triggerCropRect.top},${triggerCropRect.right},${triggerCropRect.bottom} | source=${srcW}x${srcH}"
                            )
                        }

                        if (BuildConfig.DEBUG) {
                            TrainerInactivePokemonVisibilityProbe.inspectAndLog(
                                sessionId = sessionId,
                                frameIndex = frameIndex,
                                sourceBitmap = bitmap,
                                cropRect = trainerInactiveCropRect
                            )
                        }

                        // Reuse geometry from trigger for consistency in the burst
                        CountdownBurstRecorder.record(
                            sessionId = sessionId,
                            index = frameIndex++,
                            sourceBitmap = bitmap,
                            cropRect = triggerCropRect,
                            trainerInactiveCropRect = trainerInactiveCropRect,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        if (frameIndex > 70) {
                            this@launch.cancel("Burst limit reached")
                        }
                    }
            } finally {
                withContext(NonCancellable) {
                    CountdownBurstRecorder.finishBurst(sessionId)
                }
            }
        }
    }

    override fun stop() {
        burstJob?.cancel()
        scope?.cancel("Observer stopped")
        scope = null
    }

    private fun escapeForLog(text: String?): String {
        if (text == null) return "null"
        return text
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
            .replace("\"", "\\\"")
    }
}
