package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Specialized recognizer for extracting the Shadow Bonus value (e.g., +3) from a move row.
 */
object ShadowBonusRecognizer {

    private const val TAG = "SHADOW_BONUS_REC"

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<Int>? {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            val result = recognizer.process(image).await()

            // ----------------------------------------------------------------
            // DEBUG: Dump exactly what ML Kit recognized.
            // ----------------------------------------------------------------
            Log.d(TAG, "========== RAW OCR ==========")
            Log.d(TAG, result.text)

            result.textBlocks.forEachIndexed { blockIndex, block ->
                Log.d(TAG, "Block $blockIndex:")
                block.lines.forEachIndexed { lineIndex, line ->
                    Log.d(TAG, "  Line $lineIndex: '${line.text}'")
                }
            }
            Log.d(TAG, "=============================")

            // Existing heuristic (unchanged)
            val plusNumber = result.textBlocks
                .flatMap { it.lines }
                .map { it.text.trim() }
                .firstOrNull {
                    it.startsWith("+") &&
                            it.drop(1).all { c -> c.isDigit() }
                }

            Log.d(TAG, "Matched value: $plusNumber")

            val shadowBonus = plusNumber?.drop(1)?.toIntOrNull()

            if (shadowBonus != null) {
                RecognitionResult(
                    value = shadowBonus,
                    confidence = 1.0f,
                    recognizer = "ShadowBonusRecognizer"
                )
            } else {
                Log.d(TAG, "No Shadow Bonus recognized.")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            null
        }
    }
}