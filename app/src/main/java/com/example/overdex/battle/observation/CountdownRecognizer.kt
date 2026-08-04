package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await


object CountdownRecognizer {

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            val recognizedText = result.text
                .trim()
                .replace(" ", "")
                .uppercase()

            val targets = setOf("VS", "GETREADY", "3", "2", "1", "GO")
            
            if (recognizedText in targets) {
                RecognitionResult(
                    value = recognizedText,
                    confidence = 1.0f,
                    recognizer = "CountdownRecognizer"
                )
            } else {
                RecognitionResult(
                    value = if (recognizedText.isNotEmpty()) recognizedText else null,
                    confidence = 0.0f,
                    recognizer = "CountdownRecognizer"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("COUNTDOWN_RECOGNIZER", "Recognition failed", e)
            RecognitionResult(null, 0.0f, "CountdownRecognizer")
        }
    }
}

private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)