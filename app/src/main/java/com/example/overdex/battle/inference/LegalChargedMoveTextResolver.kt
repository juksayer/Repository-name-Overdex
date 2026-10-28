package com.example.overdex.battle.inference

import com.example.overdex.battle.observation.MoveTextAliases
import com.example.overdex.battle.observation.SpeciesOcrTypography
import com.example.overdex.model.Move

/** Legal vocabulary comparison shared by live inference and replay presentation. */
internal object LegalChargedMoveTextResolver {
    // Every repair has the same rank: fewer missing letters do not prove which
    // of two plausible legal names was used. A complete exact reading wins.
    data class Match(val move: Move, val repairRank: Int, val repairBasis: String? = null)

    /** Returns all best candidates; the caller must still establish one move and its performer. */
    fun bestMatches(text: String, moves: List<Move>): List<Match> {
        val reading = normalize(text)
        val matches = moves.distinctBy { normalize(it.name) }.mapNotNull { move ->
            MoveTextAliases.normalizedNames(move.name).mapNotNull { name ->
                when {
                    reading == name -> Match(move, 0,
                        "VISIBLE_MOVE_NAME_ALIAS".takeUnless { reading == normalize(move.name) })
                    reading.length < 6 || name.length < 6 -> null
                    reading.startsWith(name) || name.startsWith(reading) -> null
                    SpeciesOcrTypography.glyphRepairCost(reading, name) != null ->
                        Match(move, 1, "OCR_LEGAL_GLYPH_REPAIR")
                    missingInternalCharacters(reading, name) ->
                        Match(move, 1, "OCR_MISSING_INTERNAL_CHARACTERS")
                    singleCharacterDifference(reading, name) ->
                        Match(move, 1, "OCR_SINGLE_CHARACTER_REPAIR")
                    else -> null
                }
            }.minByOrNull { it.repairRank }
        }
        val best = matches.minOfOrNull { it.repairRank } ?: return emptyList()
        return matches.filter { it.repairRank == best }
    }

    private fun normalize(value: String) = SpeciesOcrTypography.normalizeLabel(value)

    /**
     * Pale strokes can disappear into the battlefield (FouPay -> Foul Play).
     * Allow only one or two missing internal letters, with both name edges and
     * at least three quarters of the name still present in their original order.
     * This is not prefix completion, transposition, or arbitrary fuzzy spelling.
     */
    private fun missingInternalCharacters(reading: String, name: String): Boolean {
        val missing = name.length - reading.length
        if (missing !in 1..2 || reading.length < 6 || reading.length * 4 < name.length * 3) return false
        if (reading.first() != name.first() || reading.last() != name.last()) return false
        var readIndex = 0
        for (letter in name) {
            if (readIndex < reading.length && reading[readIndex] == letter) readIndex++
        }
        return readIndex == reading.length
    }

    private fun singleCharacterDifference(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        var a = 0; var b = 0; var edits = 0
        while (a < left.length && b < right.length) {
            if (left[a] == right[b]) { a++; b++; continue }
            if (++edits > 1) return false
            when {
                left.length > right.length -> a++
                right.length > left.length -> b++
                else -> { a++; b++ }
            }
        }
        return edits + (left.length - a) + (right.length - b) == 1
    }
}
