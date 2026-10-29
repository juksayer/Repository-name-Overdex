package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import kotlin.math.abs
import kotlin.math.max

/**
 * Short-lived visual layout shared by fill, label and pointer witnesses. A saved
 * moveset cannot establish where a control was visible. Two matching center rims
 * with neither side rim must precede the single-control fallback.
 */
internal class PlayerChargeControlLayout {
    private data class Sample(val at: Long, val width: Int, val height: Int,
        val center: ChargeButtonFillHeightReader.Reading)
    private var candidate: Sample? = null
    private var confirmed: Sample? = null
    private var lastAt: Long? = null

    @Synchronized fun reset() {
        candidate = null
        confirmed = null
        lastAt = null
    }

    @Synchronized fun observe(at: Long, width: Int, height: Int,
        left: ChargeButtonFillHeightReader.Reading?, right: ChargeButtonFillHeightReader.Reading?,
        center: ChargeButtonFillHeightReader.Reading?) {
        if (lastAt?.let { at <= it } == true) return
        lastAt = at
        if (left != null || right != null || center == null) {
            candidate = null
            confirmed = null
            return
        }
        val current = Sample(at, width, height, center)
        val previous = candidate
        val tolerance = max(3, width / 100)
        val sameRim = previous != null && previous.width == width && previous.height == height &&
            abs(previous.center.centerX - center.centerX) <= tolerance &&
            abs(previous.center.centerY - center.centerY) <= tolerance &&
            abs(previous.center.radius - center.radius) <= tolerance &&
            at - previous.at in 100_000_000L..MAX_AGE_NANOS
        confirmed = current.takeIf { sameRim }
        candidate = current
    }

    @Synchronized fun centerAt(at: Long, width: Int? = null, height: Int? = null): ChargeButtonFillHeightReader.Reading? =
        confirmed?.takeIf { at - it.at in 0..MAX_AGE_NANOS &&
            (width == null || it.width == width) && (height == null || it.height == height) }?.center

    companion object {
        private const val MAX_AGE_NANOS = 1_250_000_000L

        fun textCrop(slot: Int, effectiveness: Boolean, singleCentered: Boolean): BattleCropContract? = when {
            slot == 0 -> BattleCropContracts.pointerCoordinates
            singleCentered && slot == 2 -> null
            singleCentered -> if (effectiveness) BattleCropContracts.playerChargeMoveEffectivenessCenter
                else BattleCropContracts.playerChargeMoveNameCenter
            slot == 1 -> if (effectiveness) BattleCropContracts.playerChargeMoveEffectivenessLeft
                else BattleCropContracts.playerChargeMoveNameLeft
            else -> if (effectiveness) BattleCropContracts.playerChargeMoveEffectivenessRight
                else BattleCropContracts.playerChargeMoveNameRight
        }

        /** Runtime crop only. Saved left/right calibration remains untouched. */
        fun centeredRegion(left: AnchorRegion, right: AnchorRegion): AnchorRegion {
            val width = max(left.width, right.width).coerceIn(0f, 1f)
            val centerX = (left.x + left.width / 2f + right.x + right.width / 2f) / 2f
            val top = minOf(left.y, right.y).coerceIn(0f, 1f)
            val bottom = max(left.y + left.height, right.y + right.height).coerceIn(top, 1f)
            return AnchorRegion((centerX - width / 2f).coerceIn(0f, 1f - width), top, width, bottom - top)
        }
    }
}
