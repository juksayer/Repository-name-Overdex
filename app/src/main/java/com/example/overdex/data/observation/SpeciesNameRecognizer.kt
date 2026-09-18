package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

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
        // Start with the exact path that proved itself in StartOvermon: the
        // unmodified, purpose-specific name strip goes directly to ML Kit. The
        // earlier binary transform was erasing anti-aliased blue letter edges.
        // Species labels are deliberately narrow strips (usually only 25 px
        // tall at the reference frame). ML Kit refuses images below 32 px on
        // either axis, so enlarge the untouched source before submitting it.
        // This keeps the anti-aliased glyph information that the old binary
        // preprocessing discarded.
        val rawSource = enlargedOriginal(bitmap)
        val rawText = try {
            recognizeText(rawSource)
        } catch (e: Exception) {
            Log.e("SPECIES_RECOGNIZER", "Raw-strip recognition failed", e)
            ""
        } finally {
            if (rawSource !== bitmap) rawSource.recycle()
        }
        if (rawText.isNotBlank()) {
            Log.d("SPECIES_NAME_RECOGNIZER", "source=${bitmap.width}x${bitmap.height} ocr=raw-strip text=\"${rawText.replace("\n", "\\n")}\"")
            return RecognitionResult(rawText, confidence = null, recognizer = "SpeciesNameRecognizer")
        }

        // Some captures are washed out. Keep the transformed pass as a fallback
        // rather than making it the only way species text can be read.
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
        return try {
            val fallbackText = recognizeText(enlarged)
            val rawTextEscaped = fallbackText.replace("\n", "\\n")
            Log.d("SPECIES_NAME_RECOGNIZER", "source=${bitmap.width}x${bitmap.height} ocr=high-contrast-${enlarged.width}x${enlarged.height} text=\"$rawTextEscaped\"")

            RecognitionResult(
                value = fallbackText,
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

    private suspend fun recognizeText(bitmap: Bitmap): String = recognizer.readText(bitmap)

    private fun enlargedOriginal(bitmap: Bitmap): Bitmap {
        val scale = maxOf(
            1f,
            32f / bitmap.width.coerceAtLeast(1),
            32f / bitmap.height.coerceAtLeast(1),
            2f
        )
        if (scale == 1f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(32),
            (bitmap.height * scale).toInt().coerceAtLeast(32),
            true
        )
    }
}
