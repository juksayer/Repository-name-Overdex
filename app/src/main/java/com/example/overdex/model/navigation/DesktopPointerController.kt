package com.example.overdex.model.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Relative LCD input and CRT hit testing only. The caller owns selection and launching.
 * Target bounds use pixels relative to the measured CRT content viewport.
 */
@Stable
class DesktopPointerController {
    private var enabledState by mutableStateOf(false)
    val enabled: Boolean get() = enabledState
    var x by mutableStateOf(0.5f)
        private set
    var y by mutableStateOf(0.5f)
        private set
    var viewportWidth by mutableStateOf(0f)
        private set
    var viewportHeight by mutableStateOf(0f)
        private set

    /** Only actual relative movement increments this, never layout or arrow alignment. */
    var motionRevision by mutableStateOf(0L)
        private set

    val pixelX: Float get() = x * viewportWidth
    val pixelY: Float get() = y * viewportHeight

    private var surfaceKey: Any? = null
    private val targets = mutableStateMapOf<String, TargetBounds>()
    private var registrationOrder = 0L
    private var centerAnchor: String? = null

    val hoveredTargetId: String?
        get() {
            if (!enabled || !hasViewport) return null
            return targets.entries.asSequence()
                .filter { (_, bounds) -> bounds.visible()?.contains(pixelX, pixelY) == true }
                .maxByOrNull { it.value.order }?.key
        }

    fun setEnabled(value: Boolean) { enabledState = value }
    fun toggle(): Boolean { setEnabled(!enabled); return enabled }

    /** A different program/menu must never inherit the previous screen's click targets. */
    fun beginSurface(key: Any) {
        if (surfaceKey == key) return
        surfaceKey = key
        clearTargets()
    }

    /** Preserve the normalized pointer, but require fresh measurements after a resize. */
    fun setViewport(widthPx: Float, heightPx: Float) {
        val valid = widthPx.isFinite() && heightPx.isFinite() && widthPx > 0f && heightPx > 0f
        val width = if (valid) widthPx else 0f
        val height = if (valid) heightPx else 0f
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        targets.clear()
    }

    /** Also used when the CRT launcher leaves composition; pointer mode and position remain. */
    fun clearTargets() {
        targets.clear()
        centerAnchor = null
    }

    fun setTarget(id: String, leftPx: Float, topPx: Float, rightPx: Float, bottomPx: Float): Boolean {
        if (id.isBlank() || !listOf(leftPx, topPx, rightPx, bottomPx).all { it.isFinite() } ||
            rightPx <= leftPx || bottomPx <= topPx) {
            targets.remove(id)
            return false
        }
        val previous = targets[id]
        if (previous == null || previous.left != leftPx || previous.top != topPx ||
            previous.right != rightPx || previous.bottom != bottomPx) {
            targets[id] = TargetBounds(leftPx, topPx, rightPx, bottomPx, ++registrationOrder)
        }
        if (centerAnchor == id) centerVisibleTarget(id)
        return true
    }

    fun removeTarget(id: String) { targets.remove(id) }

    /**
     * The LCD's full width/height maps to the CRT's full width/height. Lifting a
     * finger does nothing, so users may reposition their hand without a click.
     */
    fun moveBy(deltaXPx: Float, deltaYPx: Float, lcdWidthPx: Float, lcdHeightPx: Float): Boolean {
        if (!enabled || !hasViewport || !deltaXPx.isFinite() || !deltaYPx.isFinite() ||
            !lcdWidthPx.isFinite() || !lcdHeightPx.isFinite() || lcdWidthPx <= 0f || lcdHeightPx <= 0f) {
            return false
        }
        // A user's drag supersedes arrow alignment, including a pending scrolled row.
        centerAnchor = null
        val nextX = (x + deltaXPx / lcdWidthPx).coerceIn(0f, 1f)
        val nextY = (y + deltaYPx / lcdHeightPx).coerceIn(0f, 1f)
        if (nextX == x && nextY == y) return false
        x = nextX
        y = nextY
        motionRevision++
        return true
    }

    /** Return an identifier only; a tap never launches or changes a program itself. */
    fun tap(): String? = hoveredTargetId

    /**
     * Align with physical-button selection without simulating pointer movement.
     * A missing/offscreen row is centered once its visible layout is measured.
     * Keep following that row through a BringIntoView animation until the user
     * moves the pointer, changes surface, or the CRT releases its targets.
     */
    fun centerOnTarget(id: String): Boolean {
        centerAnchor = id.takeIf { it.isNotBlank() }
        return centerAnchor != null && centerVisibleTarget(id)
    }

    private val hasViewport: Boolean get() = viewportWidth > 0f && viewportHeight > 0f

    private fun centerVisibleTarget(id: String): Boolean {
        val visible = targets[id]?.visible() ?: return false
        x = (visible.left + (visible.right - visible.left) / 2f) / viewportWidth
        y = (visible.top + (visible.bottom - visible.top) / 2f) / viewportHeight
        return true
    }

    private fun TargetBounds.visible(): TargetBounds? {
        if (!hasViewport) return null
        val clipped = copy(
            left = left.coerceIn(0f, viewportWidth),
            top = top.coerceIn(0f, viewportHeight),
            right = right.coerceIn(0f, viewportWidth),
            bottom = bottom.coerceIn(0f, viewportHeight),
        )
        return clipped.takeIf { it.right > it.left && it.bottom > it.top }
    }

    private fun TargetBounds.contains(pointX: Float, pointY: Float): Boolean =
        pointX >= left && pointY >= top &&
            (pointX < right || pointX == viewportWidth && right == viewportWidth) &&
            (pointY < bottom || pointY == viewportHeight && bottom == viewportHeight)

    private data class TargetBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val order: Long,
    )
}
