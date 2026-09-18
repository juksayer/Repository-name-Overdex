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
        // Pokémon GO draws names in muted blue over a nearly white badge. Turn that
        // narrow, purpose-specific crop into black text on white before scaling so
        // ML Kit sees glyph strokes instead of translucent UI color.
        val highContrast = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val luma = (android.graphics.Color.red(color) * 299 +
                    android.graphics.Color.green(color) * 587 +
                    android.graphics.Color.blue(color) * 114) / 1000
                highContrast.setPixel(x, y, if (luma < 190) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        val enlarged = Bitmap.createScaledBitmap(highContrast, bitmap.width * 6, bitmap.height * 6, false)
        highContrast.recycle()
        val image = InputImage.fromBitmap(enlarged, 0)
        return try {
            val result = recognizer.process(image).await()
            val rawText = result.text
            val rawTextEscaped = rawText.replace("\n", "\\n")
            Log.d("SPECIES_NAME_RECOGNIZER", "source=${bitmap.width}x${bitmap.height} ocr=high-contrast-${enlarged.width}x${enlarged.height} text=\"$rawTextEscaped\"")

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
        } finally {
            enlarged.recycle()
        }
    }
}
