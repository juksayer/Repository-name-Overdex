package com.example.overdex.battle.observation

import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import java.text.Normalizer
import java.util.Locale

/** Additional interpretation of preserved OCR; it never changes its source text or time. */
internal object PartialOutcomeTextResolver {
    enum class Basis {
        PARTIAL_EXPECTED_LABEL_WITH_RESULT_GEOMETRY,
        DISTINCTIVE_RESULT_SIGNATURE_WITH_POSITION,
    }

    data class Decision(
        val phrase: MatchOutcomePhrase,
        val basis: Basis,
        /** A W/FF fragment needs independent source frames, never two OCR treatments of one frame. */
        val needsConfirmation: Boolean,
        val observedLetters: String,
        val matchedLetterCount: Int,
        /** Original source-frame pixels, after undoing OCR input scaling/padding. */
        val bounds: OcrBox,
        /** Smallest measured word height, normalized to a 672-pixel-wide capture. */
        val minimumHeightAt672: Float,
    )

    fun resolve(reading: OcrTextEvidence, provenance: BattleCropProvenance): Decision? {
        val letters = normalize(reading.acceptedText)
        if (letters.isEmpty() || letters.length > 11) return null
        val words = reading.words.filter { it.decision == "RETAINED" && normalize(it.text).isNotEmpty() }
        // Do not pick an attractive fragment out of unrelated/mixed text. Every
        // surviving word must belong to the reading being compared with a label.
        if (normalize(words.joinToString(" ") { it.text }) != letters) return null
        val geometry = geometry(words.map { it.bounds ?: return null }, provenance) ?: return null
        val candidates = listOf(MatchOutcomePhrase.WIN to "YOUWIN", MatchOutcomePhrase.LOSS to "GOODEFFORT")
            .mapNotNull { (phrase, expected) ->
                val alignment = align(letters, expected)
                val requiredMatches = if (phrase == MatchOutcomePhrase.WIN) 4 else 5
                // Missing edge/interior letters are common against GO's scene.
                // Permit one wrong/extra glyph too, but do not explain away
                // arbitrary additional words as the rest of a result label.
                if (alignment.matches >= requiredMatches && alignment.unexpected <= 1) phrase to alignment else null
            }
        if (candidates.size == 1) {
            val (phrase, alignment) = candidates.single()
            return Decision(phrase, Basis.PARTIAL_EXPECTED_LABEL_WITH_RESULT_GEOMETRY, false,
                letters, alignment.matches, geometry.bounds, geometry.minimumHeightAt672)
        }
        if (candidates.isNotEmpty()) return null

        val phrase = when (letters) {
            "W", "WIN" -> MatchOutcomePhrase.WIN
            "FF", "EFF" -> MatchOutcomePhrase.LOSS
            else -> return null
        }
        if (!signaturePositionFits(phrase, letters, geometry.bounds, provenance)) return null
        return Decision(phrase, Basis.DISTINCTIVE_RESULT_SIGNATURE_WITH_POSITION, true,
            letters, letters.length, geometry.bounds, geometry.minimumHeightAt672)
    }

    private data class Geometry(val bounds: OcrBox, val minimumHeightAt672: Float)

    private fun geometry(boxes: List<OcrBox>, provenance: BattleCropProvenance): Geometry? {
        if (boxes.isEmpty() || provenance.sourceWidth <= 0 || provenance.sourceHeight <= 0) return null
        val crop = provenance.bounds
        if (crop.right <= crop.left || crop.bottom <= crop.top || crop.left < 0 || crop.top < 0 ||
            crop.right > provenance.sourceWidth || crop.bottom > provenance.sourceHeight) return null
        // Real ML Kit word boxes can cross the preserved crop by a few pixels;
        // reject impossible geometry without cutting those legitimate letters.
        val margin = provenance.sourceWidth * .04f
        if (boxes.any { box ->
                listOf(box.left, box.top, box.right, box.bottom).any { !it.isFinite() } ||
                    box.width <= 0f || box.height <= 0f || box.left < -margin || box.top < -margin ||
                    box.right > crop.right - crop.left + margin || box.bottom > crop.bottom - crop.top + margin
            }) return null
        val heights = boxes.map { it.height * 672f / provenance.sourceWidth }
        // Nov 11 result words measure 72..102 px at this scale; effectiveness
        // text measures 16..25. Keep room for fading/edge loss while separating
        // title lettering from ordinary battle announcements and HUD labels.
        if (heights.any { it !in 48f..160f }) return null
        val bounds = OcrBox(boxes.minOf { it.left } + crop.left, boxes.minOf { it.top } + crop.top,
            boxes.maxOf { it.right } + crop.left, boxes.maxOf { it.bottom } + crop.top)
        val centerX = (bounds.left + bounds.right) / (2f * provenance.sourceWidth)
        val centerY = (bounds.top + bounds.bottom) / (2f * provenance.sourceHeight)
        if (centerX !in .15f.. .85f || centerY !in .40f.. .65f) return null
        return Geometry(bounds, heights.min())
    }

    private fun signaturePositionFits(
        phrase: MatchOutcomePhrase, letters: String, bounds: OcrBox, provenance: BattleCropProvenance,
    ): Boolean {
        val centerX = (bounds.left + bounds.right) / (2f * provenance.sourceWidth)
        val centerY = (bounds.top + bounds.bottom) / (2f * provenance.sourceHeight)
        val aspect = bounds.width / bounds.height
        return when (phrase) {
            // These are layout tolerances, not claimed per-letter detections.
            // W is right of centre in the single-line YOU WIN title. A complete
            // WIN fragment extends farther right than that individual letter.
            MatchOutcomePhrase.WIN -> centerY in .43f.. .57f && when (letters) {
                "W" -> centerX in .48f.. .66f && aspect in .4f..2f
                else -> centerX in .54f.. .81f && aspect in 1f..4f
            }
            // GO uses both one-line GOOD EFFORT and two-line GOOD / EFFORT
            // layouts. Their FF positions differ; a left-edge FF is neither.
            MatchOutcomePhrase.LOSS -> aspect in .5f..3f && (
                (centerX in .54f.. .72f && centerY in .43f.. .57f) ||
                    (centerX in .31f.. .49f && centerY in .50f.. .60f))
        }
    }

    private fun normalize(text: String): String = Normalizer.normalize(text.uppercase(Locale.ROOT), Normalizer.Form.NFD)
        .map { when (it) { '0' -> 'O'; '1' -> 'I'; '7' -> 'T'; else -> it } }
        .filter { it in 'A'..'Z' }.joinToString("")

    private data class Alignment(val edits: Int, val matches: Int, val unexpected: Int)

    /** Whole-reading alignment permits missing letters without searching a substring of unrelated text. */
    private fun align(actual: String, expected: String): Alignment {
        val cells = Array(actual.length + 1) { i -> Array(expected.length + 1) { j ->
            Alignment(i + j, 0, i)
        } }
        for (i in 1..actual.length) for (j in 1..expected.length) {
            val same = actual[i - 1] == expected[j - 1]
            val diagonal = cells[i - 1][j - 1].let {
                Alignment(it.edits + if (same) 0 else 1, it.matches + if (same) 1 else 0,
                    it.unexpected + if (same) 0 else 1)
            }
            val missing = cells[i][j - 1].let { it.copy(edits = it.edits + 1) }
            val extra = cells[i - 1][j].let { it.copy(edits = it.edits + 1, unexpected = it.unexpected + 1) }
            cells[i][j] = listOf(diagonal, missing, extra).minWith(
                compareBy<Alignment> { it.edits }.thenByDescending { it.matches }.thenBy { it.unexpected })
        }
        return cells[actual.length][expected.length]
    }
}
