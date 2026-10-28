package com.example.overdex.data.observation

import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.model.observation.OcrWordEvidence

enum class OcrField(val initialHeightAt672: Float) { SPECIES(21f), ANNOUNCEMENT(28f) }

/** Capture-time geometry, never the overlay's position when delayed OCR completes. */
data class OcrSourceContext(val frameWidth: Int, val overlayBounds: OcrBox? = null)

/** A bounded, per-witness profile learned only from clean, vocabulary-confirmed GO reads. */
class OcrHeightProfile(val textField: OcrField) {
    private val heights = ArrayDeque<Float>()
    val sampleCount: Int get() = heights.size
    val expectedHeightAt672: Float get() = heights.sorted().let {
        if (it.isEmpty()) textField.initialHeightAt672 else it[it.size / 2]
    }
    val minimumHeightAt672: Float get() = expectedHeightAt672 * .72f

    fun learn(reading: OcrTextEvidence, context: OcrSourceContext, confirmed: Boolean) {
        // Thresholding changes which edge pixels ML Kit includes in a word's
        // box, even after scaling is undone. Only native-color reads train the
        // shared reference height, so one treatment cannot shrink another's gate.
        if (reading.treatment != "RAW" || !confirmed || context.frameWidth <= 0 ||
            reading.acceptedText.isBlank()) return
        val retainedWords = reading.words.filter { it.decision == "RETAINED" }
        // A crop can contain a clean GO phrase beside rejected HUD words. Its
        // retained phrase is still usable, but even a small overlap (or missing
        // geometry) makes it unsafe to teach the game-text height from that word.
        // The filtering gate deliberately requires much more overlap than this
        // training gate: retaining uncertain text must not train the profile.
        if (context.overlayBounds != null && retainedWords.any { word ->
                word.bounds?.overlapFraction(context.overlayBounds)?.let { it > 0f } != false
            }) return
        val measured = retainedWords.mapNotNull { word ->
            word.bounds?.height?.takeIf { word.text.any(Char::isLetter) && it > 0f }
                ?.times(672f / context.frameWidth)
        }.sorted()
        if (measured.isEmpty()) return
        val median = measured[measured.size / 2]
        // Prevent a tiny false read or a merged multi-line box from training the profile.
        if (median !in (textField.initialHeightAt672 * .72f)..(textField.initialHeightAt672 * 1.4f)) return
        if (heights.size == 7) heights.removeFirst()
        heights.addLast(median)
    }
}

object OcrSourceFilter {
    fun apply(
        rawText: String,
        words: List<OcrWordEvidence>,
        treatment: String,
        context: OcrSourceContext?,
        profile: OcrHeightProfile?,
    ): OcrTextEvidence {
        val minHeight = profile?.minimumHeightAt672
        val checked = words.map { word ->
            val box = word.bounds
            val overlap = box != null && context?.overlayBounds?.let { box.overlapFraction(it) >= .5f } == true
            val height = if (box != null && context != null && context.frameWidth > 0)
                box.height * 672f / context.frameWidth else null
            val ourInk = word.violetFraction >= .04f || word.tealFraction >= .08f || word.dangerRedFraction >= .06f
            val decision = when {
                overlap && height != null && minHeight != null && height < minHeight && ourInk -> "HUD_SIZE_COLOR_POSITION"
                else -> "RETAINED"
            }
            word.copy(decision = decision)
        }
        // Preserve ML Kit's original ordering/spacing when nothing was rejected.
        // Filtering is word-level; it never fills missing letters or joins word fragments.
        val accepted = if (checked.none { it.decision != "RETAINED" }) rawText else checked
            .groupBy { it.line }.values.mapNotNull { line ->
                line.filter { it.decision == "RETAINED" }.joinToString(" ") { it.text }.takeIf(String::isNotBlank)
            }.joinToString("\n")
        return OcrTextEvidence(treatment, rawText, accepted, checked, context?.frameWidth,
            minHeight, profile?.sampleCount ?: 0)
    }
}
