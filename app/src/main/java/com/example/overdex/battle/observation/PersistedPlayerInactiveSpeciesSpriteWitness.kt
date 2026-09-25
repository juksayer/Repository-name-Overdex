package com.example.overdex.battle.observation

import android.graphics.Bitmap
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.PlayerInactiveSpeciesSpriteFingerprintMeasured
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.timeline.observer.ObserverId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.overdex.battle.timeline.observer.ObservationSource as ObserverSource

/** Measures one inactive sprite appearance without claiming a species identity. */
class PersistedPlayerInactiveSpeciesSpriteWitness(
    private val artifactStore: FileCropArtifactStore,
    private val slot: Int,
    private val crop: BattleCropContract,
    override val observerId: ObserverId,
    override val name: String
) : Observer {
    private var scope: CoroutineScope? = null

    init { require(slot in 0..1) { "Player inactive sprite slot must be 0 or 1." } }

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
                        val sample = PlayerInactiveSpriteFingerprinter.measure(bitmap) ?: return@collect
                        match.custody.submitTestimony(
                            sourceId = sourceId,
                            payload = PlayerInactiveSpeciesSpriteFingerprintMeasured(
                                slot, sample.fingerprint, sample.left, sample.top, sample.right, sample.bottom
                            ),
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
        fun upper(artifactStore: FileCropArtifactStore) = PersistedPlayerInactiveSpeciesSpriteWitness(
            artifactStore, 0, BattleCropContracts.playerInactiveUpperSpeciesSprite,
            ObserverId("PLAYER_INACTIVE_UPPER_SPECIES_SPRITE_WITNESS", ObserverSource.SCREEN_CAPTURE),
            "Player Inactive Upper Species Sprite Witness"
        )
        fun lower(artifactStore: FileCropArtifactStore) = PersistedPlayerInactiveSpeciesSpriteWitness(
            artifactStore, 1, BattleCropContracts.playerInactiveLowerSpeciesSprite,
            ObserverId("PLAYER_INACTIVE_LOWER_SPECIES_SPRITE_WITNESS", ObserverSource.SCREEN_CAPTURE),
            "Player Inactive Lower Species Sprite Witness"
        )
    }
}

internal data class SpriteFingerprintSample(
    val fingerprint: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Samples the sprite portion of each 160×160 inactive card, excluding the CP
 * label above and HP bar below. Its 8×8 luminance hash is a measurement for a
 * future appearance-reference matcher, never a species assertion on its own.
 */
internal object PlayerInactiveSpriteFingerprinter {
    private const val REFERENCE_WIDTH = 160f
    private const val REFERENCE_HEIGHT = 190f
    private const val LEFT = 18f
    private const val RIGHT = 144f
    private const val TOP = 28f
    private const val BOTTOM_OFFSET = 104f
    private const val GRID = 8

    fun measure(bitmap: Bitmap): SpriteFingerprintSample? =
        measure(bitmap.width, bitmap.height, bitmap::getPixel)

    internal fun measure(
        width: Int,
        height: Int,
        pixelAt: (x: Int, y: Int) -> Int
    ): SpriteFingerprintSample? {
        if (width < GRID || height < GRID) return null
        val left = (LEFT / REFERENCE_WIDTH * width).toInt().coerceAtLeast(0)
        val right = (RIGHT / REFERENCE_WIDTH * width).toInt().coerceAtMost(width)
        val slotTop = (TOP / REFERENCE_HEIGHT * height).toInt().coerceAtLeast(0)
        val bottom = ((TOP + BOTTOM_OFFSET) / REFERENCE_HEIGHT * height).toInt().coerceAtMost(height)
        if (right - left < GRID || bottom - slotTop < GRID) return null

        val samples = buildList(GRID * GRID) {
            for (row in 0 until GRID) for (column in 0 until GRID) {
                val x = left + ((column + 0.5f) * (right - left) / GRID).toInt().coerceAtMost(right - left - 1)
                val y = slotTop + ((row + 0.5f) * (bottom - slotTop) / GRID).toInt().coerceAtMost(bottom - slotTop - 1)
                add(luminance(pixelAt(x, y)))
            }
        }
        val mean = samples.average()
        var bits = 0UL
        samples.forEachIndexed { index, value -> if (value >= mean) bits = bits or (1UL shl index) }
        return SpriteFingerprintSample(bits.toString(16).padStart(16, '0'), left, slotTop, right, bottom)
    }

    private fun luminance(color: Int): Int {
        val red = color ushr 16 and 0xff
        val green = color ushr 8 and 0xff
        val blue = color and 0xff
        return (red * 54 + green * 183 + blue * 19) ushr 8
    }
}
