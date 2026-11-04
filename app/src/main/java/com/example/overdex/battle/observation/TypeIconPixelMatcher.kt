package com.example.overdex.battle.observation

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Pixel-only implementation so geometry and rejection can be checked on the JVM. */
internal class TypeIconPixels(val width: Int, val height: Int, val argb: IntArray) {
    init { require(width > 0 && height > 0 && argb.size == width * height) }

    fun pixel(x: Int, y: Int): Int = argb[y * width + x]

    fun sample(x: Double, y: Double): Int {
        val sx = x.coerceIn(0.0, (width - 1).toDouble())
        val sy = y.coerceIn(0.0, (height - 1).toDouble())
        val left = sx.toInt(); val top = sy.toInt()
        val fx = sx - left; val fy = sy - top
        val a = pixel(left, top)
        val b = pixel(min(left + 1, width - 1), top)
        val c = pixel(left, min(top + 1, height - 1))
        val d = pixel(min(left + 1, width - 1), min(top + 1, height - 1))
        fun channel(shift: Int): Int = ((1 - fy) * ((1 - fx) * component(a, shift) + fx * component(b, shift)) +
            fy * ((1 - fx) * component(c, shift) + fx * component(d, shift))).roundToInt()
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun reduced(maximum: Int): Pair<TypeIconPixels, Double> {
        if (max(width, height) <= maximum) return this to 1.0
        val scale = maximum.toDouble() / max(width, height)
        val w = max(1, (width * scale).roundToInt())
        val h = max(1, (height * scale).roundToInt())
        return TypeIconPixels(w, h, IntArray(w * h) { index ->
            sample((index % w + .5) / scale - .5, (index / w + .5) / scale - .5)
        }) to scale
    }
}

internal data class TypeIconCircle(
    val centerX: Double, val centerY: Double, val radius: Double, val complete: Boolean = true,
)
internal data class TypeIconRgb(val red: Double, val green: Double, val blue: Double)
internal data class TypeIconReference(val key: Int, val pixels: TypeIconPixels, val circle: TypeIconCircle)
internal data class TypeIconPixelResult(
    val key: Int?, val bestCandidate: Int?, val similarity: Float, val colorSimilarity: Float,
    val shapeSimilarity: Float, val runnerUpMargin: Float, val colorAvailable: Boolean,
)

/**
 * The colored disk supplies scale and position; the reference glyph supplies
 * identity. Calibration margins therefore cannot shrink the observed glyph.
 */
internal class TypeIconPixelMatcher(references: List<TypeIconReference>) {
    private data class Template(val reference: TypeIconReference, val color: TypeIconRgb?)
    private data class Scored(val key: Int, val combined: Double, val shape: Double, val color: Double?)
    private val templates = references.map { Template(it, representativeColor(it.pixels, it.circle)) }

    fun matchCrop(pixels: TypeIconPixels): List<TypeIconPixelResult> =
        TypeIconCircleDetector.detect(pixels).map { matchCircle(pixels, it) }

    fun matchIsolatedBadge(pixels: TypeIconPixels): TypeIconPixelResult = matchCircle(pixels,
        TypeIconCircle((pixels.width - 1) / 2.0, (pixels.height - 1) / 2.0,
            min(pixels.width, pixels.height) / 2.0))

    internal fun matchCircle(pixels: TypeIconPixels, circle: TypeIconCircle): TypeIconPixelResult {
        if (!circle.complete) return unknown()
        val points = mutableListOf<Triple<Int, Int, Double>>()
        val extent = circle.radius * .65
        val step = max(1, (circle.radius / 32).toInt())
        for (y in max(0, floor(circle.centerY - extent).toInt())..
            min(pixels.height - 1, ceil(circle.centerY + extent).toInt()) step step) {
            for (x in max(0, floor(circle.centerX - extent).toInt())..
                min(pixels.width - 1, ceil(circle.centerX + extent).toInt()) step step) {
                points += Triple(x, y, softWhite(pixels.pixel(x, y)))
            }
        }
        if (points.sumOf { it.third } < 3.0 || templates.isEmpty()) return unknown()
        val observedColor = representativeColor(pixels, circle)
        val scores = templates.map { template ->
            val reference = template.reference
            var shape = 0.0
            // Half a source pixel compensates for capture/sample phase, without
            // changing glyph proportions or moving it independently of the disk.
            for (offsetY in PHASE_OFFSETS) for (offsetX in PHASE_OFFSETS) {
                var intersection = 0.0; var union = 0.0
                for ((x, y, observed) in points) {
                    val sourceX = (x - circle.centerX - offsetX) / circle.radius * reference.circle.radius +
                        reference.circle.centerX
                    val sourceY = (y - circle.centerY - offsetY) / circle.radius * reference.circle.radius +
                        reference.circle.centerY
                    val expected = softWhite(reference.pixels.sample(sourceX, sourceY))
                    intersection += min(observed, expected)
                    union += max(observed, expected)
                }
                if (union > 0) shape = max(shape, intersection / union)
            }
            val color = if (observedColor != null && template.color != null)
                colorSimilarity(observedColor, template.color) else null
            Scored(reference.key, if (color == null) shape else shape * .7 + color * .3, shape, color)
        }.sortedByDescending { it.combined }
        val best = scores.first()
        val margin = best.combined - (scores.getOrNull(1)?.combined ?: 0.0)
        // Keep the established acceptance threshold, and independently require
        // glyph support and separation. Color alone cannot identify a badge.
        val accepted = best.combined >= ACCEPTANCE && best.shape >= MINIMUM_SHAPE && margin >= MINIMUM_MARGIN
        return TypeIconPixelResult(best.key.takeIf { accepted }, best.key, best.combined.toFloat(),
            best.color?.toFloat() ?: 0f, best.shape.toFloat(), margin.toFloat(), best.color != null)
    }

    private fun unknown() = TypeIconPixelResult(null, null, 0f, 0f, 0f, 0f, false)

    companion object {
        private const val ACCEPTANCE = .40
        private const val MINIMUM_SHAPE = .45
        private const val MINIMUM_MARGIN = .04
        private val PHASE_OFFSETS = doubleArrayOf(-.5, 0.0, .5)

        internal fun representativeColor(pixels: TypeIconPixels, circle: TypeIconCircle): TypeIconRgb? {
            val red = mutableListOf<Int>(); val green = mutableListOf<Int>(); val blue = mutableListOf<Int>()
            val step = max(1, (circle.radius / 32).toInt())
            for (y in max(0, floor(circle.centerY - circle.radius).toInt())..
                min(pixels.height - 1, ceil(circle.centerY + circle.radius).toInt()) step step) {
                for (x in max(0, floor(circle.centerX - circle.radius).toInt())..
                    min(pixels.width - 1, ceil(circle.centerX + circle.radius).toInt()) step step) {
                    val distance = hypot(x - circle.centerX, y - circle.centerY) / circle.radius
                    val pixel = pixels.pixel(x, y)
                    if (distance !in .63.. .88 || component(pixel, 24) < 240 || softWhite(pixel) >= .1) continue
                    red += component(pixel, 16); green += component(pixel, 8); blue += component(pixel, 0)
                }
            }
            // Neutral gray and bright yellow are actual color observations.
            // No surviving samples means missing evidence, never a black pixel.
            if (red.size < 8) return null
            fun median(values: MutableList<Int>): Double {
                values.sort()
                return (values[(values.size - 1) / 2] + values[values.size / 2]) / 2.0
            }
            return TypeIconRgb(median(red), median(green), median(blue))
        }

        private fun colorSimilarity(a: TypeIconRgb, b: TypeIconRgb): Double {
            val red = a.red - b.red; val green = a.green - b.green; val blue = a.blue - b.blue
            // A color difference has to matter: dividing by the full RGB cube's
            // diagonal gave unrelated colors nearly the same positive support.
            return exp(-(red * red + green * green + blue * blue) / (2 * 65.0 * 65.0))
        }
    }
}

/** Finds disks from their circular RGB boundary, including disks with no readable glyph. */
internal object TypeIconCircleDetector {
    private data class Edge(val x: Int, val y: Int, val normalX: Double, val normalY: Double)
    private data class Candidate(val x: Int, val y: Int, val radius: Int, val votes: Int)
    private data class Verified(val candidate: Candidate, val quality: Double)

    fun detect(source: TypeIconPixels): List<TypeIconCircle> {
        val (pixels, scale) = source.reduced(256)
        val width = pixels.width; val height = pixels.height
        if (min(width, height) < 12) return emptyList()
        val edges = mutableListOf<Edge>()
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val left = pixels.pixel(x - 1, y); val right = pixels.pixel(x + 1, y)
            val top = pixels.pixel(x, y - 1); val bottom = pixels.pixel(x, y + 1)
            var gx = 0; var gy = 0; var strongest = 0
            for (channel in RGB_CHANNELS) {
                val dx = component(right, channel) - component(left, channel)
                val dy = component(bottom, channel) - component(top, channel)
                val strength = dx * dx + dy * dy
                if (strength > strongest) { strongest = strength; gx = dx; gy = dy }
            }
            if (strongest < 20 * 20) continue
            val length = sqrt(strongest.toDouble())
            edges += Edge(x, y, gx / length, gy / length)
        }
        if (edges.isEmpty()) return emptyList()
        // Keep only a bounded set of circle proposals, rather than allocating
        // a width x height x radius Hough volume for a generous calibration box.
        val proposals = PriorityQueue<Candidate>(compareBy { it.votes })
        val outsideProposals = PriorityQueue<Candidate>(compareBy { it.votes })
        val maximumRadius = min(width, height) / 2
        val voteWidth = width + maximumRadius * 2
        val votes = IntArray(voteWidth * (height + maximumRadius * 2))
        val touched = IntArray(votes.size)
        for (radius in 6..maximumRadius) {
            votes.fill(0)
            var touchedCount = 0
            for (edge in edges) for (sign in SIGNS) {
                val x = (edge.x + sign * edge.normalX * radius).roundToInt()
                val y = (edge.y + sign * edge.normalY * radius).roundToInt()
                val index = (y + maximumRadius) * voteWidth + x + maximumRadius
                if (votes[index] == 0) touched[touchedCount++] = index
                votes[index]++
            }
            for (position in 0 until touchedCount) {
                val index = touched[position]
                val count = votes[index]
                val x = index % voteWidth - maximumRadius
                val y = index / voteWidth - maximumRadius
                val outside = x !in 0 until width || y !in 0 until height
                val pool = if (outside) outsideProposals else proposals
                if (count < (if (outside) 3 else 7) ||
                    (pool.size >= MAX_PROPOSALS && count <= requireNotNull(pool.peek()).votes)) continue
                pool += Candidate(x, y, radius, count)
                if (pool.size > MAX_PROPOSALS) pool.remove()
            }
        }
        fun verified(candidates: Collection<Candidate>) = candidates.mapNotNull { candidate ->
            verify(pixels, candidate)?.let { Verified(candidate, it) }
        }.sortedByDescending { it.quality }
        val selected = mutableListOf<Candidate>()
        fun select(candidates: List<Verified>) { for ((candidate) in candidates) {
            if (selected.any { hypot((it.x - candidate.x).toDouble(), (it.y - candidate.y).toDouble()) <
                    min(it.radius, candidate.radius) }) continue
            selected += candidate
        } }
        select(verified(proposals))
        // GO active type badges form one horizontal row at one disk scale. Only
        // a complete disk can establish that row for a mostly clipped sibling;
        // unrelated background curves above/below it are not missing badges.
        val rowAnchors = selected.filter { complete(it, width, height) }
        val partialSiblings = outsideProposals.filter { candidate ->
            candidate.x !in 0 until width && candidate.y in 0 until height &&
                rowAnchors.any { anchor ->
                    kotlin.math.abs(candidate.y - anchor.y) <= anchor.radius * .5 &&
                        candidate.radius.toDouble() in anchor.radius * (2.0 / 3)..anchor.radius * (4.0 / 3)
                }
        }
        // Never borrow an already found disk's boundary to invent a sibling.
        select(verified(partialSiblings.filterNot { overlapsKnownBoundary(it, selected, width, height) }))
        // Preserve an unreadable second badge and an unexpected third badge.
        // The witness owns whether the complete reading is usable.
        return selected.sortedWith(compareBy<Candidate> { it.x }.thenBy { it.y }).map {
            val measured = TypeIconCircle(it.x.toDouble(), it.y.toDouble(), it.radius.toDouble(),
                complete(it, width, height))
            val refined = if (measured.complete) refine(pixels, measured) else measured
            TypeIconCircle((refined.centerX + .5) / scale - .5, (refined.centerY + .5) / scale - .5,
                refined.radius / scale, refined.complete)
        }
    }

    private fun verify(pixels: TypeIconPixels, candidate: Candidate): Double? {
        var available = 0; var supported = 0; var whiteInside = 0
        val outside = candidate.x !in 0 until pixels.width || candidate.y !in 0 until pixels.height
        val innerRatio = if (outside) .98 else .85
        for (index in 0 until 48) {
            val angle = index * Math.PI / 24
            val dx = cos(angle); val dy = sin(angle)
            val outerX = candidate.x + dx * candidate.radius * 1.12
            val outerY = candidate.y + dy * candidate.radius * 1.12
            val innerX = candidate.x + dx * candidate.radius * innerRatio
            val innerY = candidate.y + dy * candidate.radius * innerRatio
            if (outerX !in 0.0..(pixels.width - 1).toDouble() ||
                outerY !in 0.0..(pixels.height - 1).toDouble() ||
                innerX !in 0.0..(pixels.width - 1).toDouble() ||
                innerY !in 0.0..(pixels.height - 1).toDouble()) continue
            val inner = pixels.sample(innerX, innerY)
            val outer = pixels.sample(outerX, outerY)
            available++
            if (softWhite(inner) > .1) whiteInside++
            var squaredDistance = 0
            for (channel in RGB_CHANNELS) {
                val difference = component(inner, channel) - component(outer, channel)
                squaredDistance += difference * difference
            }
            if (squaredDistance >= 18 * 18) supported++
        }
        val minimumVisible = if (outside) 6 else if (complete(candidate, pixels.width, pixels.height)) 32 else 24
        if (available < minimumVisible || supported < available * .7 || whiteInside > available * .1) return null
        return supported.toDouble() / available * ln(1.0 + candidate.votes)
    }

    private fun complete(candidate: Candidate, width: Int, height: Int): Boolean =
        candidate.x - candidate.radius >= -1 && candidate.y - candidate.radius >= -1 &&
            candidate.x + candidate.radius <= width && candidate.y + candidate.radius <= height

    private fun overlapsKnownBoundary(candidate: Candidate, known: List<Candidate>, width: Int, height: Int): Boolean {
        var visible = 0; var covered = 0
        for (index in 0 until 48) {
            val angle = index * Math.PI / 24
            val x = candidate.x + cos(angle) * candidate.radius
            val y = candidate.y + sin(angle) * candidate.radius
            if (x !in 0.0..(width - 1).toDouble() || y !in 0.0..(height - 1).toDouble()) continue
            visible++
            if (known.any { hypot(x - it.x, y - it.y) <= it.radius + 2 }) covered++
        }
        return visible == 0 || covered >= visible * .25
    }

    private fun refine(pixels: TypeIconPixels, measured: TypeIconCircle): TypeIconCircle {
        var best = measured
        var bestEdge = Double.NEGATIVE_INFINITY
        // Fit only the disk's boundary. A desired type or a better glyph match
        // must not choose the geometry of its own recognition input.
        for (offsetX in REFINEMENTS) for (offsetY in REFINEMENTS) for (offsetRadius in REFINEMENTS) {
            val radius = measured.radius + offsetRadius
            val x = measured.centerX + offsetX; val y = measured.centerY + offsetY
            if (radius < 6 || x - radius < -1 || y - radius < -1 ||
                x + radius > pixels.width || y + radius > pixels.height) continue
            var edge = 0.0; var available = 0
            for (index in PERIMETER_X.indices) {
                val dx = PERIMETER_X[index]; val dy = PERIMETER_Y[index]
                val innerX = x + dx * (radius - .75); val innerY = y + dy * (radius - .75)
                val outerX = x + dx * (radius + .75); val outerY = y + dy * (radius + .75)
                if (innerX !in 0.0..(pixels.width - 1).toDouble() || innerY !in 0.0..(pixels.height - 1).toDouble() ||
                    outerX !in 0.0..(pixels.width - 1).toDouble() || outerY !in 0.0..(pixels.height - 1).toDouble()) continue
                val inner = pixels.sample(innerX, innerY); val outer = pixels.sample(outerX, outerY)
                var distance = 0
                for (channel in RGB_CHANNELS) {
                    val difference = component(inner, channel) - component(outer, channel)
                    distance += difference * difference
                }
                edge += sqrt(distance.toDouble()); available++
            }
            if (available >= 64 && edge / available > bestEdge) {
                bestEdge = edge / available
                best = TypeIconCircle(x, y, radius, true)
            }
        }
        return best
    }

    private const val MAX_PROPOSALS = 160
    private val RGB_CHANNELS = intArrayOf(16, 8, 0)
    private val SIGNS = intArrayOf(-1, 1)
    private val REFINEMENTS = doubleArrayOf(-1.0, -.5, 0.0, .5, 1.0)
    private val PERIMETER_X = DoubleArray(96) { cos(it * Math.PI / 48) }
    private val PERIMETER_Y = DoubleArray(96) { sin(it * Math.PI / 48) }
}

private fun component(pixel: Int, shift: Int): Int = (pixel ushr shift) and 255

/** Soft neutral-white stroke coverage retains low-resolution antialiased arcs. */
private fun softWhite(pixel: Int): Double {
    if (component(pixel, 24) < 240) return 0.0
    val red = component(pixel, 16); val green = component(pixel, 8); val blue = component(pixel, 0)
    val darkest = min(red, min(green, blue)); val brightest = max(red, max(green, blue))
    return ((darkest - 170) / 75.0).coerceIn(0.0, 1.0) *
        ((55 - (brightest - darkest)) / 25.0).coerceIn(0.0, 1.0)
}
