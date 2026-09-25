package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.tasks.await

/**
 * The sole bitmap entry point for ML Kit text recognition.
 *
 * Battle witnesses may intentionally use a very small, tightly scoped crop.
 * ML Kit requires both dimensions to be at least 32 pixels, so this adapter
 * enlarges only an undersized input before it reaches the recognizer. The
 * witness's stored crop remains the original artifact and its geometry is not
 * altered.
 */
suspend fun TextRecognizer.read(bitmap: Bitmap): Text {
    val scaled = bitmap.scaledForMlKitText()
    return try {
        process(InputImage.fromBitmap(scaled, 0)).await()
    } finally {
        if (scaled !== bitmap) scaled.recycle()
    }
}

suspend fun TextRecognizer.readText(bitmap: Bitmap): String = read(bitmap).text

fun Bitmap.scaledForMlKitText(): Bitmap {
    val scale = maxOf(
        1f,
        ML_KIT_MINIMUM_DIMENSION.toFloat() / width.coerceAtLeast(1),
        ML_KIT_MINIMUM_DIMENSION.toFloat() / height.coerceAtLeast(1)
    )
    if (scale == 1f) return this
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(ML_KIT_MINIMUM_DIMENSION),
        (height * scale).toInt().coerceAtLeast(ML_KIT_MINIMUM_DIMENSION),
        true
    )
}

private const val ML_KIT_MINIMUM_DIMENSION = 32
