package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.RecognitionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Specialized recognizer for identifying Pokémon entry/re-entry announcements.
 *
 * This component performs pattern matching against known announcement framing
 * (e.g., "GO, [NAME]!") and verifies the candidate species against an assigned
 * vocabulary.
 */
object AnnouncementRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val GO_PATTERN = Regex("^GO,\\s*(.+)!$", RegexOption.IGNORE_CASE)
    private val SENT_OUT_PATTERN = Regex("^SENT OUT\\s*(.+)$", RegexOption.IGNORE_CASE)

    /**
     * Scans the provided bitmap for Pokémon announcements.
     *
     * @param bitmap The cropped announcement region.
     * @param vocabulary The set of domain-valid species names (uppercase).
     * @return A [RecognitionResult] containing the full raw OCR string if a pattern is matched.
     */
    suspend fun recognize(bitmap: Bitmap, vocabulary: Set<String>): RecognitionResult<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            val result = recognizer.process(image).await()
            processText(result.text, vocabulary)
        } catch (e: Exception) {
            android.util.Log.e("ANNOUNCEMENT_RECOGNIZER", "Recognition failed", e)
            RecognitionResult(null, 0.0f, "AnnouncementRecognizer")
        }
    }

    /**
     * Internal logic for processing recognized text against a vocabulary.
     * Exposed for unit testing.
     */
    fun processText(text: String, vocabulary: Set<String>): RecognitionResult<String> {
        val rawText = text.trim()

        if (rawText.isEmpty()) {
            return RecognitionResult(null, 0.0f, "AnnouncementRecognizer")
        }

        // 1. Try to extract candidate from patterns
        val candidate = extractCandidate(rawText)

        return if (candidate != null) {
            val normalizedCandidate = candidate.replace(" ", "").uppercase()

            // 2. Determine confidence based on vocabulary membership
            val confidence = if (vocabulary.contains(normalizedCandidate)) {
                1.0f // Exact match: Authoritative evidence
            } else {
                0.3f // Pattern matched but species unknown: Uncertain evidence
            }

            RecognitionResult(
                value = rawText,
                confidence = confidence,
                recognizer = "AnnouncementRecognizer"
            )
        } else {
            // No announcement pattern detected
            RecognitionResult(null, 0.0f, "AnnouncementRecognizer")
        }
    }

    private fun extractCandidate(text: String): String? {
        val goMatch = GO_PATTERN.find(text)
        if (goMatch != null) return goMatch.groupValues[1]

        val sentOutMatch = SENT_OUT_PATTERN.find(text)
        if (sentOutMatch != null) return sentOutMatch.groupValues[1]

        return null
    }
}
