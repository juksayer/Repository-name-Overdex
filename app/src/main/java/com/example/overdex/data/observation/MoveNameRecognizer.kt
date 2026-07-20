package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Generic recognizer for extracting a Move Name from a move row observation bundle.
 */
object MoveNameRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts the move name from the provided bitmap.
     * 
     * Assumes the move name is the primary text block in the row. It filters out
     * typical "noise" found in move rows such as damage numbers, "bonus" text,
     * and UI elements like "Power Up" or "Stardust".
     * 
     * @param bitmap The cropped image of a move row.
     * @return A [RecognitionResult] containing the extracted move name.
     */
    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            
            // Extract text and find the most likely move name.
            // Move names are generally at the start and alphabetic.
            val moveName = result.textBlocks
                .flatMap { it.lines }
                .map { it.text.trim() }
                .firstOrNull { text -> 
                    val lower = text.lowercase()
                    // Basic heuristic: ignore damage numbers, known suffixes, and Power Up button noise
                    text.isNotEmpty() && 
                    !text.all { it.isDigit() } && 
                    !lower.contains("bonus") &&
                    !lower.contains("power up") &&
                    !lower.contains("stardust") &&
                    !lower.contains("candy")
                }

            RecognitionResult(
                value = moveName,
                confidence = if (moveName != null) 1.0f else 0.0f,
                recognizer = "MoveNameRecognizer"
            )
        } catch (e: Exception) {
            android.util.Log.e("MOVE_RECOGNIZER", "Recognition failed", e)
            RecognitionResult(null, 0.0f, "MoveNameRecognizer")
        }
    }
}
