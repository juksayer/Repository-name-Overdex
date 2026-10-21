package com.example.overdex.battle.observation

/** Exact bright, neutral letter pixels. No fuzzy comparison that could hide a changed letter. */
internal data class LabelInk(val width: Int, val height: Int, val bits: List<Long>) {
    companion object {
        fun fromPixels(width: Int, height: Int, pixels: IntArray): LabelInk {
            require(width > 0 && height > 0 && pixels.size == width * height)
            val words = LongArray((pixels.size + 63) / 64)
            for (i in pixels.indices) {
                val r = pixels[i] ushr 16 and 255
                val g = pixels[i] ushr 8 and 255
                val b = pixels[i] and 255
                if (minOf(r,g,b) >= 195 && maxOf(r,g,b) - minOf(r,g,b) <= 40)
                    words[i / 64] = words[i / 64] or (1L shl (i % 64))
            }
            return LabelInk(width, height, words.toList())
        }
    }
}

internal class StableLabelReading {
    data class Reading(val text: String, val references: List<String>)
    private var ink: LabelInk? = null
    private var at = Long.MIN_VALUE
    private var reading: Reading? = null
    fun reuse(current: LabelInk, capturedAt: Long): Reading? {
        val saved = reading ?: return null
        val lifetime = if (saved.text.isBlank()) 1_000_000_000L else 5_000_000_000L
        return saved.takeIf { current == ink && capturedAt - at in 0 until lifetime }
    }
    fun remember(current: LabelInk, capturedAt: Long, text: String, references: List<String>) {
        // A reused reading must always lead back to the preserved pixels originally read by OCR.
        if (references.isEmpty()) return
        ink = current; at = capturedAt; reading = Reading(text, references.toList())
    }
}
