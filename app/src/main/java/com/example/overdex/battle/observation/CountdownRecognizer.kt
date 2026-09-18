package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.data.observation.readText
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Recognizer for extracting raw OCR text from the countdown/announcement region.
 */
object CountdownRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        return try {
            val rawText = recognizer.readText(bitmap)

            RecognitionResult(
                value = rawText,
                confidence = null,
                recognizer = "CountdownRecognizer"
            )
        } catch (e: Exception) {
            android.util.Log.e("COUNTDOWN_RECOGNIZER", "Recognition failed", e)
            RecognitionResult(
                value = null,
                confidence = null,
                recognizer = "CountdownRecognizer"
            )
        }
    }
}
