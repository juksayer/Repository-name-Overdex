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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    private var scope: CoroutineScope? = null
    private var receiptSequence = 0L
    private var previousReceiptNanoTime = 0L

    override fun start(match: Match) {
        if (scope != null) return
        Log.d("COUNTDOWN", "start()")
        receiptSequence = 0L
        previousReceiptNanoTime = 0L

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
                            }
                        }
                    }
                }
            } finally {
                // Ensure session shutdown regardless of how collection ends
                if (BuildConfig.DEBUG && currentSessionId.isNotEmpty()) {
                    CountdownSampleRecorder.stopSession(currentSessionId)
                }
            }
        }
    }

    override fun stop() {
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
