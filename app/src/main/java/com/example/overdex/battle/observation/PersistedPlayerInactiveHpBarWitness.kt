package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.PlayerInactiveHpBarMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Measures one player-inactive HP bar from verified PlayerInactiveHpBarCrop artifacts. */
class PersistedPlayerInactiveHpBarWitness(
    private val artifactStore: FileCropArtifactStore,
    private val slot: Int,
    private val crop: BattleCropContract,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null

    init { require(slot in 0..1) { "Player inactive HP slot must be 0 or 1." } }

    override fun start(match: Match) {
        if (scope != null) return
        val sourceId = SourceId(observerId.id)
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { witnessScope ->
            witnessScope.launch {
                match.articles.collect { article ->
                    val captured = article.payload as? CropCaptured ?: return@collect
                    if (captured.cropProvenance.cropName != crop.cropName) return@collect
                    val bitmap = artifactStore.loadVerifiedPng(captured.artifact) ?: return@collect
                    try {
                        val fill = PlayerInactiveHpBarMeasurer.measure(bitmap) ?: return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = PlayerInactiveHpBarMeasured(slot, fill),
                            timestamp = article.perceivedAt,
                            confidence = null,
                            evidenceReferences = listOf(article.id.value),
                            monotonicTimeNanos = article.monotonicTimeNanos ?: return@collect
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    override fun stop() { scope?.cancel("Witness stopped"); scope = null }

    companion object {
        fun upper(artifactStore: FileCropArtifactStore) = PersistedPlayerInactiveHpBarWitness(
            artifactStore, 0, BattleCropContracts.playerInactiveUpperHpBar,
            ObserverId("PLAYER_INACTIVE_UPPER_HP_BAR_WITNESS", ObserverSource.SCREEN_CAPTURE),
            "Player Inactive Upper HP Bar Witness"
        )

        fun lower(artifactStore: FileCropArtifactStore) = PersistedPlayerInactiveHpBarWitness(
            artifactStore, 1, BattleCropContracts.playerInactiveLowerHpBar,
            ObserverId("PLAYER_INACTIVE_LOWER_HP_BAR_WITNESS", ObserverSource.SCREEN_CAPTURE),
            "Player Inactive Lower HP Bar Witness"
        )
    }
}

/**
 * The two 160×160 reference cards have HP bands at y=138–150 and y=340–352
 * in the 160×380 Player Inactive Pokémon Region. Measurements scale those
 * established coordinates to each preserved crop's actual dimensions.
 */
internal object PlayerInactiveHpBarMeasurer {
    private const val REFERENCE_WIDTH = 160f
    private const val REFERENCE_HEIGHT = 190f
    private const val BAR_LEFT = 17f
    private const val BAR_RIGHT = 144f
    private const val BAR_TOP = 138f
    private const val BAR_BOTTOM_OFFSET = 12f

    fun measure(bitmap: Bitmap): Float? {
        return measure(bitmap.width, bitmap.height, bitmap::getPixel)
    }

    internal fun measure(
        width: Int,
        height: Int,
        pixelAt: (x: Int, y: Int) -> Int
    ): Float? {
        if (width < 16 || height < 38) return null
        val left = (BAR_LEFT / REFERENCE_WIDTH * width).toInt().coerceAtLeast(0)
        val right = (BAR_RIGHT / REFERENCE_WIDTH * width).toInt().coerceAtMost(width)
        val top = (BAR_TOP / REFERENCE_HEIGHT * height).toInt().coerceAtLeast(0)
        val bottom = ((BAR_TOP + BAR_BOTTOM_OFFSET) / REFERENCE_HEIGHT * height).toInt().coerceAtMost(height)
        if (right - left < 8 || bottom - top < 3) return null

        val columns = (left until right).map { x ->
            val filledPixels = (top until bottom).count { y -> isHpFillPixel(pixelAt(x, y)) }
            filledPixels * 2 >= bottom - top
        }
        val filledColumns = columns.takeWhile { it }.count()
        return (filledColumns.toFloat() / columns.size).takeIf { it >= 0.02f }
    }

    private fun isHpFillPixel(color: Int): Boolean {
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        return maximum >= 100 && maximum - minimum >= 45
    }
}
