package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.observation.RecognitionResult

/**
 * Recognizer for extracting raw OCR text from the countdown/announcement region.
 */
object CountdownRecognizer {

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        return try {
            val rawText = SharedLatinTextRecognizer.readText(bitmap)

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
