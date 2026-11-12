package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.custody.TerminalFlashFeature
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val BattleCropBounds.width get() = right - left
private val BattleCropBounds.height get() = bottom - top

/** A pixel feature, not a Pokémon, disappearance, damage measurement or faint. */
internal data class TerminalParticlePoint(
    val x: Double, val y: Double, val area: Int, val width: Int, val height: Int,
    val freshFraction: Double, val haloCoverage: Double, val haloWeakestQuadrant: Double,
    val brightnessFalloff: Double,
)

internal enum class TerminalParticleReadStatus {
    SAMPLED, BASELINE, SOURCE_GAP, GEOMETRY_CHANGED, OLD_FRAME, INVALID, UNREADABLE, CLUTTER,
}

internal data class TerminalParticleFrameMeasurement(
    val status: TerminalParticleReadStatus,
    val sourceAtNanos: Long,
    val previousSourceAtNanos: Long? = null,
    val cluster: List<TerminalParticlePoint> = emptyList(),
    val flashes: List<TerminalFlashFeature> = emptyList(),
    val appearance: com.example.overdex.battle.custody.TerminalAppearanceMeasurement? = null,
)

/**
 * Measures one source-ordered territory using bounded, reused pixel buffers.
 * The caller owns attention deadlines, side/appearance/phase, visibility and
 * lifecycle. No constructor starts capture and no result establishes a faint.
 * An empty SAMPLED cluster only means this particular pixel pattern was absent.
 *
 * Port of the recorded-frame probe: fresh white cores, cool diffuse halos,
 * radial falloff, then a spatial group. Shields, QTE and entry effects can pass.
 */
internal class TerminalParticleFrameMeasurer {
    private data class Geometry(val width: Int, val height: Int, val bounds: BattleCropBounds)
    private var geometry: Geometry? = null
    private var previousAt: Long? = null
    private var newestAt: Long? = null
    private var current = IntArray(0)
    private var previous = IntArray(0)
    private var labels = IntArray(0)
    private var queue = IntArray(0)
    private var rimMarks = IntArray(0)

    fun reset() {
        geometry = null
        previousAt = null
        newestAt = null
    }

    fun measure(bitmap: Bitmap, bounds: BattleCropBounds, sourceAtNanos: Long): TerminalParticleFrameMeasurement =
        measurePixels(bitmap.width, bitmap.height, bounds, sourceAtNanos) { pixels ->
            bitmap.getPixels(pixels, 0, bounds.width, bounds.left, bounds.top, bounds.width, bounds.height)
        }

    internal fun measure(width: Int, height: Int, bounds: BattleCropBounds, sourceAtNanos: Long,
        pixelAt: (Int, Int) -> Int): TerminalParticleFrameMeasurement =
        measurePixels(width, height, bounds, sourceAtNanos) { pixels ->
            for (y in 0 until bounds.height) for (x in 0 until bounds.width) {
                pixels[y * bounds.width + x] = pixelAt(bounds.left + x, bounds.top + y)
            }
        }

    private fun measurePixels(width: Int, height: Int, bounds: BattleCropBounds, at: Long,
        read: (IntArray) -> Unit): TerminalParticleFrameMeasurement {
        fun result(status: TerminalParticleReadStatus, oldAt: Long? = null,
            points: List<TerminalParticlePoint> = emptyList(), flashes: List<TerminalFlashFeature> = emptyList()) =
            TerminalParticleFrameMeasurement(status, at, oldAt, points, flashes)
        if (at >= 0 && newestAt?.let { at <= it } == true) return result(TerminalParticleReadStatus.OLD_FRAME)
        if (at >= 0) newestAt = at
        if (at < 0 || width <= 0 || height <= 0 || bounds.left < 0 || bounds.top < 0 ||
            bounds.right > width || bounds.bottom > height || bounds.width < 8 || bounds.height < 8 ||
            bounds.width.toLong() * bounds.height > MAX_TERRITORY_PIXELS) {
            // A rejected surface cannot be a baseline for a later visual transition.
            previousAt = null
            return result(TerminalParticleReadStatus.INVALID)
        }
        val nextGeometry = Geometry(width, height, bounds)
        val oldGeometry = geometry
        val oldAt = previousAt
        val count = bounds.width * bounds.height
        if (current.size != count) {
            current = IntArray(count); previous = IntArray(count)
            labels = IntArray(count); queue = IntArray(count); rimMarks = IntArray(count)
        }
        try { read(current) } catch (_: RuntimeException) {
            previousAt = null
            return result(TerminalParticleReadStatus.UNREADABLE)
        }
        if (current.any { it ushr 24 < 230 }) {
            previousAt = null
            return result(TerminalParticleReadStatus.UNREADABLE)
        }
        val status = when {
            oldGeometry != null && oldGeometry != nextGeometry -> TerminalParticleReadStatus.GEOMETRY_CHANGED
            oldAt == null -> TerminalParticleReadStatus.BASELINE
            at - oldAt > MAX_FRAME_GAP_NANOS -> TerminalParticleReadStatus.SOURCE_GAP
            else -> TerminalParticleReadStatus.SAMPLED
        }
        val measured = if (status == TerminalParticleReadStatus.SAMPLED) findCluster(nextGeometry) else null
        val swap = previous; previous = current; current = swap
        geometry = nextGeometry; previousAt = at
        return result(if (status == TerminalParticleReadStatus.SAMPLED && measured == null)
            TerminalParticleReadStatus.CLUTTER else status,
            oldAt.takeIf { status == TerminalParticleReadStatus.SAMPLED }, measured?.cluster.orEmpty(), measured?.flashes.orEmpty())
    }

    private data class Features(val cluster: List<TerminalParticlePoint>, val flashes: List<TerminalFlashFeature>)

    private fun findCluster(space: Geometry): Features? {
        val w = space.bounds.width
        val h = space.bounds.height
        var white = 0
        for (i in current.indices) {
            val isWhite = low(current[i]) >= 230
            labels[i] = if (isWhite) -1 else 0
            if (isWhite) white++
        }
        // A broad whiteout cannot be separated into small particle evidence.
        if (white > current.size / 4) return null
        rimMarks.fill(0)
        var component = 0
        val candidates = ArrayList<TerminalParticlePoint>()
        val flashes = ArrayList<TerminalFlashFeature>()
        for (start in current.indices) {
            if (labels[start] != -1) continue
            if (++component > MAX_COMPONENTS) return null
            var head = 0
            var tail = 1
            queue[0] = start; labels[start] = component
            var left = w; var top = h; var right = 0; var bottom = 0
            var sumX = 0L; var sumY = 0L
            while (head < tail) {
                val index = queue[head++]
                val x = index % w; val y = index / w
                left = min(left, x); top = min(top, y); right = max(right, x); bottom = max(bottom, y)
                sumX += x; sumY += y
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val next = ny * w + nx
                    if (labels[next] == -1) { labels[next] = component; queue[tail++] = next }
                }
            }
            val bw = right - left + 1; val bh = bottom - top + 1
            val density = tail.toDouble() / (bw * bh)
            val scale = space.width.toDouble()
            if (tail.toDouble() in .002 * scale * scale.. .03 * scale * scale &&
                min(bw, bh) >= .015 * scale && bw.toDouble() / bh in .5..2.0 && density >= .4 &&
                left > 0 && top > 0 && right < w - 1 && bottom < h - 1) {
                flashes += TerminalFlashFeature(sumX.toDouble() / tail + space.bounds.left,
                    sumY.toDouble() / tail + space.bounds.top, tail, bw, bh, density)
                if (flashes.size > 16) return null
            }
            if (tail !in 4..1000 || min(bw, bh) < 2 || bw.toDouble() / bh !in .3..3.5 ||
                tail.toDouble() / (bw * bh) < .25 || left == 0 || top == 0 || right == w - 1 || bottom == h - 1) continue
            var coreSum = 0L; var ringSum = 0L; var ringSize = 0; var fresh = 0
            for (i in 0 until tail) {
                val index = queue[i]
                coreSum += low(current[index])
                if (low(previous[index]) < 210) fresh++
                val x = index % w; val y = index / w
                for (dy in -3..3) for (dx in -3..3) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val neighbor = ny * w + nx
                    if (labels[neighbor] != component && rimMarks[neighbor] != component) {
                        rimMarks[neighbor] = component
                        ringSum += low(current[neighbor]); ringSize++
                    }
                }
            }
            if (ringSize == 0 || coreSum.toDouble() / tail - ringSum.toDouble() / ringSize < 20 ||
                fresh.toDouble() / tail < .3) continue
            val x = sumX.toDouble() / tail; val y = sumY.toDouble() / tail
            val rx = max(2.0, bw / 2.0); val ry = max(2.0, bh / 2.0)
            // Do not invent the halo outside the caller's visible/calibrated crop.
            val haloLeft = (x - rx * 3).toInt(); val haloTop = (y - ry * 3).toInt()
            val haloRight = ceil(x + rx * 3).toInt(); val haloBottom = ceil(y + ry * 3).toInt()
            if (haloLeft < 0 || haloTop < 0 || haloRight >= w || haloBottom >= h) continue
            var innerCount = 0; var innerGood = 0; var innerSum = 0L; var outerCount = 0; var outerSum = 0L
            val quadCount = IntArray(4); val quadGood = IntArray(4)
            for (hy in haloTop..haloBottom) for (hx in haloLeft..haloRight) {
                val radius = ((hx - x) / rx).let { it * it } + ((hy - y) / ry).let { it * it }
                val color = current[hy * w + hx]
                val minimum = low(color)
                if (radius in 1.1 * 1.1..1.7 * 1.7) {
                    innerCount++; innerSum += minimum
                    val r = color ushr 16 and 255; val g = color ushr 8 and 255; val b = color and 255
                    val good = minimum >= 110 && maxOf(r, g, b) - minimum <= 80 && b >= r && g >= r
                    if (good) innerGood++
                    for (q in 0..3) {
                        val sx = if (q == 0 || q == 3) 1 else -1
                        val sy = if (q < 2) 1 else -1
                        if ((hx - x) * sx >= 0 && (hy - y) * sy >= 0) {
                            quadCount[q]++; if (good) quadGood[q]++
                        }
                    }
                }
                if (radius in 2.2 * 2.2..2.8 * 2.8) { outerCount++; outerSum += minimum }
            }
            if (innerCount == 0 || outerCount == 0) continue
            val coverage = innerGood.toDouble() / innerCount
            val weakest = (0..3).minOf { if (quadCount[it] == 0) 0.0 else quadGood[it].toDouble() / quadCount[it] }
            val falloff = innerSum.toDouble() / innerCount - outerSum.toDouble() / outerCount
            if (coverage < .5 || weakest < .25 || falloff < 15) continue
            candidates += TerminalParticlePoint(x + space.bounds.left, y + space.bounds.top, tail, bw, bh,
                fresh.toDouble() / tail, coverage, weakest, falloff)
            // Keep spatial grouping bounded under particle storms or a bad crop.
            // Exceeding the budget is unavailable, not a truncated positive.
            if (candidates.size > MAX_CANDIDATES) return null
        }
        var best = emptyList<TerminalParticlePoint>()
        val radius = space.width * .14
        for (point in candidates) {
            val cluster = candidates.filter { other ->
                val dx = point.x - other.x; val dy = point.y - other.y
                sqrt(dx * dx + dy * dy) <= radius
            }
            if (cluster.size < 4 || cluster.size <= best.size) continue
            if (cluster.maxOf { it.x } - cluster.minOf { it.x } >= space.width * .04 &&
                cluster.maxOf { it.y } - cluster.minOf { it.y } >= space.width * .04) best = cluster
        }
        return Features(best, flashes)
    }

    private fun low(color: Int) = minOf(color ushr 16 and 255, color ushr 8 and 255, color and 255)

    companion object {
        const val MAX_FRAME_GAP_NANOS = 250_000_000L
        const val MAX_TERRITORY_PIXELS = 1_500_000L
        private const val MAX_COMPONENTS = 4096
        private const val MAX_CANDIDATES = 128
    }
}
