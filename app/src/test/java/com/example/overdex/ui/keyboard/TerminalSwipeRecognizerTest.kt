package com.example.overdex.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSwipeRecognizerTest {
    private val keys = buildMap {
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { row, letters ->
            letters.forEachIndexed { column, letter ->
                put(letter, SwipePoint(column * 50f + row * 12f, row * 60f))
            }
        }
    }

    @Test fun ranksDistinctPokemonAndMoveTracesAgainstTheirAlternatives() {
        val recognizer = TerminalSwipeRecognizer(listOf("Pikachu", "Raichu", "Bubble", "Waterfall", "Rock Smash"))
        assertEquals("Pikachu", recognizer.suggest(trace("pikachu"), keys).first())
        assertEquals("Waterfall", recognizer.suggest(trace("waterfal"), keys).first())
        assertEquals("Rock Smash", recognizer.suggest(trace("rocksmash"), keys).first())
    }

    @Test fun toleratesAnImperfectFingerPathWithoutChangingTheIntendedWord() {
        val recognizer = TerminalSwipeRecognizer(listOf("Confusion", "Constrict", "Conversion", "Incinerate"))
        val points = trace("confusion").mapIndexed { index, point ->
            SwipePoint(point.x + if (index % 2 == 0) 8f else -5f,
                point.y + if (index % 3 == 0) -7f else 6f)
        }
        assertEquals("Confusion", recognizer.suggest(points, keys).first())
    }

    @Test fun preservesCanonicalNamesAndOffersAmbiguousSpellingsForExplicitSelection() {
        val recognizer = TerminalSwipeRecognizer(listOf("Flabébé", "Mr. Mime", "Bubble", "Nidoran♀", "Nidoran♂"))
        assertEquals("Flabébé", recognizer.suggest(trace("flabebe"), keys).first())
        assertEquals("Mr. Mime", recognizer.suggest(trace("mrmime"), keys).first())
        assertEquals("Bubble", recognizer.suggest(trace("buble"), keys).first())
        assertEquals(setOf("Nidoran♀", "Nidoran♂"), recognizer.suggest(trace("nidoran"), keys).toSet())
    }

    @Test fun rejectsUnrelatedMotionRatherThanAlwaysGuessingACatalogueWord() {
        val recognizer = TerminalSwipeRecognizer(listOf("Pikachu", "Bubble", "Rock Smash"))
        assertTrue(recognizer.suggest(trace("qweop"), keys).isEmpty())
        assertTrue(recognizer.suggest(listOf(SwipePoint(-500f, -500f), SwipePoint(-100f, -100f)), keys).isEmpty())
    }

    @Test fun cancellationTapsInvalidCoordinatesAndMissingKeysProduceNoCandidates() {
        val recognizer = TerminalSwipeRecognizer(listOf("Pikachu"))
        assertTrue(recognizer.suggest(emptyList(), keys).isEmpty())
        assertTrue(recognizer.suggest(listOf(keys.getValue('p')), keys).isEmpty())
        assertTrue(recognizer.suggest(listOf(SwipePoint(0f, 0f), SwipePoint(2f, 2f)), keys).isEmpty())
        assertTrue(recognizer.suggest(listOf(SwipePoint(Float.NaN, 0f), SwipePoint(80f, 80f)), keys).isEmpty())
        assertTrue(recognizer.suggest(listOf(SwipePoint(Float.POSITIVE_INFINITY, 0f), SwipePoint(80f, 80f)), keys).isEmpty())
        assertTrue(recognizer.suggest(trace("pikachu"), keys - 'k').isEmpty())
        assertTrue(recognizer.suggest(trace("pikachu"), keys, limit = 0).isEmpty())
    }

    @Test fun letterCaseCoordinateScaleAndRepeatedSamplesDoNotChangeTheResult() {
        val recognizer = TerminalSwipeRecognizer(listOf("Razor Leaf", "Razor Shell", "Rock Smash"))
        val scaledKeys = keys.mapKeys { it.key.uppercaseChar() }
            .mapValues { (_, point) -> SwipePoint(point.x * 2f + 11f, point.y * 2f + 90f) }
        val repeated = trace("razorleaf").flatMap { point -> List(3) { point } }
            .map { SwipePoint(it.x * 2f + 11f, it.y * 2f + 90f) }
        assertEquals("Razor Leaf", recognizer.suggest(repeated, scaledKeys).first())
    }

    /** Deliberate letter waypoints with intermediate motion, independent of matcher sampling. */
    private fun trace(word: String): List<SwipePoint> = buildList {
        val points = word.map { keys.getValue(it) }
        add(points.first())
        points.zipWithNext().forEach { (start, end) ->
            for (step in 1..7) add(SwipePoint(start.x + (end.x - start.x) * step / 7f,
                start.y + (end.y - start.y) * step / 7f))
        }
    }
}
