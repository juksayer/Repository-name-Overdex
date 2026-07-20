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

    private fun normalizeSpeciesCandidate(text: String): String {
        return text
            .replace(Regex("^#\\d+"), "")   // Remove "#15"
            .replace(Regex("^\\d+"), "")    // Remove "15"
            .trim()
    }

    /**
     * Extracts the Pokémon species name from the provided bitmap.
     * 
     * @param bitmap The cropped image of the species name region.
     * @return A [RecognitionResult] containing the normalized species name.
     */
    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            
            // The species name is usually the primary text in this region.
            val speciesName = result.textBlocks
                .flatMap { it.lines }
                .map { normalizeSpeciesCandidate(it.text) }
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
