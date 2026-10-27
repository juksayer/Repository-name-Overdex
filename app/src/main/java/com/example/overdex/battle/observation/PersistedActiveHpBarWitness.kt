package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Measures one active combatant HP bar from purpose-specific, already-preserved
 * HP evidence crops. It observes only outline geometry and visible fill; pulse
 * colour and fast-move interpretation remain separate witnesses.
 */
class PersistedActiveHpBarWitness(
    private val artifactStore: FileCropArtifactStore,
    private val crop: BattleCropContract,
    private val side: ActivePokemonSide,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null
    private var activeMatch: Match? = null
    private var lastSpeciesName: String? = null
    private val tracker = ActiveHpBarTracker()

    override val managesAvailability: Boolean = true

    override fun start(match: Match) {
        if (scope != null) return
        activeMatch = match
        lastSpeciesName = null
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { witnessScope ->
            // A current HP position matters more than an old one. The complete crop
            // sequence is already immutable Timeline evidence; this bounded lane only
            // prevents a slow bitmap read from delaying live tracking.
            val scheduledCrops = Channel<RealityArticle>(
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            witnessScope.launch {
                match.articles.collect { article ->
                    val species = article.payload as? ActivePokemonSpeciesWitnessed ?: return@collect
                    if (species.side == side && species.speciesName != lastSpeciesName) {
                        tracker.resetForNextCombatant()
                        lastSpeciesName = species.speciesName
                    }
                }
            }
            witnessScope.launch {
                match.activeHpCropArticles.collect { article ->
                    val captured = article.payload as? CropCaptured ?: return@collect
                    if (captured.cropProvenance.cropName == crop.cropName) {
                        scheduledCrops.trySend(article)
                    }
                }
            }
            witnessScope.launch {
                match.custody.submitAvailability(sourceId, true, System.currentTimeMillis())
                for (article in scheduledCrops) {
                    val captured = article.payload as? CropCaptured ?: continue
                    val bitmap = artifactStore.loadVerifiedPng(captured.artifact, captured.cropProvenance) ?: continue
                    try {
                        val measurement = tracker.measure(bitmap) ?: continue
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = ActiveHpBarMeasured(
                                side = side,
                                barLeft = measurement.left,
                                barTop = measurement.top,
                                barRight = measurement.right,
                                barBottom = measurement.bottom,
                                filledFraction = measurement.filledFraction
                            ),
                            timestamp = article.perceivedAt,
                            confidence = measurement.confidence,
                            evidenceReferences = listOf(article.id.value),
                            monotonicTimeNanos = article.monotonicTimeNanos ?: continue
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() {
        activeMatch?.custody?.submitAvailability(
            SourceId(observerId.id), false, System.currentTimeMillis()
        )
        activeMatch = null
        scope?.cancel("Active HP bar witness stopped")
        scope = null
        tracker.resetForNextCombatant()
    }

    companion object {
        fun player(artifactStore: FileCropArtifactStore) = PersistedActiveHpBarWitness(
            artifactStore = artifactStore,
            crop = BattleCropContracts.playerHpEvidence,
            side = ActivePokemonSide.PLAYER,
            observerId = ObserverId("PLAYER_ACTIVE_HP_BAR_WITNESS", ObserverSource.SCREEN_CAPTURE),
            name = "Player Active HP Bar Witness"
        )

        fun opponent(artifactStore: FileCropArtifactStore) = PersistedActiveHpBarWitness(
            artifactStore = artifactStore,
            crop = BattleCropContracts.opponentHpEvidence,
            side = ActivePokemonSide.OPPONENT,
            observerId = ObserverId("OPPONENT_ACTIVE_HP_BAR_WITNESS", ObserverSource.SCREEN_CAPTURE),
            name = "Opponent Active HP Bar Witness"
        )
    }
}

/** A measurement in the coordinate system of the cited HP evidence crop. */
internal data class ActiveHpBarMeasurement(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val filledFraction: Float,
    val confidence: Float
) {
    val width: Int get() = right - left
    val centerX: Float get() = (left + right) / 2f
}

/** Active bars retain their dimensions while their vertical position changes. */
internal object ActiveHpBarGeometry {
    fun matchesSize(width: Int, height: Int, anchorWidth: Int, anchorHeight: Int): Boolean =
        anchorWidth > 0 && anchorHeight > 0 &&
            width.toFloat() / anchorWidth in 0.90f..1.10f &&
            height.toFloat() / anchorHeight in 0.65f..1.50f
}

/**
 * Holds a per-combatant lock. Candidate matching retains the original X range
 * while allowing the bar to travel vertically with its Pokémon. If a frame has
 * no compatible bar, it emits no invented position and searches from the lock
 * again on the next preserved crop.
 */
internal class ActiveHpBarTracker {
    private var initialLock: ActiveHpBarMeasurement? = null
    private var latestLock: ActiveHpBarMeasurement? = null

    @Synchronized
    fun resetForNextCombatant() {
        initialLock = null
        latestLock = null
    }

    @Synchronized
    fun measure(bitmap: Bitmap): ActiveHpBarMeasurement? {
        val candidates = ActiveHpBarMeasurer.measureAll(bitmap)
        if (candidates.isEmpty()) return null
        // X belongs to the initial per-combatant lock. Score against the latest
        // location for continuity, but never allow successive small errors to
        // walk the accepted bar sideways into another bright battlefield edge.
        val anchor = initialLock
        val selected = if (anchor == null) {
            // Width used to dominate this choice. A cloud edge or battlefield
            // effect could therefore become the combatant's permanent X anchor,
            // even when the real bar was present in the same frame. The measurer
            // now folds the rectangular side edges and GO bar proportions into
            // confidence, so acquire the strongest shape rather than the widest
            // bright line.
            candidates.maxByOrNull { it.confidence }
        } else {
            candidates
                .filter { it.matches(anchor) }
                .minByOrNull { candidate ->
                    abs(candidate.centerX - (latestLock ?: anchor).centerX) * 4f +
                        abs(candidate.width - anchor.width).toFloat()
                }
        } ?: return null

        if (initialLock == null) initialLock = selected
        latestLock = selected
        return selected
    }

    private fun ActiveHpBarMeasurement.matches(anchor: ActiveHpBarMeasurement): Boolean {
        return ActiveHpBarGeometry.matchesSize(width, bottom - top, anchor.width, anchor.bottom - anchor.top) &&
            abs(centerX - anchor.centerX) <= anchor.width * 0.20f
    }
}

/**
 * Finds Pokémon GO active bars as a paired neutral outline with a left-aligned,
 * saturated fill band between the outlines. This deliberately excludes the
 * surrounding Pokémon art, text, and inactive-card measurements.
 */
internal object ActiveHpBarMeasurer {
    private data class OutlineRun(val y: Int, val left: Int, val right: Int, val colorMask: Int) {
        val width: Int get() = right - left
    }

    fun measureAll(bitmap: Bitmap): List<ActiveHpBarMeasurement> {
        if (bitmap.width < 32 || bitmap.height < 24) return emptyList()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return measureAll(bitmap.width, bitmap.height, null, pixels)
    }

    internal fun measureAll(
        width: Int,
        height: Int,
        pixelAt: (x: Int, y: Int) -> Int
    ): List<ActiveHpBarMeasurement> = measureAll(width, height, null, pixelAt)

    internal fun measureAll(
        width: Int,
        height: Int,
        expectedBarWidth: Int?,
        pixelAt: (x: Int, y: Int) -> Int
    ): List<ActiveHpBarMeasurement> {
        if (width < 32 || height < 24) return emptyList()
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) pixels[y * width + x] = pixelAt(x, y)
        return measureAll(width, height, expectedBarWidth, pixels)
    }

    /** Primitive hot path: no boxed Function2 calls for millions of pixels per second. */
    internal fun measureAll(
        width: Int,
        height: Int,
        expectedBarWidth: Int?,
        pixels: IntArray,
    ): List<ActiveHpBarMeasurement> {
        if (width < 32 || height < 24) return emptyList()
        require(pixels.size >= width * height)
        val pixelAt = HpPixels(width, pixels)
        // The outline is a foreground UI rectangle and is nearly continuous.
        // A larger gap joined separate cloud highlights into hundreds of fake
        // outlines, making one frame expensive enough to starve later frames.
        val maxGap = max(3, width / 100)
        // Active HP bars span roughly half of their purpose-specific crop. This
        // excludes short QTE and charge-effect outlines that can be bright and filled.
        val minBarWidth = expectedBarWidth?.let { max(32, (it * .85f).toInt()) } ?: max(100, width * 9 / 20)
        // Calibration can hug the bar. Its width relative to the search box is
        // not a property of the bar: the saved player crop is >90% occupied.
        // Keep a small real margin and require both end caps instead of rejecting
        // the user's tighter boxes. A clipped screen edge still cannot lock on.
        val maxBarWidth = expectedBarWidth?.let { minOf(width, (it * 1.15f).toInt()) } ?: width
        val outlines = buildList {
            val colorMasks = intArrayOf(1, 2, 8)
            val contrastMasks = intArrayOf(1, 8)
            val inset = ((expectedBarWidth ?: width) / 64).coerceIn(2, 4)
            for (y in 0 until height) {
                // White outline plus adjacent orange Pokémon art is not one
                // long outline. Keep the two border states separate geometrically.
                for (colorMask in colorMasks) {
                    addAll(findRuns(width, maxGap) { x ->
                        pixelAt(x, y) and colorMask != 0
                    }.filter { (left, right) -> right - left in minBarWidth..maxBarWidth }
                        .map { (left, right) -> OutlineRun(y, left, right, colorMask) })
                }
                // White Pokémon art or blue sky can join an outline to the
                // background. Its interior still provides a contrast boundary.
                // Recover that boundary without relaxing width, gaps, paired
                // colors, real end caps, or the left-aligned saturated fill.
                for (colorMask in contrastMasks) {
                    addAll(findRuns(width, maxGap) { x ->
                        pixelAt(x, y) and colorMask != 0 &&
                            ((y >= inset && pixelAt(x, y - inset) and colorMask == 0) ||
                                (y + inset < height && pixelAt(x, y + inset) and colorMask == 0))
                    }.filter { (left, right) -> right - left in minBarWidth..maxBarWidth }
                        .map { (left, right) -> OutlineRun(y, left, right, colorMask) })
                }
            }
        }
        if (outlines.isEmpty()) return emptyList()

        // The 62.5%-scale capture can reduce a real 16 px outline separation to
        // 10 px. Short QTE lines remain excluded by the paired-outline and fill
        // requirements, so do not discard a legitimate compact bar here.
        val minBarHeight = max(8, height / 160)
        val maxBarHeight = max(28, height / 24)
        val edgeTolerance = max(12, width / 20)
        val candidates = mutableListOf<ActiveHpBarMeasurement>()
        for (topIndex in outlines.indices) {
            val top = outlines[topIndex]
            if (!hasSaturatedFillBelow(top, width, height, pixelAt)) continue
            for (bottomIndex in topIndex + 1 until outlines.size) {
                val bottom = outlines[bottomIndex]
                val barHeight = bottom.y - top.y
                if (barHeight > maxBarHeight) break
                if (top.colorMask != bottom.colorMask || barHeight < minBarHeight ||
                    abs(top.left - bottom.left) > edgeTolerance ||
                    abs(top.right - bottom.right) > edgeTolerance
                ) continue

                var left = max(top.left, bottom.left)
                var right = minOf(top.right, bottom.right)
                if (right - left < minBarWidth) continue
                val edgeMargin = MIN_EDGE_MARGIN_PX
                // Adjacent white Pokémon art can join the horizontal outline
                // all the way to a tight crop's edge. Locate the interior-facing
                // end cap before rejecting it as clipped. This never accepts a
                // genuinely cut-off bar without both vertical caps.
                if (left < edgeMargin || right > width - edgeMargin) {
                    val search = max(12, width / 12)
                    if (left < edgeMargin) {
                        left = (minOf(left + search, right - 1) downTo edgeMargin)
                            .firstOrNull { x -> edgeSupportAt(x, top.y, bottom.y, pixelAt, top.colorMask) >= 0.8f }
                            ?: continue
                    }
                    if (right > width - edgeMargin) {
                        right = ((right - search).coerceAtLeast(left + 1) until width - edgeMargin)
                            .firstOrNull { x -> edgeSupportAt(x, top.y, bottom.y, pixelAt, top.colorMask) >= 0.8f }
                            ?.plus(1) ?: continue
                    }
                }
                if (left < edgeMargin || right > width - edgeMargin) continue
                if (right - left < minBarWidth) continue
                val edgeSupport = verticalEdgeSupport(
                    left = left,
                    top = top.y,
                    right = right,
                    bottom = bottom.y,
                    width = width,
                    pixelAt = pixelAt,
                    colorMask = top.colorMask,
                )
                if (edgeSupport < MIN_VERTICAL_EDGE_SUPPORT) continue
                measureFill(left, top.y, right, bottom.y, width, pixelAt,
                    requireContinuousFill = top.colorMask == 8)?.let { measurement ->
                    val widthRatio = measurement.width.toFloat() / (expectedBarWidth ?: width)
                    val targetWidthRatio = if (expectedBarWidth != null) 1f else EXPECTED_WIDTH_RATIO
                    val widthScore = (1f - abs(widthRatio - targetWidthRatio) / WIDTH_RATIO_TOLERANCE)
                        .coerceIn(0f, 1f)
                    val heightRatio = (measurement.bottom - measurement.top).toFloat() /
                        measurement.width.coerceAtLeast(1)
                    val heightScore = (1f - abs(heightRatio - EXPECTED_HEIGHT_RATIO) / HEIGHT_RATIO_TOLERANCE)
                        .coerceIn(0f, 1f)
                    val shapeConfidence = edgeSupport * 0.60f + widthScore * 0.25f + heightScore * 0.15f
                    candidates += measurement.copy(
                        confidence = (measurement.confidence * 0.45f + shapeConfidence * 0.55f)
                            .coerceIn(0f, 0.99f)
                    )
                }
            }
        }
        return candidates
            .distinctBy { listOf(it.left, it.top, it.right, it.bottom, it.filledFraction) }
            .sortedByDescending { it.confidence }
    }

    /**
     * The real GO bar has two tall neutral (or pulsing orange) end caps. Requiring
     * both is the inexpensive distinction between the UI rectangle and long
     * highlights in clouds, attacks, Pokémon art, and the battlefield.
     */
    private fun verticalEdgeSupport(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        width: Int,
        pixelAt: HpPixels,
        colorMask: Int,
    ): Float {
        if (bottom <= top || right <= left) return 0f
        fun bestNear(edgeX: Int, interiorDirection: Int): Float {
            var best = 0f
            val searchLeft = (edgeX - EDGE_SEARCH_RADIUS).coerceAtLeast(0)
            val searchRight = (edgeX + EDGE_SEARCH_RADIUS).coerceAtMost(width - 1)
            for (x in searchLeft..searchRight) {
                var matches = 0
                var samples = 0
                for (y in top..bottom) {
                    samples++
                    // Blue scenery alone is not an end cap. The blue pulse
                    // must end against the bar interior on each side; otherwise
                    // the sky can widen the denominator and manufacture HP loss.
                    val supported = if (colorMask == 8) {
                        val interiorX = (x + interiorDirection * (EDGE_SEARCH_RADIUS + 1))
                            .coerceIn(0, width - 1)
                        pixelAt(x, y) and colorMask != 0 && pixelAt(interiorX, y) and colorMask == 0
                    } else isTrackableOutline(pixelAt(x, y))
                    if (supported) matches++
                }
                if (samples > 0) best = max(best, matches.toFloat() / samples)
            }
            return best
        }
        return minOf(bestNear(left, 1), bestNear(right - 1, -1))
    }

    private fun edgeSupportAt(x: Int, top: Int, bottom: Int, pixelAt: HpPixels, colorMask: Int): Float =
        (top..bottom).count { y ->
            pixelAt(x, y) and colorMask != 0
        }.toFloat() / (bottom - top + 1)

    private fun hasSaturatedFillBelow(
        top: OutlineRun,
        width: Int,
        height: Int,
        pixelAt: HpPixels
    ): Boolean {
        val searchBottom = minOf(height, top.y + max(14, height / 100))
        // Low/red HP is still a visible measurement, even below 12.5%.
        val requiredRun = max(4, top.width / 80)
        for (y in top.y + 1 until searchBottom) {
            val left = top.left.coerceIn(0, width)
            val right = top.right.coerceIn(left, width)
            if (longestRun(left, right) { x -> isSaturatedFill(pixelAt(x, y)) } >= requiredRun) return true
        }
        return false
    }

    private fun measureFill(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cropWidth: Int,
        pixelAt: HpPixels,
        requireContinuousFill: Boolean,
    ): ActiveHpBarMeasurement? {
        val inset = max(2, (right - left) / 80)
        val innerLeft = left + inset
        val innerRight = right - inset
        // Inspect the centre of the interior. A thick orange outline bleeds
        // into its first rows and used to turn a nearly empty bar into 100% HP.
        val verticalInset = max(2, (bottom - top) / 4)
        val innerTop = top + verticalInset
        val innerBottom = bottom - verticalInset
        if (innerRight - innerLeft < 24 || innerBottom - innerTop < 3) return null

        val requiredFilledRows = max(2, (innerBottom - innerTop + 1) / 2)
        val filledColumns = BooleanArray(innerRight - innerLeft) { index ->
            val x = innerLeft + index
            var filledRows = 0
            for (y in innerTop until innerBottom) {
                if (isSaturatedFill(pixelAt(x, y))) filledRows++
            }
            filledRows >= requiredFilledRows
        }
        val firstFilledIndex = filledColumns.indexOfFirst { it }
        if (firstFilledIndex < 0) return null
        // HP fill begins at the left edge of its bar. A distant coloured object
        // inside a white frame is not a valid active-HP measurement.
        if (firstFilledIndex > max(12, filledColumns.size / 5)) return null

        var lastFilledIndex = firstFilledIndex
        var gap = 0
        val allowedGap = max(3, cropWidth / 160)
        for (index in firstFilledIndex + 1 until filledColumns.size) {
            if (filledColumns[index]) {
                lastFilledIndex = index
                gap = 0
            } else if (++gap > allowedGap) {
                break
            }
        }
        val minimumFilledWidth = max(4, filledColumns.size / 40)
        // A blue pulse/overlay can interrupt the green interior. If another
        // substantial fill segment continues beyond that hole, the left fragment
        // is not a valid HP endpoint. Abstain instead of bridging unseen pixels
        // or publishing a large, false loss from the shortened fragment.
        if (requireContinuousFill && longestRun(lastFilledIndex + 1, filledColumns.size) {
                filledColumns[it]
            } >= minimumFilledWidth) return null
        val filledWidth = lastFilledIndex - firstFilledIndex + 1
        if (filledWidth < minimumFilledWidth) return null

        val fraction = ((lastFilledIndex + 1).toFloat() / filledColumns.size).coerceIn(0f, 1f)
        val outlineCoverage = (right - left).toFloat() / cropWidth
        val confidence = (0.62f + (outlineCoverage.coerceAtMost(1f) * 0.20f) +
            (filledWidth.toFloat() / filledColumns.size * 0.18f)).coerceIn(0f, 1f)
        return ActiveHpBarMeasurement(left, top, right, bottom, fraction, confidence)
    }

    private inline fun findRuns(width: Int, allowedGap: Int, predicate: (Int) -> Boolean): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        var start = -1
        var lastMatch = -1
        for (x in 0 until width) {
            if (predicate(x)) {
                if (start < 0) start = x
                lastMatch = x
            } else if (start >= 0 && x - lastMatch > allowedGap) {
                runs += start to (lastMatch + 1)
                start = -1
                lastMatch = -1
            }
        }
        if (start >= 0) runs += start to (lastMatch + 1)
        return runs
    }

    private inline fun longestRun(start: Int, endExclusive: Int, predicate: (Int) -> Boolean): Int {
        var longest = 0
        var current = 0
        for (x in start until endExclusive) {
            if (predicate(x)) {
                current++
                longest = max(longest, current)
            } else {
                current = 0
            }
        }
        return longest
    }

    private class HpPixels(private val width: Int, pixels: IntArray) {
        private val flags = ByteArray(pixels.size)
        init {
            for (i in pixels.indices) {
                val color = pixels[i]
                val r = color ushr 16 and 255
                val g = color ushr 8 and 255
                val b = color and 255
                val neutral = minOf(r, g, b) >= 175 && maxOf(r, g, b) - minOf(r, g, b) <= 48
                val orange = r >= 155 && g in 65..210 && b <= 135 && r - g >= 22 && g - b >= 12
                val blueOutline = isBlueHpBarBorder(r, g, b)
                val green = g >= 145 && g - r >= 55 && g - b >= 15
                val yellow = r >= 145 && g >= 145 && b <= 135 && abs(r - g) <= 55
                val red = r >= 145 && g <= 130 && b <= 145 && r - g >= 65
                flags[i] = ((if (neutral) 1 else 0) or (if (orange) 2 else 0) or (if (blueOutline) 8 else 0) or
                    (if ((green || yellow || red) && !orange) 4 else 0)).toByte()
            }
        }
        operator fun invoke(x: Int, y: Int): Int = flags[y * width + x].toInt()
    }

    private fun isTrackableOutline(flags: Int) = flags and 11 != 0
    private fun isSaturatedFill(flags: Int) = flags and 4 != 0

    private const val EDGE_SEARCH_RADIUS = 3
    private const val MIN_EDGE_MARGIN_PX = 2
    private const val MIN_VERTICAL_EDGE_SUPPORT = 0.55f
    private const val EXPECTED_WIDTH_RATIO = 0.60f
    private const val WIDTH_RATIO_TOLERANCE = 0.18f
    private const val EXPECTED_HEIGHT_RATIO = 0.09f
    private const val HEIGHT_RATIO_TOLERANCE = 0.065f
}
