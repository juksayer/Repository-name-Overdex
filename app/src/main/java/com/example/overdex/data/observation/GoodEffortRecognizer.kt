package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.model.observation.RecognitionResult

object GoodEffortRecognizer {

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        return try {
            val result = SharedLatinTextRecognizer.read(bitmap)
            val rawText = result.text
            val rawTextEscaped = rawText.replace("\n", "\\n")
            Log.d("GOOD_EFFORT_RECOGNIZER", "Bitmap: ${bitmap.width}x${bitmap.height} | OCR Text: \"$rawTextEscaped\"")

            RecognitionResult(
                value = rawText,
                confidence = null,
                recognizer = "GoodEffortRecognizer"
            )
        } catch (e: Exception) {
            Log.e("GOOD_EFFORT_RECOGNIZER", "Recognition failed", e)

            RecognitionResult(
                value = null,
                confidence = null,
                recognizer = "GoodEffortRecognizer"
            )
        }
    }
}
