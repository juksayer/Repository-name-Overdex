package com.example.overdex.battle.collector

import android.graphics.Bitmap
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyCustody
import com.example.overdex.model.observation.ObservationInput
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Collector responsible for gathering evidence of the "Attack Incoming!" phenomenon.
 *
 * This component monitors visual input, performs local cropping of the announcement region,
 * and submits neutral testimony to custody when the phenomenon is detected.
 */
class AttackIncomingCollector(
    private val input: ObservationInput,
    private val custody: TestimonyCustody,
    private val sourceId: SourceId = SourceId("ATTACK_INCOMING_COLLECTOR"),
    // Internal dependencies allowed for testability while preserving production defaults
    private val crop: (Bitmap, Int, Int, Int, Int) -> Bitmap? = { b, x, y, w, h ->
        try {
            val right = (x + w).coerceAtMost(b.width)
            val bottom = (y + h).coerceAtMost(b.height)
            val actualW = (right - x).coerceAtLeast(0)
            val actualH = (bottom - y).coerceAtLeast(0)
            if (actualW > 0 && actualH > 0 && y < b.height) {
                Bitmap.createBitmap(b, x, y, actualW, actualH)
            } else null
        } catch (e: Exception) { null }
    },
    private val detect: suspend (Bitmap) -> String? = { bitmap ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        try {
            recognizer.process(image).await().text
        } catch (e: Exception) {
            null
        }
    }
) {
    private var scope: CoroutineScope? = null

    // Measured geometry: 2460 x 1080 in Portrait Orientation
    // Top: Y 710 Bottom: Y 870 Left: X 0 Right: X 1080
    private companion object {
        const val ANNOUNCEMENT_X = 0
        const val ANNOUNCEMENT_Y = 710
        const val ANNOUNCEMENT_W = 1080
        const val ANNOUNCEMENT_H = 160 // 870 - 710
    }

    /**
     * Starts the collection occupation and reports availability.
     */
    fun start() {
        if (scope != null) return
        
        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = newScope

        custody.submitAvailability(sourceId, true, System.currentTimeMillis())

        newScope.launch {
            input.supply { bitmap ->
                processFrame(bitmap)
            }
        }
    }

    /**
     * Stops the collection occupation and reports unavailability.
     */
    fun stop() {
        custody.submitAvailability(sourceId, false, System.currentTimeMillis())
        scope?.cancel()
        scope = null
    }

    private suspend fun processFrame(bitmap: Bitmap) {
        val timestamp = System.currentTimeMillis()
        custody.submitInputAvailability(sourceId, true, timestamp)

        val cropped = crop(bitmap, ANNOUNCEMENT_X, ANNOUNCEMENT_Y, ANNOUNCEMENT_W, ANNOUNCEMENT_H)

        if (cropped != null) {
            val text = detect(cropped)
            val normalizedText = text?.uppercase()?.trim() ?: ""
            
            // Remove spaces for comparison to handle OCR artifacts where spaces might be missed.
            val comparableText = normalizedText.replace(" ", "")

            if (comparableText.contains("ATTACKINCOMING")) {
                custody.submitTestimony(
                    sourceId = sourceId,
                    payload = RawTestimony("ATTACK_INCOMING"),
                    timestamp = timestamp,
                    confidence = 1.0f,
                    evidenceReferences = emptyList()
                )
            }
        }
    }
}
