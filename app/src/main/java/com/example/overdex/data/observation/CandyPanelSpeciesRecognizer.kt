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
object CandyPanelFamilyRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            
            // Look for lines containing "Candy" or "Mega Energy"
            var detectedFamily: String? = null
            
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.lowercase()
                    when {
                        text.contains(" candy") -> {
                            detectedFamily = line.text.substring(0, text.indexOf(" candy")).trim()
                        }
                        text.contains(" mega energy") -> {
                            detectedFamily = line.text.substring(0, text.indexOf(" mega energy")).trim()
                        }
                    }
                    if (detectedFamily != null) break
                }
                if (detectedFamily != null) break
            }

            RecognitionResult(
                value = detectedFamily,
                confidence = if (detectedFamily != null) 0.8f else 0.0f,
                recognizer = "CandyPanelFamilyRecognizer"
            )
        } catch (e: Exception) {
            android.util.Log.e("CANDY_FAMILY_REC", "Recognition failed", e)
            RecognitionResult(null, 0.0f, "CandyPanelFamilyRecognizer")
        }
    }
}
