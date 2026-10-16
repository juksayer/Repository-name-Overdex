package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.overdex.model.observation.RecognitionResult
import kotlinx.coroutines.CancellationException
import com.example.overdex.model.observation.OcrTextEvidence
import com.google.mlkit.vision.text.Text

/**
 * Reads the small Pokémon GO badge-name strip.
 *
 * Each returned candidate is a distinct reading of the same preserved artifact.
 * Consumers retain the raw reads and resolve only against their own known-species
 * catalogue; this recognizer never turns a partial string into a Pokémon identity.
 */
object SpeciesNameRecognizer {
    /** Initialize ML Kit during deployment, before an entry cue starts the latency budget. */
    suspend fun warmUp() = PrioritySpeciesTextRecognizer.warmUp()

    /** One urgent turn, with no outer species mutex hiding work from the scheduler. */
    internal suspend fun <T> withReadingTurn(block: suspend ReadingTurn.() -> T): T =
        SharedLatinTextRecognizer.withReadingTurn(OcrPriority.URGENT) { read ->
            ReadingTurn(read).block()
        }

    internal class ReadingTurn(private val read: suspend (Bitmap) -> Text) {
        var nativeReadNanos: Long = 0L
            private set
        var readCount: Int = 0
            private set

        suspend fun recognizeCandidates(
            bitmap: Bitmap,
            sourceContext: OcrSourceContext? = null,
            heightProfile: OcrHeightProfile? = null,
            accepts: (String) -> Boolean = { false },
        ): List<Candidate> = recognizeInTurn(bitmap, sourceContext, heightProfile, accepts) { input ->
            val startedAt = System.nanoTime()
            try { read(input) } finally {
                nativeReadNanos += System.nanoTime() - startedAt
                readCount++
            }
        }
    }


    data class Candidate(
        val text: String,
        val treatment: String,
        val evidence: OcrTextEvidence? = null,
    )

    /**
     * Runs complementary treatments because ML Kit can return a partial word from
     * the unmodified 25-pixel badge text. The padded threshold passes retain the
     * word's silhouette without changing the source crop or its evidence record.
     */
    suspend fun recognizeCandidates(
        bitmap: Bitmap,
        sourceContext: OcrSourceContext? = null,
        heightProfile: OcrHeightProfile? = null,
        accepts: (String) -> Boolean = { false }
    ): List<Candidate> = withReadingTurn {
        recognizeCandidates(bitmap, sourceContext, heightProfile, accepts)
    }

    private suspend fun recognizeInTurn(
        bitmap: Bitmap,
        sourceContext: OcrSourceContext?,
        heightProfile: OcrHeightProfile?,
        accepts: (String) -> Boolean,
        read: suspend (Bitmap) -> Text,
    ): List<Candidate> {
        val candidates = linkedSetOf<Candidate>()
        // The measured phone badges are under 32 px and their raw reads are truncated.
        // Start with the successful padded treatment; retain raw as an independent fallback.
        val treatments = if (bitmap.height < 64) listOf("THRESHOLD_190_PADDED", "RAW")
            else listOf("RAW", "THRESHOLD_190_PADDED")
        for (treatment in treatments) {
            val prepared = if (treatment == "RAW") rawInput(bitmap)
                else threshold(bitmap, cutoff = 190, padding = 12)
            recognize(treatment, bitmap, prepared, sourceContext, heightProfile, read)?.let {
                candidates += Candidate(it.acceptedText, treatment, it)
                if (accepts(it.acceptedText)) return candidates.toList()
            }
        }
        return candidates.toList()
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

    private suspend fun recognize(
        treatment: String, original: Bitmap, prepared: Bitmap,
        context: OcrSourceContext?, profile: OcrHeightProfile?,
        read: suspend (Bitmap) -> Text,
    ): OcrTextEvidence? = try {
        val reading = OcrWordGeometry.read(original, prepared,
            if (treatment == "RAW") 0 else 12, treatment, context, profile, urgent = true, read = read)
        Log.d(
            "SPECIES_NAME_RECOGNIZER",
            "source=${original.width}x${original.height} treatment=$treatment text=\"${reading.acceptedText.replace("\n", "\\n")}\" rejected=${reading.words.count { it.decision != "RETAINED" }}"
        )
        reading.takeIf { it.rawText.isNotBlank() }
    } catch (cancelled: CancellationException) {
        throw cancelled
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
        val sourcePixels = IntArray(source.width * source.height)
        val thresholdPixels = IntArray(source.width * source.height)
        source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        sourcePixels.forEachIndexed { index, color ->
                val luma = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
            thresholdPixels[index] = if (luma < cutoff) Color.BLACK else Color.WHITE
        }
        thresholded.setPixels(
            thresholdPixels,
            0,
            source.width,
            padding,
            padding,
            source.width,
            source.height,
        )
        val scale = maxOf(1f, TARGET_THRESHOLD_HEIGHT / thresholded.height.toFloat())
        if (scale == 1f) return thresholded
        return try {
            Bitmap.createScaledBitmap(
                thresholded,
                (thresholded.width * scale).toInt(),
                (thresholded.height * scale).toInt(),
                false,
            )
        } finally {
            thresholded.recycle()
        }
    }

    private const val TARGET_THRESHOLD_HEIGHT = 160f
}
