package com.example.overdex.ui.keyboard

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** A position in the keyboard's own coordinate space, before any shell rotation. */
data class SwipePoint(val x: Float, val y: Float)

/**
 * Small, offline gesture vocabulary for the instrument keyboard. Results are suggestions,
 * never inserted text: a trainer must choose the intended spelling from the candidate strip.
 *
 * Coordinates stay in the actual keyboard geometry. Normalizing each word to its own bounds
 * would make unrelated traces look alike, particularly short Pokémon and move names.
 */
class TerminalSwipeRecognizer(words: List<String>) {
    private data class Word(val display: String, val letters: String)

    private val vocabulary = words.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .map { Word(it, normalize(it)) }
        .filter { it.letters.length >= 2 }
        .toList()

    /**
     * Call only after a completed, single-pointer gesture. Cancellation is represented by an
     * empty path. Work is bounded, but callers with a full catalogue should use a worker thread.
     */
    fun suggest(
        path: List<SwipePoint>,
        centers: Map<Char, SwipePoint>,
        limit: Int = 3,
    ): List<String> {
        if (limit <= 0 || path.size !in 2..MAX_INPUT_POINTS || path.any { !it.isFinite() }) {
            return emptyList()
        }
        val keys = centers.entries.asSequence()
            .filter { it.key.isLetter() && it.value.isFinite() }
            .associate { it.key.lowercaseChar() to it.value }
        val spacing = keySpacing(keys.values.toList()) ?: return emptyList()
        val trace = compact(path, spacing * 0.015f)
        if (trace.size < 2) return emptyList()
        val length = length(trace)
        if (!length.isFinite() || length < spacing * 0.65f) return emptyList()
        val sampled = resample(trace, length)
        val start = trace.first()
        val end = trace.last()
        val candidates = mutableListOf<Pair<Word, Float>>()

        for (word in vocabulary) {
            val template = word.letters.mapNotNull(keys::get)
            if (template.size != word.letters.length) continue
            val startError = distance(start, template.first()) / spacing
            val endError = distance(end, template.last()) / spacing
            if (startError > 1.15f || endError > 1.15f) continue
            val templateLength = length(template)
            if (templateLength <= 0f) continue
            val lengthRatio = length / templateLength
            if (lengthRatio !in 0.52f..1.85f) continue
            val target = resample(template, templateLength)
            var shapeError = 0f
            for (index in sampled.indices) shapeError += distance(sampled[index], target[index]) / spacing
            shapeError /= SAMPLE_COUNT
            if (shapeError > 0.82f) continue

            // A trace that takes a shortcut past a defining letter should not become that word
            // merely because its start, end, and total travel happen to match.
            var anchorError = 0f
            var maxAnchorError = 0f
            for (point in template) {
                val error = distanceToPath(point, trace) / spacing
                anchorError += error
                maxAnchorError = max(maxAnchorError, error)
            }
            anchorError /= template.size
            if (anchorError > 0.6f || maxAnchorError > 1.2f) continue
            val score = shapeError * 0.72f + anchorError * 0.18f +
                (startError + endError) * 0.05f + abs(ln(lengthRatio)) * 0.12f
            candidates += word to score
        }

        val sorted = candidates.sortedWith(compareBy<Pair<Word, Float>> { it.second }
            .thenBy { it.first.display.length }.thenBy { it.first.display })
        val best = sorted.firstOrNull()?.second ?: return emptyList()
        return sorted.asSequence().filter { it.second <= min(0.8f, best + 0.30f) }
            .take(limit.coerceAtMost(10)).map { it.first.display }.toList()
    }

    private fun keySpacing(keys: List<SwipePoint>): Float? {
        if (keys.size < 2) return null
        val nearest = keys.mapNotNull { key ->
            keys.asSequence().map { distance(key, it) }.filter { it > 0.001f }
                .minOrNull()?.takeIf { it.isFinite() }
        }.sorted()
        if (nearest.isEmpty()) return null
        return nearest[nearest.size / 2].takeIf { it > 0.001f }
    }

    private fun compact(points: List<SwipePoint>, minimumTravel: Float): List<SwipePoint> {
        val result = ArrayList<SwipePoint>(points.size)
        result += points.first()
        for (point in points.drop(1)) {
            if (distance(result.last(), point) >= minimumTravel) result += point
        }
        if (result.last() != points.last()) result += points.last()
        return result
    }

    private fun length(points: List<SwipePoint>): Float {
        var result = 0f
        for (index in 1 until points.size) result += distance(points[index - 1], points[index])
        return result
    }

    private fun resample(points: List<SwipePoint>, totalLength: Float): List<SwipePoint> {
        val result = ArrayList<SwipePoint>(SAMPLE_COUNT)
        var segment = 1
        var completed = 0f
        for (index in 0 until SAMPLE_COUNT) {
            val position = totalLength * index / (SAMPLE_COUNT - 1)
            while (segment < points.lastIndex &&
                completed + distance(points[segment - 1], points[segment]) < position) {
                completed += distance(points[segment - 1], points[segment])
                segment++
            }
            val before = points[segment - 1]
            val after = points[segment]
            val segmentLength = distance(before, after)
            val fraction = if (segmentLength <= 0f) 0f else
                ((position - completed) / segmentLength).coerceIn(0f, 1f)
            result += SwipePoint(before.x + (after.x - before.x) * fraction,
                before.y + (after.y - before.y) * fraction)
        }
        return result
    }

    private fun distanceToPath(point: SwipePoint, path: List<SwipePoint>): Float {
        var result = Float.POSITIVE_INFINITY
        for (index in 1 until path.size) {
            val start = path[index - 1]
            val end = path[index]
            val dx = end.x - start.x
            val dy = end.y - start.y
            val squared = dx * dx + dy * dy
            val fraction = if (squared <= 0f) 0f else
                (((point.x - start.x) * dx + (point.y - start.y) * dy) / squared)
                    .coerceIn(0f, 1f)
            result = min(result, distance(point, SwipePoint(start.x + dx * fraction, start.y + dy * fraction)))
        }
        return result
    }

    private fun SwipePoint.isFinite(): Boolean = x.isFinite() && y.isFinite()

    private fun distance(first: SwipePoint, second: SwipePoint): Float =
        hypot(first.x - second.x, first.y - second.y)

    private companion object {
        const val SAMPLE_COUNT = 40
        const val MAX_INPUT_POINTS = 4096
        fun normalize(word: String): String {
            val letters = Normalizer.normalize(word, Normalizer.Form.NFD)
                .lowercase(Locale.ROOT).filter { it in 'a'..'z' }
            return buildString {
                for (letter in letters) if (lastOrNull() != letter) append(letter)
            }
        }
    }
}
