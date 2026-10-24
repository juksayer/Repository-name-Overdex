package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** One warm Latin model, with admission priority before ML Kit's shared queue. */
object SharedLatinTextRecognizer {
    private val recognizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val lane = OcrWorkScheduler()
    @Volatile private var warmed = false

    internal fun waitingCount(priority: OcrPriority): Int = lane.waitingCount(priority)

    suspend fun read(bitmap: Bitmap): Text = read(bitmap, OcrPriority.NORMAL)

    /**
     * Reserve one backend turn before selecting pixels. The caller may replace
     * its waiting crop with a newer preserved crop here, without a second queue
     * between selection and ML Kit. The supplied reader is valid only in this block.
     */
    internal suspend fun <T> withReadingTurn(
        priority: OcrPriority,
        block: suspend (suspend (Bitmap) -> Text) -> T,
    ): T = lane.run(priority) {
        block { bitmap -> recognizer.read(bitmap).also { warmed = true } }
    }

    internal suspend fun read(bitmap: Bitmap, priority: OcrPriority): Text {
        val queuedAt = System.nanoTime()
        return lane.run(priority) {
            val startedAt = System.nanoTime()
            val result = recognizer.read(bitmap)
            warmed = true
            val finishedAt = System.nanoTime()
            if (finishedAt - queuedAt >= 1_000_000_000L) {
                Log.d("OCR_SCHEDULER", "priority=$priority size=${bitmap.width}x${bitmap.height} " +
                    "waitMs=${(startedAt - queuedAt) / 1_000_000} readMs=${(finishedAt - startedAt) / 1_000_000}")
            }
            result
        }
    }

    suspend fun readText(bitmap: Bitmap): String = read(bitmap).text

    internal suspend fun readTextFresh(bitmap: Bitmap, isFresh: () -> Boolean): String? =
        lane.runFresh(OcrPriority.NORMAL, isFresh) {
            recognizer.read(bitmap).text.also { warmed = true }
        }

    /** Optional checks cannot queue ahead of a pending species/result read. */
    suspend fun readTextIfIdle(bitmap: Bitmap): String? = lane.runIfIdle {
        recognizer.read(bitmap).text.also { warmed = true }
    }

    suspend fun warmUp() {
        if (warmed) return
        lane.run(OcrPriority.NORMAL) {
            if (!warmed) {
                val blank = Bitmap.createBitmap(96, 32, Bitmap.Config.ARGB_8888)
                blank.eraseColor(Color.WHITE)
                try {
                    recognizer.read(blank)
                    warmed = true
                } finally {
                    blank.recycle()
                }
            }
        }
    }
}

/** Active species have priority over routine button/effectiveness text checks. */
object PrioritySpeciesTextRecognizer {
    suspend fun readText(bitmap: Bitmap): String =
        SharedLatinTextRecognizer.read(bitmap, OcrPriority.URGENT).text
    suspend fun warmUp() = SharedLatinTextRecognizer.warmUp()
}
