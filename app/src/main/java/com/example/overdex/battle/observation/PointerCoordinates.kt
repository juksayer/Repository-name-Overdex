package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion

/** A visible, single-finger DOWN reading. dX/dY after release are never coordinates. */
data class PointerCoordinates(val x: Float, val y: Float)

object PointerCoordinateParser {
    private val pointers = Regex("(?i)(?<![a-z])P\\s*:\\s*([0-9]+)\\s*/\\s*([0-9]+)")
    // A vertical pointer trace over the colon read as `Y}` in a real Moondrop
    // frame. Permit only this field-separator repair, never invent numeric digits.
    private val x = Regex("(?i)(?<![a-z])X\\s*[:};]\\s*([0-9]+(?:\\.[0-9]+)?)(?![a-z0-9.])")
    private val y = Regex("(?i)(?<![a-z])Y\\s*[:};]\\s*([0-9]+(?:\\.[0-9]+)?)(?![a-z0-9.])")

    fun parse(text: String, displayWidth: Int, displayHeight: Int): PointerCoordinates? {
        if (displayWidth <= 0 || displayHeight <= 0) return null
        val p = pointers.findAll(text).toList().singleOrNull() ?: return null
        if (p.groupValues[1] != "1" || p.groupValues[2] != "1") return null
        // Do not repair arbitrary digits: a wrong coordinate can select the other move.
        if (Regex("(?i)\\bd[xy]\\s*:").containsMatchIn(text)) return null
        val px = x.findAll(text).toList().singleOrNull()?.groupValues?.get(1)?.toFloatOrNull() ?: return null
        val py = y.findAll(text).toList().singleOrNull()?.groupValues?.get(1)?.toFloatOrNull() ?: return null
        if (!px.isFinite() || !py.isFinite() || px < 0 || py < 0 || px >= displayWidth || py >= displayHeight) return null
        return PointerCoordinates(px, py)
    }

    /** These bounds are in full-display fractions, with no notification-bar subtraction. */
    fun chargedButtonSlot(point: PointerCoordinates, displayWidth: Int, displayHeight: Int, controls: AnchorRegion,
        singleCentered: Boolean = false): Int? {
        if (displayWidth <= 0 || displayHeight <= 0 || controls.width <= 0 || controls.height <= 0) return null
        val nx = point.x / displayWidth
        val ny = point.y / displayHeight
        if (nx !in controls.x..(controls.x + controls.width) || ny !in controls.y..(controls.y + controls.height)) return null
        val horizontal = (nx - controls.x) / controls.width
        if (singleCentered) return 1.takeIf { horizontal in 0.30f..0.70f }
        // Leave a gap between the controls; do not guess when a touch is between them.
        return when {
            horizontal in 0.04f..0.44f -> 1
            horizontal in 0.56f..0.96f -> 2
            else -> null
        }
    }
}
