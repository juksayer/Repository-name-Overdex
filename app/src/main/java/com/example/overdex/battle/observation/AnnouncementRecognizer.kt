package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.util.Log
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.observation.RecognitionResult
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.data.observation.OcrWordGeometry
import com.example.overdex.data.observation.OcrSourceContext
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrPriority

object AnnouncementRecognizer {

    /** Live announcement path: same warm recognizer, with auditable source filtering. */
    suspend fun recognizeEvidence(
        bitmap: Bitmap, context: OcrSourceContext, profile: OcrHeightProfile,
    ): List<OcrTextEvidence> {
        val direct = OcrWordGeometry.read(bitmap, bitmap, 0, "RAW", context, profile,
            priority = OcrPriority.TRANSIENT)
        // No extra OCR pass just because our own text was rejected. Native pixels
        // remain available for future treatments; this avoids growing the OCR queue.
        if (direct.rawText.isNotBlank()) return listOf(direct)
        val prepared = highContrastInput(bitmap)
        return try {
            listOf(direct, OcrWordGeometry.read(bitmap, prepared, 0, "THRESHOLD_185_2X", context, profile,
                priority = OcrPriority.TRANSIENT))
        } finally { prepared.recycle() }
    }

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
        val enlarged = highContrastInput(bitmap)
        return try { SharedLatinTextRecognizer.readText(enlarged) }
        finally { enlarged.recycle() }
    }

    private fun highContrastInput(bitmap: Bitmap): Bitmap {
        val thresholded = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
                val color = pixels[i]
                val luma = (android.graphics.Color.red(color) * 299 +
                    android.graphics.Color.green(color) * 587 +
                    android.graphics.Color.blue(color) * 114) / 1000
                pixels[i] = if (luma < 185) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        thresholded.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val enlarged = Bitmap.createScaledBitmap(thresholded, bitmap.width * 2, bitmap.height * 2, false)
        thresholded.recycle()
        return enlarged
    }

}
