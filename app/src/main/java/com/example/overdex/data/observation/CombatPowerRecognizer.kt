package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Specialized recognizer for extracting Combat Power (CP) from visual evidence.
 */
object CombatPowerRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts the numeric CP value from a bitmap.
     * Removes prefixes like "CP" or "cp" and ignores non-numeric characters.
     */
    suspend fun recognize(bitmap: Bitmap): RecognitionResult<Int> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
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
