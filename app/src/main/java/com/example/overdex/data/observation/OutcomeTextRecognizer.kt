package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.overdex.model.observation.OcrTextEvidence
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.CancellationException

/** One warm backend and one urgent turn for a result crop and its bounded fallback. */
object OutcomeTextRecognizer {
    suspend fun warmUp() = SharedLatinTextRecognizer.warmUp()

    /** Raw-only compatibility entry point; result witnesses retain every candidate below. */
    suspend fun readText(bitmap: Bitmap): String =
        SharedLatinTextRecognizer.read(bitmap, OcrPriority.URGENT).text

    internal suspend fun <T> withReadingTurn(block: suspend ReadingTurn.() -> T): T =
        SharedLatinTextRecognizer.withReadingTurn(OcrPriority.URGENT) { read ->
            ReadingTurn(read).block()
        }

    internal class ReadingTurn(private val read: suspend (Bitmap) -> Text) {
        var nativeReadNanos = 0L
            private set
        var readCount = 0
            private set

        suspend fun recognizeCandidates(
            bitmap: Bitmap,
            accepts: (String) -> Boolean,
        ): List<OcrTextEvidence> {
            suspend fun reading(input: Bitmap, padding: Int, treatment: String) =
                OcrWordGeometry.read(bitmap, input, padding, treatment, null, null, read = { prepared ->
                    val startedAt = System.nanoTime()
                    try { read(prepared) } finally {
                        nativeReadNanos += System.nanoTime() - startedAt
                        readCount++
                    }
                })
            val raw = reading(bitmap, 0, "RAW")
            if (accepts(raw.rawText) || raw.rawText.count(Char::isLetter) < 3) return listOf(raw)

            // The Oct 24 crop had clear upright white YOU WIN! lettering, yet
            // native-color OCR returned iNIAA NOA. Remove the moving avatar's
            // colors before retrying; never translate that gibberish into WIN.
            // Blank frames and numeric reward counters do not pay for a retry.
            val whiteLetters = isolateWhiteLetters(bitmap)
            return try {
                listOf(raw, reading(whiteLetters, PADDING, "WHITE_TEXT_210_PADDED"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("OutcomeTextRecognizer", "White-letter retry failed; retaining the native reading", error)
                listOf(raw)
            } finally { whiteLetters.recycle() }
        }
    }

    private fun isolateWhiteLetters(source: Bitmap): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val min = minOf(Color.red(color), Color.green(color), Color.blue(color))
            val max = maxOf(Color.red(color), Color.green(color), Color.blue(color))
            pixels[index] = if (min >= 210 && max - min <= 35) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(source.width + PADDING * 2, source.height + PADDING * 2,
            Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            setPixels(pixels, 0, source.width, PADDING, PADDING, source.width, source.height)
        }
    }

    private const val PADDING = 12
}
