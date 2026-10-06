package com.example.overdex.model.observation

/**
 * One short, vibration-like burst measured by the device linear-acceleration sensor.
 *
 * This is a transient transport object. The Witness turns it into immutable
 * testimony before any charge-move interpretation is attempted.
 */
data class CapturedDeviceMotionPulse(
    val capturedAtWallTimeMillis: Long,
    val startedAtMonotonicTimeNanos: Long,
    val durationNanos: Long,
    val peakLinearAccelerationMetersPerSecondSquared: Float,
    val rmsLinearAccelerationMetersPerSecondSquared: Float,
    val sampleCount: Int
)
