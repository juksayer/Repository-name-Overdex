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
        val result = recognizer.process(image).await()
        val recognizedText = result.text
            .trim()
            .replace(" ", "")
            .uppercase()
        //.replace(Regex("\\s+"), "")


        if (recognizedText !in setOf("3", "2", "1", "GO")) {
            return RecognitionResult(
                value = null,
                confidence = 0.0f,
                recognizer = "CountdownRecognizer"
            )
        }
        return RecognitionResult(
            value = recognizedText,
            confidence = if (recognizedText.isNotEmpty()) 1.0f else 0.0f,
            recognizer = "CountdownRecognizer"
        )


    }
}

private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)