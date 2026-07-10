package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Specialized recognizer for extracting the Pokémon species name from the top of the summary screen.
 */
object SpeciesNameRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            
            // The species name is usually the primary text in this region.
            val speciesName = result.textBlocks
                .flatMap { it.lines }
                .map { it.text.trim() }
                .firstOrNull { it.isNotEmpty() }

            RecognitionResult(
                value = speciesName,
                confidence = if (speciesName != null) 1.0f else 0.0f,
                recognizer = "SpeciesNameRecognizer"
            )
        } catch (e: Exception) {
            android.util.Log.e("SPECIES_RECOGNIZER", "Recognition failed", e)
            RecognitionResult(null, 0.0f, "SpeciesNameRecognizer")
        }
    }
}
