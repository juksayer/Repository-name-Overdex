package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Specialized recognizer for determining species name from the Candy Panel.
 * Extracts text like "Mewtwo Candy" or "Charizard Mega Energy" and isolates the species.
 */
object CandyPanelSpeciesRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            
            // Look for lines containing "Candy" or "Mega Energy"
            var detectedSpecies: String? = null
            
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.lowercase()
                    when {
                        text.contains(" candy") -> {
                            detectedSpecies = line.text.substring(0, text.indexOf(" candy")).trim()
                        }
                        text.contains(" mega energy") -> {
                            detectedSpecies = line.text.substring(0, text.indexOf(" mega energy")).trim()
                        }
                    }
                    if (detectedSpecies != null) break
                }
                if (detectedSpecies != null) break
            }

            RecognitionResult(
                value = detectedSpecies,
                confidence = if (detectedSpecies != null) 1.0f else 0.0f,
                recognizer = "CandyPanelSpeciesRecognizer"
            )
        } catch (e: Exception) {
            android.util.Log.e("CANDY_SPECIES_REC", "Recognition failed", e)
            RecognitionResult(null, 0.0f, "CandyPanelSpeciesRecognizer")
        }
    }
}
