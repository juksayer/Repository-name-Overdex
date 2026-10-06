package com.example.overdex.battle.observation

import com.example.overdex.model.observation.CapturedDeviceMotionPulse
import kotlin.math.sqrt

/**
 * Extracts short vibration-like bursts from linear acceleration samples.
 *
 * The detector deliberately reports motion only. Whether a group of pulses was
 * caused by Pokémon GO's charged-move haptics is decided later from Timeline
 * evidence.
 */
class DeviceMotionPulseDetector(
    private val activationThreshold: Float = 0.55f,
    private val releaseThreshold: Float = 0.22f,
    private val quietGapNanos: Long = 45_000_000L,
    private val minimumDurationNanos: Long = 6_000_000L,
    private val maximumDurationNanos: Long = 350_000_000L,
    private val minimumSampleCount: Int = 3
) {
    private var startedAtNanos: Long? = null
    private var lastActiveAtNanos: Long = Long.MIN_VALUE
    private var peak = 0f
    private var sumSquares = 0.0
    private var sampleCount = 0

    fun accept(
        monotonicTimeNanos: Long,
        x: Float,
        y: Float,
        z: Float,
        wallTimeMillis: Long
    ): CapturedDeviceMotionPulse? {
        val magnitude = sqrt(x * x + y * y + z * z)
        val start = startedAtNanos
        if (start == null) {
            if (magnitude >= activationThreshold) begin(monotonicTimeNanos, magnitude)
            return null
        }

        peak = maxOf(peak, magnitude)
        sumSquares += magnitude.toDouble() * magnitude.toDouble()
        sampleCount++
        if (magnitude >= releaseThreshold) lastActiveAtNanos = monotonicTimeNanos

        val duration = monotonicTimeNanos - start
        val quiet = monotonicTimeNanos - lastActiveAtNanos >= quietGapNanos
        if (!quiet && duration < maximumDurationNanos) return null

        val pulse = if (
            duration >= minimumDurationNanos &&
            duration <= maximumDurationNanos + quietGapNanos &&
            sampleCount >= minimumSampleCount
        ) {
            CapturedDeviceMotionPulse(
                capturedAtWallTimeMillis = wallTimeMillis,
                startedAtMonotonicTimeNanos = start,
                durationNanos = duration.coerceAtMost(maximumDurationNanos),
                peakLinearAccelerationMetersPerSecondSquared = peak,
                rmsLinearAccelerationMetersPerSecondSquared =
                    sqrt(sumSquares / sampleCount).toFloat(),
                sampleCount = sampleCount
            )
        } else {
            null
        }
        reset()
        if (magnitude >= activationThreshold) begin(monotonicTimeNanos, magnitude)
        return pulse
    }

    fun reset() {
        startedAtNanos = null
        lastActiveAtNanos = Long.MIN_VALUE
        peak = 0f
        sumSquares = 0.0
        sampleCount = 0
    }

    private fun begin(monotonicTimeNanos: Long, magnitude: Float) {
        startedAtNanos = monotonicTimeNanos
        lastActiveAtNanos = monotonicTimeNanos
        peak = magnitude
        sumSquares = magnitude.toDouble() * magnitude.toDouble()
        sampleCount = 1
    }
}
