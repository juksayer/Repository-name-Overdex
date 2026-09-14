package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.BuildConfig
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.SupportingMatchStart
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
        ObserverId(BattleWitnessContracts.countdownGlyph.witnessId, ObserverSource.SCREEN_CAPTURE),

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
                input.supplyFrames { frame ->
                    val bitmap = frame.bitmap
                    Log.d("COUNTDOWN", "bitmap received")
                    match.incrementFrameCount()

                    val timestamp = frame.capturedAtWallTimeMillis
                    val receiptNano = frame.capturedAtMonotonicTimeNanos
                    receiptSequence++
                    val intervalMs = if (previousReceiptNanoTime == 0L) 0L else (receiptNano - previousReceiptNanoTime) / 1_000_000L
                    previousReceiptNanoTime = receiptNano

                    match.custody.submitInputAvailability(sourceId, true, timestamp)

                    Log.d("COUNTDOWN", "Calibration: ${calibration.isCalibrated()}")
                    if (!calibration.isCalibrated()) {
                        Log.d("COUNTDOWN_TIMING", "seq=$receiptSequence | intervalMs=$intervalMs | Attempt prevented: not calibrated")
                    } else {
                        val resolvedCountdown = BattleCropContracts.countdownGlyph.resolve(calibration, bitmap)

                        if (resolvedCountdown == null) {
                            Log.w("CountdownObserver", "Countdown glyph crop is unavailable or smaller than 32px")
                        } else {
                            val cropped = resolvedCountdown.bitmap
                            val cropProvenance = resolvedCountdown.provenance
                            try {
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
                                        cropRect = cropProvenance.bounds.asRect(),
                                        sourceWidth = cropProvenance.sourceWidth,
                                        sourceHeight = cropProvenance.sourceHeight,
                                        durationMs = durationMs,
                                        outcome = outcome,
                                        rawValue = value
                                    )
                                }

                                val escapedText = escapeForLog(value)
                                Log.d("COUNTDOWN_TIMING", "seq=$receiptSequence | intervalMs=$intervalMs | crop=${cropped.width}x${cropped.height} | durationMs=$durationMs | outcome=$outcome | text=\"$escapedText\"")

                                if (BuildConfig.DEBUG && !burstTriggered && 
                                    value?.contains("VS", ignoreCase = true) == true) {
                                    burstTriggered = true
                                    triggerDiagnosticBurst(currentSessionId, bitmap, match, sourceId)
                                }
                            } finally {
                                cropped.recycle()
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
    private fun triggerDiagnosticBurst(sessionId: String, triggerBitmap: Bitmap, match: Match, sourceId: SourceId) {
        val observerScope = scope ?: return
        
        val sourceWidth = triggerBitmap.width
        val sourceHeight = triggerBitmap.height
        val triggerCropRect = BattleCropContracts.countdownGlyph.resolveRect(
            calibration, sourceWidth, sourceHeight
        ) ?: return
        val trainerInactiveCropRect = BattleCropContracts.trainerInactiveTimerOverlayClearance.resolveRect(
            calibration, sourceWidth, sourceHeight
        ) ?: return

        Log.d("COUNTDOWN_BURST", "Trainer Inactive Pokémon crop rect: $trainerInactiveCropRect")

        val witnessedGlyphs = mutableSetOf<String>()

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
                    .collect { frame ->
                        val bitmap = frame.bitmap
                        val resolvedCountdown = BattleCropContracts.countdownGlyph.resolve(calibration, bitmap)

                        if (resolvedCountdown != null) {
                            val tempCrop = resolvedCountdown.bitmap
                            try {
                                val matchResult = CountdownGlyphMatcher.match(tempCrop)
                                val candidate = matchResult.candidate
                                if (candidate != null && witnessedGlyphs.add(candidate)) {
                                    match.custody.submitTestimony(
                                        sourceId = sourceId,
                                        payload = CountdownGlyphWitnessed(
                                            glyph = candidate,
                                            similarity = matchResult.similarity,
                                            frameIndex = frameIndex,
                                            cropProvenance = resolvedCountdown.provenance
                                        ),
                                        timestamp = frame.capturedAtWallTimeMillis,
                                        confidence = null,
                                        evidenceReferences = emptyList(),
                                        monotonicTimeNanos = frame.capturedAtMonotonicTimeNanos
                                    )

                                    val witnessLog = String.format(
                                        Locale.ROOT,
                                        "session=%s | frame=%d | glyph=%s | similarity=%.3f | payload=CountdownGlyphWitnessed",
                                        sessionId,
                                        frameIndex,
                                        candidate,
                                        matchResult.similarity
                                    )
                                    Log.d("COUNTDOWN_GLYPH_WITNESS", witnessLog)
                                }

                                val candidateStr = candidate ?: "null"
                                val similarityStr = String.format(Locale.ROOT, "%.3f", matchResult.similarity)
                                Log.d(
                                    COUNTDOWN_GLYPH_BURST_TAG,
                                    "session=$sessionId | frame=$frameIndex | crop=${tempCrop.width}x${tempCrop.height} | candidate=$candidateStr | similarity=$similarityStr"
                                )
                            } catch (e: Exception) {
                                Log.e(COUNTDOWN_GLYPH_BURST_TAG, "Error matching glyph in burst for session $sessionId frame $frameIndex", e)
                            } finally {
                                tempCrop.recycle()
                            }
                        } else {
                            Log.w(
                                COUNTDOWN_GLYPH_BURST_TAG,
                                "session=$sessionId | frame=$frameIndex | unavailableCountdownGlyphCrop | source=${bitmap.width}x${bitmap.height}"
                            )
                        }

                        val resolvedTrainerInactive = BattleCropContracts.trainerInactiveTimerOverlayClearance
                            .resolveRect(calibration, bitmap.width, bitmap.height)

                        val clearanceResult = if (BuildConfig.DEBUG && resolvedTrainerInactive != null) {
                            TrainerInactivePokemonTimerOverlayProbe.inspectAndLog(
                                sessionId = sessionId,
                                frameIndex = frameIndex,
                                sourceBitmap = bitmap,
                                cropRect = resolvedTrainerInactive
                            )
                        } else null

                        if (BuildConfig.DEBUG && clearanceResult != null) {
                            match.custody.submitTestimony(
                                sourceId = SourceId(BattleWitnessContracts.trainerInactiveTimerOverlayClearance.witnessId),
                                payload = SupportingMatchStart(
                                    frameIndex = clearanceResult.frameIndex,
                                    upperColorfulPixelFraction = clearanceResult.upperColorfulPixelFraction,
                                    lowerColorfulPixelFraction = clearanceResult.lowerColorfulPixelFraction
                                ),
                                timestamp = frame.capturedAtWallTimeMillis,
                                confidence = null,
                                evidenceReferences = emptyList(),
                                monotonicTimeNanos = frame.capturedAtMonotonicTimeNanos
                            )

                            val matchStartLog = String.format(
                                Locale.ROOT,
                                "session=%s | frame=%d | payload=SupportingMatchStart | basis=TRAINER_INACTIVE_TIMER_OVERLAY_CLEARANCE | upperColorfulPixelFraction=%.3f | lowerColorfulPixelFraction=%.3f",
                                sessionId,
                                clearanceResult.frameIndex,
                                clearanceResult.upperColorfulPixelFraction,
                                clearanceResult.lowerColorfulPixelFraction
                            )
                            Log.d("MATCH_START_SUPPORT", matchStartLog)
                        }

                        val countdownRect = BattleCropContracts.countdownGlyph
                            .resolveRect(calibration, bitmap.width, bitmap.height)
                        if (countdownRect != null && resolvedTrainerInactive != null) {
                            CountdownBurstRecorder.record(
                                sessionId = sessionId,
                                index = frameIndex,
                                sourceBitmap = bitmap,
                                cropRect = countdownRect,
                                trainerInactiveCropRect = resolvedTrainerInactive,
                                timestamp = frame.capturedAtWallTimeMillis
                            )
                        }
                        frameIndex++
                        
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
