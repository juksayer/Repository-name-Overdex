package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object YouWinRecognizer {

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            val result = recognizer.process(image).await()

            val rawText = result.text
            val rawTextEscaped = rawText.replace("\n", "\\n")
            val match = rawText.trim().uppercase().contains("YOU WIN")

            Log.d("YOU_WIN_PROBE", "Bitmap: ${bitmap.width}x${bitmap.height} | OCR Text: \"$rawTextEscaped\" | Match: $match")

            RecognitionResult(
                value = rawText,
                confidence = null,
                recognizer = "YouWinRecognizer"
            )
        } catch (e: Exception) {
            Log.e("YOU_WIN_RECOGNIZER", "Recognition failed", e)

            RecognitionResult(
                value = null,
                confidence = null,
                recognizer = "YouWinRecognizer"
            )
        }
    }
}
