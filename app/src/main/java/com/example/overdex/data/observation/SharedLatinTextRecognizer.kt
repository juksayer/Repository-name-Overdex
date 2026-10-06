package com.example.overdex.data.observation

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One process-wide ML Kit Latin recognizer.
 *
 * Creating a client for every visual worker creates another TFLite interpreter
 * and competes for the same CPU pool. A single serial lane keeps the model warm
 * and bounds both interpreter count and concurrent OCR pressure.
 */
object SharedLatinTextRecognizer {
    private val recognizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val lane = Mutex()
    @Volatile private var warmed = false

    suspend fun read(bitmap: Bitmap): Text = lane.withLock {
        warmed = true
        recognizer.read(bitmap)
    }

    suspend fun readText(bitmap: Bitmap): String = read(bitmap).text

    suspend fun warmUp() {
        if (warmed) return
        lane.withLock {
            if (warmed) return@withLock
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

/**
 * One reserved ML Kit client for the two active-species badge strips.
 *
 * Full-width announcement and result OCR can remain continuously busy during a
 * battle. Species identity has a 1.5 second delivery target, so it must not wait
 * in that general queue. This creates one additional, process-wide interpreter,
 * rather than one client per witness or frame.
 */
object PrioritySpeciesTextRecognizer {
    private val recognizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val lane = Mutex()
    @Volatile private var warmed = false

    suspend fun readText(bitmap: Bitmap): String = lane.withLock {
        warmed = true
        recognizer.read(bitmap).text
    }

    suspend fun warmUp() {
        if (warmed) return
        lane.withLock {
            if (warmed) return@withLock
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
