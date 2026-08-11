package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object GoodEffortRecognizer {

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            val result = recognizer.process(image).await()
            Log.d("GOOD_EFFORT_RECOGNIZER", "OCR text: ${result.text}")

            val text = result.text
                .trim()
                .uppercase()
                .replace(Regex("\\s+"), " ")

            val match = text.contains("GOOD EFFORT")

            RecognitionResult(
                value = if (match) "GOOD EFFORT!" else null,
                confidence = if (match) 1.0f else 0.0f,
                recognizer = "GoodEffortRecognizer"
            )
        } catch (e: Exception) {
            Log.e("GOOD_EFFORT_RECOGNIZER", "Recognition failed", e)

            RecognitionResult(
                null,
                0.0f,
                "GoodEffortRecognizer"
            )
        }
    }
}