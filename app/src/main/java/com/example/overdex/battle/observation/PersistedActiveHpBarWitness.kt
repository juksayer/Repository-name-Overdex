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
    private data class OutlineRun(val y: Int, val left: Int, val right: Int) {
        val width: Int get() = right - left
    }

    fun measureAll(bitmap: Bitmap): List<ActiveHpBarMeasurement> {
        if (bitmap.width < 32 || bitmap.height < 24) return emptyList()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return measureAll(bitmap.width, bitmap.height) { x, y -> pixels[y * bitmap.width + x] }
    }

    internal fun measureAll(
        width: Int,
        height: Int,
        pixelAt: (x: Int, y: Int) -> Int
    ): List<ActiveHpBarMeasurement> {
        if (width < 32 || height < 24) return emptyList()
        // The outline is a foreground UI rectangle and is nearly continuous.
        // A larger gap joined separate cloud highlights into hundreds of fake
        // outlines, making one frame expensive enough to starve later frames.
        val maxGap = max(3, width / 100)
        // Active HP bars span roughly half of their purpose-specific crop. This
        // excludes short QTE and charge-effect outlines that can be bright and filled.
        val minBarWidth = max(100, width * 9 / 20)
        // Keep room for a tightly calibrated crop while excluding a full-width
        // screen edge. Shape confidence still strongly favors the normal 60% bar.
        val maxBarWidth = width * 9 / 10
        val outlines = buildList {
            for (y in 0 until height) {
                addAll(findRuns(width, maxGap) { x -> isTrackableOutline(pixelAt(x, y)) }
                    .filter { (left, right) -> right - left in minBarWidth..maxBarWidth }
                    .map { (left, right) -> OutlineRun(y, left, right) })
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
                if (barHeight < minBarHeight ||
                    abs(top.left - bottom.left) > edgeTolerance ||
                    abs(top.right - bottom.right) > edgeTolerance
                ) continue

                val left = max(top.left, bottom.left)
                val right = minOf(top.right, bottom.right)
                if (right - left < minBarWidth) continue
                val edgeMargin = max(MIN_EDGE_MARGIN_PX, width / 50)
                if (left < edgeMargin || right > width - edgeMargin) continue
                val edgeSupport = verticalEdgeSupport(
                    left = left,
                    top = top.y,
                    right = right,
                    bottom = bottom.y,
                    width = width,
                    pixelAt = pixelAt
                )
                if (edgeSupport < MIN_VERTICAL_EDGE_SUPPORT) continue
                measureFill(left, top.y, right, bottom.y, width, pixelAt)?.let { measurement ->
                    val widthRatio = measurement.width.toFloat() / width
                    val widthScore = (1f - abs(widthRatio - EXPECTED_WIDTH_RATIO) / WIDTH_RATIO_TOLERANCE)
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
        pixelAt: (x: Int, y: Int) -> Int
    ): Float {
        if (bottom <= top || right <= left) return 0f
        fun bestNear(edgeX: Int): Float {
            var best = 0f
            val searchLeft = (edgeX - EDGE_SEARCH_RADIUS).coerceAtLeast(0)
            val searchRight = (edgeX + EDGE_SEARCH_RADIUS).coerceAtMost(width - 1)
            for (x in searchLeft..searchRight) {
                var matches = 0
                var samples = 0
                for (y in top..bottom) {
                    samples++
                    if (isTrackableOutline(pixelAt(x, y))) matches++
                }
                if (samples > 0) best = max(best, matches.toFloat() / samples)
            }
            return best
        }
        return minOf(bestNear(left), bestNear(right - 1))
    }

    private fun hasSaturatedFillBelow(
        top: OutlineRun,
        width: Int,
        height: Int,
        pixelAt: (x: Int, y: Int) -> Int
    ): Boolean {
        val searchBottom = minOf(height, top.y + max(14, height / 100))
        val requiredRun = max(20, top.width / 8)
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
        pixelAt: (x: Int, y: Int) -> Int
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
        val filledWidth = lastFilledIndex - firstFilledIndex + 1
        if (filledWidth < max(4, filledColumns.size / 40)) return null

        val fraction = ((lastFilledIndex + 1).toFloat() / filledColumns.size).coerceIn(0f, 1f)
        val outlineCoverage = (right - left).toFloat() / cropWidth
        val confidence = (0.62f + (outlineCoverage.coerceAtMost(1f) * 0.20f) +
            (filledWidth.toFloat() / filledColumns.size * 0.18f)).coerceIn(0f, 1f)
        return ActiveHpBarMeasurement(left, top, right, bottom, fraction, confidence)
    }

    private fun findRuns(width: Int, allowedGap: Int, predicate: (Int) -> Boolean): List<Pair<Int, Int>> {
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

    private fun longestRun(start: Int, endExclusive: Int, predicate: (Int) -> Boolean): Int {
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

    private fun isBrightNeutral(color: Int): Boolean {
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        return minOf(red, green, blue) >= 175 && maxOf(red, green, blue) - minOf(red, green, blue) <= 48
    }

    /** A hit changes the normally white outline to orange for only a frame or two. */
    private fun isTrackableOutline(color: Int): Boolean =
        isBrightNeutral(color) || isOrangePulseOutline(color)

    private fun isOrangePulseOutline(color: Int): Boolean {
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        return red >= 155 && green in 65..210 && blue <= 135 &&
            red - green >= 22 && green - blue >= 12
    }

    private fun isSaturatedFill(color: Int): Boolean {
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        // HP is green, yellow, or red. Orange is lost HP / a border pulse,
        // and other saturated attack colours are not remaining health.
        val greenFill = green >= 145 && green - red >= 55 && green - blue >= 15
        val yellowFill = red >= 145 && green >= 145 && blue <= 135 && abs(red - green) <= 55
        val redFill = red >= 145 && green < 100 && blue <= 135 && red - green >= 65
        return (greenFill || yellowFill || redFill) && !isOrangePulseOutline(color)
    }

    private const val EDGE_SEARCH_RADIUS = 3
    private const val MIN_EDGE_MARGIN_PX = 6
    private const val MIN_VERTICAL_EDGE_SUPPORT = 0.55f
    private const val EXPECTED_WIDTH_RATIO = 0.60f
    private const val WIDTH_RATIO_TOLERANCE = 0.18f
    private const val EXPECTED_HEIGHT_RATIO = 0.09f
    private const val HEIGHT_RATIO_TOLERANCE = 0.065f
}
