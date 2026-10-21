package com.example.overdex.battle.replay

/** Maps monotonic frame time to archive time, independent of rendering delays. */
internal class ReplayPlaybackClock(
    private val startNanos: Long,
    private val endNanos: Long,
    private val speed: Float,
) {
    private var firstFrameNanos: Long? = null

    init {
        require(endNanos >= startNanos)
        require(speed.isFinite() && speed in MIN_SPEED..MAX_SPEED)
    }

    fun positionAt(frameNanos: Long): Long {
        val origin = firstFrameNanos ?: frameNanos.also { firstFrameNanos = it }
        val elapsed = (frameNanos - origin).coerceAtLeast(0L)
        val advance = (elapsed.toDouble() * speed).toLong().coerceAtMost(endNanos - startNanos)
        return startNanos + advance
    }

    companion object {
        const val MIN_SPEED = 0.1f
        const val MAX_SPEED = 5f
    }
}
