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
                    // The recorded finishing test read "Earth Powers". Accept
                    // only this one extra terminal glyph after a COMPLETE legal
                    // name; arbitrary suffixes and clipped edges stay rejected.
                    reading == name + "S" -> Match(move, 1, "OCR_SINGLE_CHARACTER_REPAIR")
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

    /** The announcement is a sentence. Extra HUD ink beyond its terminator can
     * be isolated only after the caller has matched the active actor by name. */
    fun namedAnnouncementReading(reading: String, moves: List<Move>): String {
        val boundary = reading.indexOf('!')
        if (boundary < 0) return reading
        val complete = reading.substring(0, boundary).trim()
        return if (moves.any { it.name.equals(complete, ignoreCase = true) }) complete else reading
    }

    /** A final lower-case L can merge with the exclamation mark in a terminated banner. */
    fun matchesConfiguredBanner(stem: String, name: String): Boolean = stem.length >= 7 &&
        (name.equals(stem, true) || name.equals(stem + "l", true) ||
            (stem.endsWith('|') && name.equals(stem.dropLast(1) + "l", true)))

    /** Only compare fragments inside a native "performer used ..." sentence, after
     * establishing the performer. Two source frames must independently agree. */
    fun equippedGlyphCandidate(field: String, equipped: List<String>): String? {
        val ink = field.trim().trimEnd('.')
        if (ink.isEmpty()) return null
        val readings = listOf(ink, ink.removeSuffix("!")).filter { it.isNotEmpty() }
        return equipped.filter(String::isNotBlank).distinctBy { it.lowercase() }.singleOrNull { move ->
            val target = move.lowercase()
            readings.any { raw ->
                val reading = raw.lowercase()
                when {
                    reading == target -> true
                    reading.length == 1 -> reading[0].isLetter() && reading[0] == target.first()
                    else -> {
                        val observed = wordShape(reading)
                        listOf(target, target.substringAfterLast(' ')).any { reference ->
                            val expected = wordShape(reference)
                            reference.length >= 4 && (observed == expected ||
                                expected.endsWith("ll") && observed in listOf(expected.dropLast(1), expected + "l"))
                        }
                    }
                }
            }
        }
    }

    private fun wordShape(value: String): String = value.map { glyph ->
        when (glyph) {
            '8' -> 'b'
            '1', '|', '!', 'i' -> 'l'
            'e' -> 'a' // Closed bowl in the small announcement lettering.
            else -> glyph
        }
    }.joinToString("")

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
