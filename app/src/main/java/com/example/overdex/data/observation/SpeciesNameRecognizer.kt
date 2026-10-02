package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.overdex.model.observation.RecognitionResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads the small Pokémon GO badge-name strip.
 *
 * Each returned candidate is a distinct reading of the same preserved artifact.
 * Consumers retain the raw reads and resolve only against their own known-species
 * catalogue; this recognizer never turns a partial string into a Pokémon identity.
 */
object SpeciesNameRecognizer {
    // ML Kit's TextRecognizer is stateful. One serial lane prevents simultaneous
    // player/opponent reads from stalling behind each other; each side's caller
    // keeps only its latest durable badge crop while it waits.
    private val recognitionMutex = Mutex()
    private var warmed = false

    /** Initialize ML Kit during deployment, before an entry cue starts the latency budget. */
    suspend fun warmUp() = recognitionMutex.withLock {
        if (warmed) return@withLock
        SharedLatinTextRecognizer.warmUp()
        warmed = true
    }


    data class Candidate(
        val text: String,
        val treatment: String
    )

    /**
     * Runs complementary treatments because ML Kit can return a partial word from
     * the unmodified 25-pixel badge text. The padded threshold passes retain the
     * word's silhouette without changing the source crop or its evidence record.
     */
    suspend fun recognizeCandidates(
        bitmap: Bitmap,
        accepts: (String) -> Boolean = { false }
    ): List<Candidate> = recognitionMutex.withLock {
        val candidates = linkedSetOf<Candidate>()
        // The measured phone badges are under 32 px and their raw reads are truncated.
        // Start with the successful padded treatment; retain raw as an independent fallback.
        val treatments = if (bitmap.height < 64) listOf("THRESHOLD_190_PADDED", "RAW")
            else listOf("RAW", "THRESHOLD_190_PADDED")
        for (treatment in treatments) {
            val prepared = if (treatment == "RAW") rawInput(bitmap)
                else threshold(bitmap, cutoff = 190, padding = 12)
            recognize(treatment, prepared)?.let {
                candidates += Candidate(it, treatment)
                if (accepts(it)) return@withLock candidates.toList()
            }
        }
        candidates.toList()
    }

    /** Legacy one-value entry point for callers that do not yet resolve candidates. */
    suspend fun recognize(bitmap: Bitmap): RecognitionResult<String> {
        val candidate = recognizeCandidates(bitmap).firstOrNull()
        return RecognitionResult(
            value = candidate?.text,
            confidence = null,
            recognizer = "SpeciesNameRecognizer"
        )
    }

    private suspend fun recognize(treatment: String, prepared: Bitmap): String? = try {
        val text = SharedLatinTextRecognizer.readText(prepared).trim()
        Log.d(
            "SPECIES_NAME_RECOGNIZER",
            "source=${prepared.width}x${prepared.height} treatment=$treatment text=\"${text.replace("\n", "\\n")}\""
        )
        text.takeIf(String::isNotBlank)
    } catch (error: Exception) {
        Log.e("SPECIES_RECOGNIZER", "Recognition failed treatment=$treatment", error)
        null
    } finally {
        prepared.recycle()
    }

    private fun rawInput(source: Bitmap): Bitmap {
        val scaled = source.scaledForMlKitText()
        // Bitmap.createBitmap(source) may legally return source unchanged on
        // some Android implementations. The recognition lane owns and recycles
        // its prepared bitmap, so the raw pass must always receive a distinct
        // allocation before the threshold pass examines the source.
        return if (scaled === source) {
            source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        } else {
            scaled
        }
    }

    private fun threshold(source: Bitmap, cutoff: Int, padding: Int): Bitmap {
        val thresholded = Bitmap.createBitmap(
            source.width + padding * 2,
            source.height + padding * 2,
            Bitmap.Config.ARGB_8888
        )
        thresholded.eraseColor(Color.WHITE)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val color = source.getPixel(x, y)
                val luma = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
                thresholded.setPixel(
                    x + padding,
                    y + padding,
                    if (luma < cutoff) Color.BLACK else Color.WHITE
                )
            }
        }
        return try {
            Bitmap.createScaledBitmap(thresholded, thresholded.width * 6, thresholded.height * 6, false)
        } finally {
            thresholded.recycle()
        }
    }
}
