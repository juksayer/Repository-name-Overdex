package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.example.overdex.BuildConfig
import java.util.Locale

/**
 * Debug-only **Trainer Inactive Pokémon visibility probe**.
 * This is a diagnostic measurement brick, not a Witness and not a battle-state claim.
 * It evaluates luminance and dark pixel fraction on cropped regions during diagnostic burst capture.
 *
 * Threshold justification:
 * candidateAppearanceOnset = darkPixelFraction >= 0.15f
 * - Before inactive cards appeared: ~0.000
 * - First fully formed cards: ~0.184
 * - Later frames through GO: 0.322–0.401
 */
object TrainerInactivePokemonVisibilityProbe {
    private const val TAG = "TRAINER_INACTIVE_PROBE"
    private const val DARK_THRESHOLD = 0.35f
    private const val VISIBILITY_THRESHOLD = 0.15f

    fun inspectAndLog(
        sessionId: String,
        frameIndex: Int,
        sourceBitmap: Bitmap,
        cropRect: Rect
    ) {
        if (!BuildConfig.DEBUG) return

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
            return
        }

        var cropped: Bitmap? = null
        try {
            cropped = Bitmap.createBitmap(
                sourceBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )

            val width = cropped.width
            val height = cropped.height
            val totalPixels = width * height

            if (totalPixels <= 0) {
                Log.w(
                    TAG,
                    "session=$sessionId | frame=$frameIndex | WARNING: Zero-area crop $cropRect for source dimensions ${sourceWidth}x${sourceHeight}"
                )
                return
            }

            val pixels = IntArray(totalPixels)
            cropped.getPixels(pixels, 0, width, 0, 0, width, height)

            var cumulativeLuminance = 0.0
            var darkPixelCount = 0

            for (pixel in pixels) {
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Standard luminance formula normalized to 0.0 - 1.0
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                cumulativeLuminance += luminance

                if (luminance <= DARK_THRESHOLD.toDouble()) {
                    darkPixelCount++
                }
            }

            val meanLuminance = cumulativeLuminance / totalPixels
            val darkPixelFraction = darkPixelCount.toFloat() / totalPixels
            val candidateAppearanceOnset = darkPixelFraction >= VISIBILITY_THRESHOLD

            val logMessage = String.format(
                Locale.ROOT,
                "session=%s | frame=%d | crop=%dx%d | meanLuminance=%.3f | darkPixelFraction=%.3f | candidateAppearanceOnset=%b",
                sessionId,
                frameIndex,
                width,
                height,
                meanLuminance,
                darkPixelFraction,
                candidateAppearanceOnset
            )

            Log.d(TAG, logMessage)

        } catch (e: Exception) {
            Log.e(
                TAG,
                "session=$sessionId | frame=$frameIndex | WARNING: Exception processing probe crop $cropRect for source dimensions ${sourceWidth}x${sourceHeight}",
                e
            )
        } finally {
            cropped?.recycle()
        }
    }
}
