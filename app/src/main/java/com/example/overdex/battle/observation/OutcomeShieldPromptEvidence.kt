package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence

/**
 * Reuses an existing result-crop reading only when it contains the complete native shield prompt.
 * This validates pixels' recorded territory, not a result, move, impact, HP measurement, or faint.
 * The caller still owns current-Match, lifecycle, age, and episode checks.
 */
internal object OutcomeShieldPromptEvidence {
    const val SOURCE_ID = "OUTCOME_SHIELD_PROMPT_TEXT_WITNESS"
    const val PROMPT = "No Protect Shields remaining!"

    private val completePrompt = Regex("No\\s+Protect\\s+Shields\\s+remaining!?", RegexOption.IGNORE_CASE)
    private val captureSources = setOf(
        BattleWitnessContracts.matchOutcomeCropCapture.witnessId,
        BattleWitnessContracts.matchOutcomeProbeCapture.witnessId,
    )

    fun matchesText(text: String): Boolean = completePrompt.matches(text.trim())

    /** Returns the unchanged accepted wording after validating its original crop-local word boxes. */
    fun resolve(crop: RealityArticle, reading: OcrTextEvidence): String? {
        val captured = crop.payload as? CropCaptured ?: return null
        val provenance = captured.cropProvenance
        val bounds = provenance.bounds
        if (crop.sourceId.id !in captureSources || crop.id.value.isBlank() ||
            crop.matchId?.value.isNullOrBlank() || crop.monotonicTimeNanos?.let { it >= 0L } != true ||
            provenance.cropName != BattleCropContracts.matchOutcome.cropName ||
            provenance.sourceWidth <= 0 || provenance.sourceHeight <= 0 ||
            bounds.left < 0 || bounds.top < 0 || bounds.right > provenance.sourceWidth ||
            bounds.bottom > provenance.sourceHeight || bounds.right <= bounds.left || bounds.bottom <= bounds.top ||
            reading.sourceFrameWidth?.let { it != provenance.sourceWidth } == true ||
            !matchesText(reading.acceptedText)) return null

        val words = reading.words.filter { it.decision == "RETAINED" }
        if (words.isEmpty() || !matchesText(words.joinToString(" ") { it.text })) return null
        val width = (bounds.right - bounds.left).toFloat()
        val height = (bounds.bottom - bounds.top).toFloat()
        val overlay = captured.overlayBounds
        if (overlay != null && !validBox(overlay)) return null
        if (words.any { word ->
                val box = word.bounds
                box == null || !validBox(box) || box.left < 0f || box.top < 0f ||
                    box.right > width || box.bottom > height ||
                    (overlay != null && intersects(box, overlay))
            }) return null
        return reading.acceptedText
    }

    private fun validBox(box: OcrBox): Boolean =
        box.left.isFinite() && box.top.isFinite() && box.right.isFinite() && box.bottom.isFinite() &&
            box.right > box.left && box.bottom > box.top

    private fun intersects(first: OcrBox, second: OcrBox): Boolean =
        minOf(first.right, second.right) > maxOf(first.left, second.left) &&
            minOf(first.bottom, second.bottom) > maxOf(first.top, second.top)
}
