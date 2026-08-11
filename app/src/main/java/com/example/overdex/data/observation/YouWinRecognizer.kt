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

            val text = result.text
                .trim()
                .uppercase()

            val match = text.contains("YOU WIN")

            RecognitionResult(
                value = if (match) "YOU WIN!" else null,
                confidence = if (match) 1.0f else 0.0f,
                recognizer = "YouWinRecognizer"
            )
        } catch (e: Exception) {
            Log.e("YOU_WIN_RECOGNIZER", "Recognition failed", e)

            RecognitionResult(
                null,
                0.0f,
                "YouWinRecognizer"
            )
        }
    }
}