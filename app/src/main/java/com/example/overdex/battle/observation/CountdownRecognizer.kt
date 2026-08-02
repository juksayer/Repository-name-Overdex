package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.common.InputImage


object CountdownRecognizer {

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)

        TODO("Git #261")
    }
}

private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)