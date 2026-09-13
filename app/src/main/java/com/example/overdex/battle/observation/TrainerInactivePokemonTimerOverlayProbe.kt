package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.example.overdex.BuildConfig
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class TimerOverlayClearanceResult(
    val frameIndex: Int,
    val upperColorfulPixelFraction: Float,
    val lowerColorfulPixelFraction: Float
)

/**
 * Debug-only diagnostic probe for the two inactive Pokémon box timer overlays.
 * Evaluates upper and lower slots independently for chroma and colorful pixel fraction
 * during diagnostic burst capture.
 * This is a measurement brick, not a Witness and not a battle-state claim.
 */
object TrainerInactivePokemonTimerOverlayProbe {
    private const val TAG = "TRAINER_INACTIVE_TIMER_PROBE"
    private const val COLORFUL_SPREAD_THRESHOLD = 40

    private class SessionProbeState(
        var armed: Boolean = false,
        var candidateEmitted: Boolean = false
    )

    private val sessionStates = ConcurrentHashMap<String, SessionProbeState>()

    fun inspectAndLog(
        sessionId: String,
        frameIndex: Int,
        sourceBitmap: Bitmap,
        cropRect: Rect
    ): TimerOverlayClearanceResult? {
        if (!BuildConfig.DEBUG) return null

        val sourceWidth = sourceBitmap.width
        val sourceHeight = sourceBitmap.height

        // Validate rectangle against source dimensions
        if (cropRect.left < 0 || cropRect.top < 0 ||
            cropRect.right > sourceWidth || cropRect.bottom > sourceHeight ||
            cropRect.width() <= 0 || cropRect.height() <= 0
        ) {
            Log.w(
                TAG,
                "session=$sessionId | frame=$frameIndex | WARNING: Invalid crop rectangle $cropRect for source dimensions ${sourceWidth}x${sourceHeight}"
            )
            return null
        }

        var sourceCrop: Bitmap? = null
        var clearanceResult: TimerOverlayClearanceResult? = null
        try {
            sourceCrop = Bitmap.createBitmap(
                sourceBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )

            val width = sourceCrop.width
            val height = sourceCrop.height
            val midY = height / 2

            // Upper slot: top half (0 to midY)
            val upperHeight = midY
            // Lower slot: bottom half (midY to height)
            val lowerHeight = height - midY

            if (width <= 0 || upperHeight <= 0 || lowerHeight <= 0) {
                Log.w(
                    TAG,
                    "session=$sessionId | frame=$frameIndex | WARNING: Invalid slot dimensions width=$width, upperHeight=$upperHeight, lowerHeight=$lowerHeight"
                )
                return null
            }

            // Extract pixels for upper slot
            val upperPixels = IntArray(width * upperHeight)
            sourceCrop.getPixels(upperPixels, 0, width, 0, 0, width, upperHeight)

            // Extract pixels for lower slot
            val lowerPixels = IntArray(width * lowerHeight)
            sourceCrop.getPixels(lowerPixels, 0, width, 0, midY, width, lowerHeight)

            val upperStats = calculateSlotStats(upperPixels)
            val lowerStats = calculateSlotStats(lowerPixels)

            val logMessage = String.format(
                Locale.ROOT,
                "session=%s | frame=%d | crop=%dx%d | upperMeanChroma=%.3f | upperColorfulPixelFraction=%.3f | lowerMeanChroma=%.3f | lowerColorfulPixelFraction=%.3f",
                sessionId,
                frameIndex,
                width,
                height,
                upperStats.meanChroma,
                upperStats.colorfulPixelFraction,
                lowerStats.meanChroma,
                lowerStats.colorfulPixelFraction
            )

            Log.d(TAG, logMessage)

            // Session-scoped paired-transition diagnostics
            val state = sessionStates.computeIfAbsent(sessionId) { SessionProbeState() }
            synchronized(state) {
                if (!state.candidateEmitted) {
                    if (!state.armed) {
                        if (upperStats.colorfulPixelFraction >= 0.85f && lowerStats.colorfulPixelFraction >= 0.85f) {
                            state.armed = true
                        }
                    } else {
                        if (upperStats.colorfulPixelFraction <= 0.55f && lowerStats.colorfulPixelFraction <= 0.30f) {
                            state.candidateEmitted = true
                            clearanceResult = TimerOverlayClearanceResult(
                                frameIndex = frameIndex,
                                upperColorfulPixelFraction = upperStats.colorfulPixelFraction,
                                lowerColorfulPixelFraction = lowerStats.colorfulPixelFraction
                            )
                            val candidateMsg = String.format(
                                Locale.ROOT,
                                "session=%s | frame=%d | candidateTimerOverlayClearance=true | upperColorfulPixelFraction=%.3f | lowerColorfulPixelFraction=%.3f",
                                sessionId,
                                frameIndex,
                                upperStats.colorfulPixelFraction,
                                lowerStats.colorfulPixelFraction
                            )
                            Log.d(TAG, candidateMsg)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "session=$sessionId | frame=$frameIndex | WARNING: Exception processing timer overlay probe crop $cropRect for source dimensions ${sourceWidth}x${sourceHeight}",
                e
            )
        } finally {
            sourceCrop?.recycle()
        }

        return clearanceResult
    }

    private data class SlotStats(
        val meanChroma: Float,
        val colorfulPixelFraction: Float
    )

    private fun calculateSlotStats(pixels: IntArray): SlotStats {
        val totalPixels = pixels.size
        if (totalPixels == 0) return SlotStats(0f, 0f)

        var cumulativeChroma = 0.0
        var colorfulPixelCount = 0

        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            val maxChannel = maxOf(r, g, b)
            val minChannel = minOf(r, g, b)
            val chromaSpread = maxChannel - minChannel

            cumulativeChroma += chromaSpread.toDouble() / 255.0

            if (chromaSpread >= COLORFUL_SPREAD_THRESHOLD) {
                colorfulPixelCount++
            }
        }

        val meanChroma = (cumulativeChroma / totalPixels).toFloat()
        val colorfulPixelFraction = colorfulPixelCount.toFloat() / totalPixels

        return SlotStats(meanChroma, colorfulPixelFraction)
    }
}
