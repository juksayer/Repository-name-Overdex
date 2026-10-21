package com.example.overdex.model.observation

import kotlinx.serialization.Serializable

/** Pixel coordinates in the original preserved crop, before OCR scaling/padding. */
@Serializable
data class OcrBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    fun overlapFraction(other: OcrBox): Float {
        val area = width * height
        if (area <= 0f) return 0f
        return ((minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f) *
            (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)) / area
    }
}

/** Raw word geometry and source-pixel color measurements survive a rejected reading. */
@Serializable
data class OcrWordEvidence(
    val text: String,
    val line: Int,
    val bounds: OcrBox?,
    val violetFraction: Float = 0f,
    val tealFraction: Float = 0f,
    val dangerRedFraction: Float = 0f,
    val decision: String = "RETAINED",
)

@Serializable
data class OcrTextEvidence(
    val treatment: String,
    val rawText: String,
    val acceptedText: String,
    val words: List<OcrWordEvidence>,
    val sourceFrameWidth: Int?,
    val expectedMinimumHeightAt672: Float?,
    val profileSampleCount: Int = 0,
)
