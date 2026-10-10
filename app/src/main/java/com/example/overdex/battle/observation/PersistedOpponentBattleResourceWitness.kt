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
                var candidateReference: String? = null
                var delivered: Int? = null
                match.articles
                    .filter { (it.payload as? CropCaptured)?.cropProvenance?.cropName == cropName }
                    .collect { article ->
                        val captured = article.payload as CropCaptured
                        val bitmap = artifactStore.loadVerifiedPng(captured.artifact, captured.cropProvenance)
                            ?: return@collect
                        val count = try {
                            if (resource == OpponentBattleResource.POKE_BALLS) OpponentBattleResourceCounter.pokeBallCount(bitmap)
                            else OpponentBattleResourceCounter.count(bitmap, maximumCount, acceptsPixel)
                        } finally {
                            bitmap.recycle()
                        }
                        if (count == null) { candidate = null; candidateReference = null; return@collect }
                        if (candidate != count) {
                            candidate = count
                            candidateReference = article.id.value
                            return@collect
                        }
                        // Repeated ball snapshots also prove that an intervening
                        // species change was a switch, rather than a faint.
                        if (resource != OpponentBattleResource.POKE_BALLS && delivered == count) return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = OpponentBattleResourceCountMeasured(resource, count, maximumCount),
                            timestamp = article.perceivedAt,
                            confidence = null,
                            evidenceReferences = listOfNotNull(candidateReference, article.id.value).distinct(),
                            monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                        )
                        delivered = count
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
                var candidateReference: String? = null
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
                            OpponentBattleResourceCounter.pokeBallCount(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                        if (count == null) { candidate = null; candidateReference = null; return@collect }
                        if (candidate != count) {
                            candidate = count
                            candidateReference = article.id.value
                            return@collect
                        }
                        if (delivered == count) return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = PlayerPokeBallCountMeasured(count),
                            timestamp = article.perceivedAt,
                            confidence = null,
                            evidenceReferences = listOfNotNull(candidateReference, article.id.value).distinct(),
                            monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                        )
                        delivered = count
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
    fun pokeBallCount(bitmap: Bitmap): Int? = pokeBallCount(bitmap.width, bitmap.height, bitmap::getPixel)

    /** Missing/covered badge pixels are unavailable evidence, never zero survivors. */
    internal fun pokeBallCount(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Int? {
        if (width < 3 || height < 3) return null
        var badgePixels = 0
        val outlinePixels = IntArray(3)
        for (y in 0 until height) for (x in 0 until width) {
            val pixel = pixelAt(x, y)
            val r = pixel ushr 16 and 255
            val g = pixel ushr 8 and 255
            val b = pixel and 255
            val high = maxOf(r, g, b)
            val low = minOf(r, g, b)
            if (low >= 180 && high - low <= 50) badgePixels++
            if (high <= 180 && high - low <= 45) outlinePixels[(x * 3 / width).coerceAtMost(2)]++
        }
        // Each of the three fixed slots retains an outline even when its ball
        // darkens. Requiring the badge and slots rejects black masks and sky.
        if (badgePixels < width * height / 4 || outlinePixels.any { it < maxOf(2, width * height / 400) }) return null
        return count(width, height, pixelAt, 3, ::isBrightPokeBallRed)
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
