package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Specialized recognizer for extracting the opponent Pokémon species name from the screen region.
 */
object SpeciesNameRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts the opponent Pokémon species name from the provided bitmap.
     * 
     * @param bitmap The cropped image of the opponent species name region.
     * @return A [RecognitionResult] containing the raw OCR text with unknown confidence.
     */
    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            val rawText = result.text
            val rawTextEscaped = rawText.replace("\n", "\\n")
            Log.d("SPECIES_NAME_RECOGNIZER", "Bitmap: ${bitmap.width}x${bitmap.height} | OCR Text: \"$rawTextEscaped\"")

            RecognitionResult(
                value = rawText,
                confidence = null,
                recognizer = "SpeciesNameRecognizer"
            )
        } catch (e: Exception) {
            Log.e("SPECIES_RECOGNIZER", "Recognition failed", e)
            RecognitionResult(
                value = null,
                confidence = null,
                recognizer = "SpeciesNameRecognizer"
            )
        }
    }
}
