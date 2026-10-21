package com.example.overdex.data.observation

import android.graphics.Bitmap
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.model.observation.OcrWordEvidence
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Maps ML Kit coordinates back through every enlargement, then measures original-color pixels. */
internal object OcrWordGeometry {
    suspend fun read(
        original: Bitmap, prepared: Bitmap, padding: Int, treatment: String,
        context: OcrSourceContext?, profile: OcrHeightProfile?, urgent: Boolean = false,
        priority: OcrPriority = if (urgent) OcrPriority.URGENT else OcrPriority.NORMAL,
        read: suspend (Bitmap) -> Text = { SharedLatinTextRecognizer.read(it, priority) },
    ): OcrTextEvidence {
        // Account for the shared adapter's minimum-size enlargement as well.
        val input = prepared.scaledForMlKitText()
        return try {
            val result = read(input)
            reading(result, original, input.width, input.height, padding, treatment, context, profile)
        } finally {
            if (input !== prepared) input.recycle()
        }
    }

    fun reading(
        result: Text,
        original: Bitmap,
        inputWidth: Int,
        inputHeight: Int,
        padding: Int,
        treatment: String,
        context: OcrSourceContext?,
        profile: OcrHeightProfile?,
    ): OcrTextEvidence {
        val scaleX = inputWidth.toFloat() / (original.width + padding * 2)
        val scaleY = inputHeight.toFloat() / (original.height + padding * 2)
        val pixels = IntArray(original.width * original.height)
        original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        var lineIndex = 0
        val words = result.textBlocks.flatMap { block -> block.lines.flatMap { line ->
            val index = lineIndex++
            line.elements.map { element ->
                val box = element.boundingBox?.let { rect -> OcrBox(
                    rect.left / scaleX - padding, rect.top / scaleY - padding,
                    rect.right / scaleX - padding, rect.bottom / scaleY - padding,
                ) }
                val colors = colors(pixels, original.width, original.height, box)
                OcrWordEvidence(element.text, index, box, colors[0], colors[1], colors[2])
            }
        } }
        return OcrSourceFilter.apply(result.text.trim(), words, treatment, context, profile)
    }

    private fun colors(pixels: IntArray, width: Int, height: Int, box: OcrBox?): FloatArray {
        if (box == null) return floatArrayOf(0f, 0f, 0f)
        var count = 0; var violet = 0; var teal = 0; var red = 0
        for (y in floor(box.top).toInt().coerceIn(0, height) until ceil(box.bottom).toInt().coerceIn(0, height)) {
            for (x in floor(box.left).toInt().coerceIn(0, width) until ceil(box.right).toInt().coerceIn(0, width)) {
                val pixel = pixels[y * width + x]
                val r = (pixel ushr 16) and 255; val g = (pixel ushr 8) and 255; val b = pixel and 255
                count++
                if (b - r >= 12 && r - g >= 8 && b - g >= 25) violet++
                if (r in 20..145 && g in 45..170 && b in 40..195 && g >= r + 10 && b >= r + 8 && abs(g - b) < 40) teal++
                if (r >= 140 && r - g >= 65 && r - b >= 65) red++
            }
        }
        if (count == 0) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(violet.toFloat() / count, teal.toFloat() / count, red.toFloat() / count)
    }
}
