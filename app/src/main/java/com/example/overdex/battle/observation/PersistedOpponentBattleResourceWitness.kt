package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.battle.custody.PlayerPokeBallCountMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.model.observation.OcrBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Turns a preserved, single-purpose badge crop into a stable resource count. */
class PersistedOpponentBattleResourceWitness private constructor(
    private val artifactStore: FileCropArtifactStore,
    private val cropName: String,
    private val resource: OpponentBattleResource,
    private val maximumCount: Int,
    private val acceptsPixel: (Int) -> Boolean,
    private val playerShields: Boolean = false,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var candidate: Int? = null
                var candidateArticle: com.example.overdex.battle.reality.RealityArticle? = null
                var delivered: Int? = null
                match.articles
                    .filter { (it.payload as? CropCaptured)?.cropProvenance?.cropName == cropName }
                    .collect { article ->
                        val captured = article.payload as CropCaptured
                        val bitmap = artifactStore.loadVerifiedPng(captured.artifact, captured.cropProvenance)
                            ?: return@collect
                        val count = try {
                            if (resource == OpponentBattleResource.POKE_BALLS) OpponentBattleResourceCounter.pokeBallCount(bitmap, captured.overlayBounds)
                            else OpponentBattleResourceCounter.shieldCount(bitmap, captured.overlayBounds)
                        } finally {
                            bitmap.recycle()
                        }
                        if (count == null) { candidate = null; candidateArticle = null; return@collect }
                        if (candidate != count) {
                            candidate = count
                            candidateArticle = article
                            return@collect
                        }
                        // Repeated ball snapshots also prove that an intervening
                        // species change was a switch, rather than a faint.
                        if (resource != OpponentBattleResource.POKE_BALLS && delivered == count) return@collect
                        val observed = if (delivered != count) candidateArticle ?: article else article
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = if (playerShields) com.example.overdex.battle.custody.PlayerShieldCountMeasured(count, maximumCount)
                                else OpponentBattleResourceCountMeasured(resource, count, maximumCount),
                            timestamp = observed.perceivedAt,
                            confidence = null,
                            evidenceReferences = listOfNotNull(candidateArticle?.id?.value, article.id.value).distinct(),
                            monotonicTimeNanos = observed.monotonicTimeNanos ?: return@collect
                        )
                        delivered = count
                        candidateArticle = article
                    }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Witness stopped")
        scope = null
    }

    companion object {
        fun pokeBalls(artifactStore: FileCropArtifactStore) = PersistedOpponentBattleResourceWitness(
            artifactStore = artifactStore,
            cropName = BattleCropContracts.opponentPokeBalls.cropName,
            resource = OpponentBattleResource.POKE_BALLS,
            maximumCount = 3,
            acceptsPixel = OpponentBattleResourceCounter::isBrightPokeBallRed,
            observerId = ObserverId("OPPONENT_POKE_BALLS_WITNESS", ObservationSource.SCREEN_CAPTURE),
            name = "Opponent Poké Balls Witness"
        )

        fun playerShields(artifactStore: FileCropArtifactStore) = PersistedOpponentBattleResourceWitness(
            artifactStore = artifactStore,
            cropName = BattleCropContracts.playerShields.cropName,
            resource = OpponentBattleResource.SHIELDS,
            maximumCount = 2,
            acceptsPixel = OpponentBattleResourceCounter::isShieldColor,
            playerShields = true,
            observerId = ObserverId("PLAYER_SHIELDS_WITNESS", ObservationSource.SCREEN_CAPTURE),
            name = "Player Shields Witness"
        )

        fun shields(artifactStore: FileCropArtifactStore) = PersistedOpponentBattleResourceWitness(
            artifactStore = artifactStore,
            cropName = BattleCropContracts.opponentShields.cropName,
            resource = OpponentBattleResource.SHIELDS,
            maximumCount = 2,
            acceptsPixel = OpponentBattleResourceCounter::isShieldColor,
            observerId = ObserverId("OPPONENT_SHIELDS_WITNESS", ObservationSource.SCREEN_CAPTURE),
            name = "Opponent Shields Witness"
        )
    }
}

/** Reads the player's dedicated Poké Ball strip without inspecting the rest of the badge. */
class PersistedPlayerPokeBallWitness(
    private val artifactStore: FileCropArtifactStore,
    override val observerId: ObserverId = ObserverId(
        "PLAYER_POKE_BALLS_WITNESS",
        ObservationSource.SCREEN_CAPTURE
    ),
    override val name: String = "Player Poké Balls Witness"
) : Observer {
    private var scope: CoroutineScope? = null

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                var candidate: Int? = null
                var candidateArticle: com.example.overdex.battle.reality.RealityArticle? = null
                var delivered: Int? = null
                match.articles
                    .filter {
                        (it.payload as? CropCaptured)?.cropProvenance?.cropName ==
                            BattleCropContracts.playerPokeBalls.cropName
                    }
                    .collect { article ->
                        val captured = article.payload as CropCaptured
                        val bitmap = artifactStore.loadVerifiedPng(captured.artifact, captured.cropProvenance)
                            ?: return@collect
                        val count = try {
                            OpponentBattleResourceCounter.pokeBallCount(bitmap, captured.overlayBounds)
                        } finally {
                            bitmap.recycle()
                        }
                        if (count == null) { candidate = null; candidateArticle = null; return@collect }
                        if (candidate != count) {
                            candidate = count
                            candidateArticle = article
                            return@collect
                        }
                        val observed = if (delivered != count) candidateArticle ?: article else article
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = PlayerPokeBallCountMeasured(count),
                            timestamp = observed.perceivedAt,
                            confidence = null,
                            evidenceReferences = listOfNotNull(candidateArticle?.id?.value, article.id.value).distinct(),
                            monotonicTimeNanos = observed.monotonicTimeNanos ?: return@collect
                        )
                        delivered = count
                        candidateArticle = article
                    }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Witness stopped")
        scope = null
    }
}

internal object OpponentBattleResourceCounter {
    fun shieldCount(bitmap: Bitmap, overlayBounds: OcrBox? = null): Int? =
        shieldCount(bitmap.width, bitmap.height, overlayBounds, bitmap::getPixel)

    internal fun shieldCount(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Int? =
        shieldCount(width, height, null, pixelAt)

    internal fun shieldCount(width: Int, height: Int, overlayBounds: OcrBox?, pixelAt: (Int, Int) -> Int): Int? {
        if (width < 8 || height < 4 || !hasUncoveredCaps(width, height, overlayBounds)) return null
        // A covered/disappeared badge is unavailable, not two shields spent.
        var badgePixels = 0
        var visiblePixels = 0
        val glyphPixels = IntArray(2)
        for (y in 0 until height) for (x in 0 until width) {
            if (covered(x, y, overlayBounds)) continue
            visiblePixels++
            val c = pixelAt(x, y)
            val r = c ushr 16 and 255; val g = c ushr 8 and 255; val b = c and 255
            if (minOf(r, g, b) >= 180 && maxOf(r, g, b) - minOf(r, g, b) <= 50) badgePixels++
            // Spent shields keep their dark purple silhouette. Require a
            // silhouette in both slots before interpreting absent bright color.
            if (isShieldColor(c) || (r >= g + 6 && b >= g + 6 && maxOf(r, g, b) in 40..180)) {
                glyphPixels[(x * 2 / width).coerceAtMost(1)]++
            }
        }
        if (badgePixels < visiblePixels / 4 || glyphPixels.any { it < maxOf(3, width * height / 100) }) return null
        return count(width, height, { x, y -> if (covered(x, y, overlayBounds)) 0 else pixelAt(x, y) }, 2, ::isShieldColor)
    }

    fun pokeBallCount(bitmap: Bitmap, overlayBounds: OcrBox? = null): Int? =
        pokeBallCount(bitmap.width, bitmap.height, overlayBounds, bitmap::getPixel)

    /** Missing/covered badge pixels are unavailable evidence, never zero survivors. */
    internal fun pokeBallCount(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Int? =
        pokeBallCount(width, height, null, pixelAt)

    internal fun pokeBallCount(width: Int, height: Int, overlayBounds: OcrBox?, pixelAt: (Int, Int) -> Int): Int? {
        if (width < 3 || height < 3 || !hasUncoveredCaps(width, height, overlayBounds)) return null
        var badgePixels = 0
        var visiblePixels = 0
        val outline = BooleanArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            // Our sprite cards also have pale backgrounds and dark outlines.
            // Their question marks must never become three spent Poké Balls.
            // Use the bounds from THIS captured frame, not the current HUD.
            if (covered(x, y, overlayBounds)) continue
            visiblePixels++
            val pixel = pixelAt(x, y)
            val r = pixel ushr 16 and 255
            val g = pixel ushr 8 and 255
            val b = pixel and 255
            val high = maxOf(r, g, b)
            val low = minOf(r, g, b)
            if (low >= 180 && high - low <= 50) badgePixels++
            // The red cap can remain visible while the HUD covers its lower
            // black rim. A spent ball has a dark cap instead.
            outline[y * width + x] = (high <= 180 && high - low <= 45) || isBrightPokeBallRed(pixel)
        }
        if (badgePixels < visiblePixels / 4) return null

        // A sliding/covered badge can leave a white corner against black. The
        // black area spans all three slots, but is not three spent balls. Only
        // count ball-sized silhouettes, not an edge-connected background. A
        // real ball may touch one crop edge when calibration is tight.
        removeEdgeBackground(outline, width, height)
        val outlinePixels = IntArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            if (outline[y * width + x]) outlinePixels[(x * 3 / width).coerceAtMost(2)]++
        }
        // Each of the three fixed slots retains an outline even when its ball
        // darkens. Requiring the badge and slots rejects black masks and sky.
        if (outlinePixels.any { it < maxOf(2, width * height / 400) }) return null
        return count(width, height, { x, y -> if (covered(x, y, overlayBounds)) 0 else pixelAt(x, y) }, 3, ::isBrightPokeBallRed)
    }

    private fun covered(x: Int, y: Int, bounds: OcrBox?): Boolean = bounds != null &&
        x + .5f >= bounds.left && x + .5f < bounds.right && y + .5f >= bounds.top && y + .5f < bounds.bottom

    private fun hasUncoveredCaps(width: Int, height: Int, bounds: OcrBox?): Boolean {
        if (bounds == null) return true
        // The upper 40% contains the bright caps in a calibrated badge strip.
        // Reading only lower rims could turn an obscured live ball into a KO.
        // A HUD below this band may hide the rims while leaving all caps usable.
        val capRows = kotlin.math.ceil(height * .4).toInt()
        for (y in 0 until capRows) for (x in 0 until width) {
            if (covered(x, y, bounds)) return false
        }
        return true
    }

    private fun removeEdgeBackground(pixels: BooleanArray, width: Int, height: Int) {
        val pending = IntArray(pixels.size)
        val visited = BooleanArray(pixels.size)
        fun inspectComponent(start: Int) {
            if (!pixels[start] || visited[start]) return
            var head = 0
            var tail = 0
            var left = width
            var right = 0
            var top = height
            var bottom = 0
            fun enqueue(index: Int) {
                if (!pixels[index] || visited[index]) return
                visited[index] = true
                pending[tail++] = index
            }
            enqueue(start)
            while (head < tail) {
                val index = pending[head++]
                val x = index % width
                val y = index / width
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
                if (x > 0) enqueue(index - 1)
                if (x < width - 1) enqueue(index + 1)
                if (y > 0) enqueue(index - width)
                if (y < height - 1) enqueue(index + width)
            }
            if (right - left + 1 > (width + 2) / 3 || (top == 0 && bottom == height - 1)) {
                for (i in 0 until tail) pixels[pending[i]] = false
            }
        }
        for (x in 0 until width) {
            inspectComponent(x)
            inspectComponent((height - 1) * width + x)
        }
        for (y in 0 until height) {
            inspectComponent(y * width)
            inspectComponent(y * width + width - 1)
        }
    }

    fun count(bitmap: Bitmap, maximumCount: Int, acceptsPixel: (Int) -> Boolean): Int {
        return count(bitmap.width, bitmap.height, bitmap::getPixel, maximumCount, acceptsPixel)
    }

    internal fun count(
        width: Int,
        height: Int,
        pixelAt: (Int, Int) -> Int,
        maximumCount: Int,
        acceptsPixel: (Int) -> Boolean
    ): Int {
        if (width < 3 || height < 3) return 0
        val minimumPixels = maxOf(1, height / 12)
        val occupied = BooleanArray(width) { x ->
            var pixels = 0
            for (y in 0 until height) if (acceptsPixel(pixelAt(x, y))) pixels++
            pixels >= minimumPixels
        }
        val maximumGap = maxOf(1, width / 50)
        val minimumRun = maxOf(2, width / (maximumCount * 16))
        var clusters = 0
        var runStart = -1
        var lastOccupied = -1
        for (x in occupied.indices) {
            if (occupied[x]) {
                if (runStart < 0) runStart = x
                lastOccupied = x
            } else if (runStart >= 0 && x - lastOccupied > maximumGap) {
                if (lastOccupied - runStart + 1 >= minimumRun) clusters++
                runStart = -1
                lastOccupied = -1
            }
        }
        if (runStart >= 0 && lastOccupied - runStart + 1 >= minimumRun) clusters++
        return clusters.coerceIn(0, maximumCount)
    }

    fun isBrightPokeBallRed(color: Int): Boolean {
        val r = color ushr 16 and 0xff
        val g = color ushr 8 and 0xff
        val b = color and 0xff
        return r >= 145 && r - g >= 45 && r - b >= 35
    }

    fun isShieldColor(color: Int): Boolean {
        val r = color ushr 16 and 0xff
        val g = color ushr 8 and 0xff
        val b = color and 0xff
        val maximum = maxOf(r, g, b)
        val minimum = minOf(r, g, b)
        return maximum >= 135 && maximum - minimum >= 30 && b >= g + 12 && r >= g + 8
    }
}
