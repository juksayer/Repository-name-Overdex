package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.observation.RecognitionResult

object AnnouncementRecognizer {

    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        return try {
            val directText = SharedLatinTextRecognizer.readText(bitmap)
            val rawText = directText.takeIf { it.isNotBlank() } ?: recognizeHighContrast(bitmap)
            val rawTextEscaped = rawText.replace("\n", "\\n")
            Log.d("ANNOUNCEMENT_RECOGNIZER", "source=${bitmap.width}x${bitmap.height} text=\"$rawTextEscaped\"")

            RecognitionResult(
                value = rawText,
                confidence = null,
                recognizer = "AnnouncementRecognizer"
            )
        } catch (e: Exception) {
            Log.e("ANNOUNCEMENT_RECOGNIZER", "Recognition failed", e)

            RecognitionResult(
                value = null,
                confidence = null,
                recognizer = "AnnouncementRecognizer"
            )
        }
    }

    /**
     * Keeps the native-color and high-contrast readings independent. A noisy,
     * non-empty native reading must not prevent the alternate view from seeing
     * a short fading phrase.
     */
    suspend fun recognizeCandidates(bitmap: Bitmap, directText: String? = null): List<String> {
        val direct = directText ?: SharedLatinTextRecognizer.readText(bitmap)
        val highContrast = recognizeHighContrast(bitmap)
        return listOf(direct, highContrast)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    private suspend fun recognizeHighContrast(bitmap: Bitmap): String {
        val thresholded = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val luma = (android.graphics.Color.red(color) * 299 +
                    android.graphics.Color.green(color) * 587 +
                    android.graphics.Color.blue(color) * 114) / 1000
                thresholded.setPixel(x, y, if (luma < 185) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        val enlarged = Bitmap.createScaledBitmap(thresholded, bitmap.width * 2, bitmap.height * 2, false)
        thresholded.recycle()
        return try {
            SharedLatinTextRecognizer.readText(enlarged)
        } finally {
            enlarged.recycle()
        }
    }

}
