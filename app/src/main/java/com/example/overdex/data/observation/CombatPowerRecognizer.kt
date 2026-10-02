package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.RecognitionResult

/**
 * Specialized recognizer for extracting Combat Power (CP) from visual evidence.
 */
object CombatPowerRecognizer {

    /**
     * Extracts the numeric CP value from the provided bitmap.
     * 
     * Removes prefixes like "CP" or "cp" and ignores all non-numeric characters
     * to isolate the combat power digits.
     * 
     * @param bitmap The cropped image of the CP region.
     * @return A [RecognitionResult] containing the parsed integer CP value.
     */
    suspend fun recognize(bitmap: Bitmap): RecognitionResult<Int> {
        return try {
            val result = SharedLatinTextRecognizer.read(bitmap)
            // Clean text: lowercase, remove "cp", filter digits
            val cleanText = result.text.lowercase()
                .replace("cp", "")
                .filter { it.isDigit() }
            
            val cp = cleanText.toIntOrNull()
            RecognitionResult(
                value = cp,
                confidence = if (cp != null) 1.0f else 0.0f,
                recognizer = "CombatPowerRecognizer"
            )
        } catch (e: Exception) {
            android.util.Log.e("CP_RECOGNIZER", "Recognition failed", e)
            RecognitionResult(null, 0.0f, "CombatPowerRecognizer")
        }
    }
}
