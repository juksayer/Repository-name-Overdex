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

    /**
     * Recognizes the shadow bonus.
     * Returns a list containing the raw OCR output and the parsed bonus (if found).
     */
    suspend fun recognize(bitmap: Bitmap): List<RecognitionResult<*>> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val results = mutableListOf<RecognitionResult<*>>()

        return try {
            val result = recognizer.process(image).await()

            // 1. Expose Raw OCR Output
            results.add(
                RecognitionResult(
                    value = result.text.ifEmpty { "[EMPTY]" },
                    confidence = 1.0f,
                    recognizer = "ShadowRawOCR"
                )
            )

            // 2. Parse Embedded Shadow Bonus
            // Refined heuristic: Search for '+' followed by digits anywhere in each line
            val bonusRegex = Regex("""\+(\d+)""")
            var shadowBonus: Int? = null

            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val match = bonusRegex.find(line.text)
                    if (match != null) {
                        shadowBonus = match.groupValues[1].toIntOrNull()
                        if (shadowBonus != null) {
                            Log.d(TAG, "Matched embedded value: $shadowBonus from '${line.text}'")
                            break
                        }
                    }
                }
                if (shadowBonus != null) break
            }

            if (shadowBonus != null) {
                results.add(
                    RecognitionResult(
                        value = shadowBonus,
                        confidence = 1.0f,
                        recognizer = "ShadowBonusRecognizer"
                    )
                )
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            results
        }
    }
}
