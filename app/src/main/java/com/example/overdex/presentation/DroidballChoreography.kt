package com.example.overdex.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.sin

/** One pose for the one body. Positions are measured in body diameters. */
data class DroidballPose(
    val xBodies: Float = 0f,
    val rollDegrees: Float = 0f,
    val focus: Float = 0f,
    val blink: Float = 0f,
    val detached: Float = 0f,
    val press: Float = 0f,
    val shadow: Float = 0f,
    val scan: Float = 0f,
    val sadness: Float = 0f,
)

/** Clock-driven presentation; neither capture nor navigation depends on a pose finishing. */
object DroidballChoreography {
    const val DEPARTURE_MILLIS = 1600L
    const val ARRIVAL_MILLIS = 2100L
    const val EXIT_MILLIS = 550L
    const val DOCKING_MILLIS = 1050L

    fun durationMillis(phase: DroidballJourneyPhase): Long = when (phase) {
        DroidballJourneyPhase.DEPARTING -> DEPARTURE_MILLIS
        DroidballJourneyPhase.ARRIVING -> ARRIVAL_MILLIS
        DroidballJourneyPhase.RETURNING -> EXIT_MILLIS
        DroidballJourneyPhase.DOCKING -> DOCKING_MILLIS
        else -> 0L
    }

    fun pose(phase: DroidballJourneyPhase, elapsedMillis: Long): DroidballPose {
        val duration = durationMillis(phase)
        val t = if (duration == 0L) 1f else
            (elapsedMillis.coerceIn(0L, duration).toFloat() / duration)
        return when (phase) {
            DroidballJourneyPhase.DOCKED -> DroidballPose()
            DroidballJourneyPhase.TRANSIT -> DroidballPose(detached = 1f)
            DroidballJourneyPhase.FIELD -> DroidballPose(detached = 1f, shadow = 1f)
            DroidballJourneyPhase.DEPARTING -> {
                val roll = ease(t, .57f, 1f)
                DroidballPose(
                    xBodies = -2.2f * roll,
                    rollDegrees = -396f * roll,
                    focus = ease(t, 0f, .12f),
                    blink = maxOf(pulse(t, .17f, .055f), pulse(t, .29f, .055f)),
                    detached = ease(t, .37f, .65f),
                    press = pulse(t, .43f, .13f),
                    shadow = ease(t, .28f, .45f),
                )
            }
            DroidballJourneyPhase.ARRIVING -> {
                // Arrive fast, skid two body lengths into the field, inspect, then tuck right.
                val entry = 1.15f - 3.15f * easeOut(t, 0f, .39f)
                val skid = .13f * pulse(t, .43f, .075f)
                val dock = ease(t, .85f, 1f)
                val x = (entry - skid) * (1f - dock)
                DroidballPose(
                    xBodies = x,
                    rollDegrees = if (t < .50f) -360f * easeOut(t, 0f, .43f) else 0f,
                    focus = 1f - ease(t, .52f, .66f),
                    blink = maxOf(pulse(t, .49f, .035f), pulse(t, .58f, .035f)),
                    detached = 1f,
                    shadow = 1f,
                    scan = pulse(t, .745f, .105f),
                )
            }
            DroidballJourneyPhase.RETURNING -> {
                val roll = ease(t, .12f, 1f)
                DroidballPose(
                    xBodies = 2.2f * roll,
                    rollDegrees = 396f * roll,
                    detached = 1f,
                    shadow = 1f,
                    sadness = ease(t, 0f, .22f),
                )
            }
            DroidballJourneyPhase.DOCKING -> {
                val entry = easeOut(t, 0f, .55f)
                DroidballPose(
                    xBodies = -1.8f * (1f - entry),
                    rollDegrees = -360f * (1f - entry),
                    detached = 1f - ease(t, .60f, .93f),
                    press = pulse(t, .76f, .17f),
                    shadow = 1f - ease(t, .72f, 1f),
                    sadness = 1f - ease(t, .85f, 1f),
                    blink = pulse(t, .82f, .07f),
                )
            }
        }
    }

    private fun fraction(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
    private fun ease(t: Float, from: Float, to: Float): Float {
        val p = fraction(t, from, to)
        return p * p * (3f - 2f * p)
    }
    private fun easeOut(t: Float, from: Float, to: Float): Float {
        val p = 1f - fraction(t, from, to)
        return 1f - p * p * p
    }
    private fun pulse(t: Float, center: Float, halfWidth: Float): Float {
        val p = (t - center) / halfWidth
        if (p <= -1f || p >= 1f) return 0f
        return (sin((p + 1f) * PI / 2.0)).toFloat().coerceIn(0f, 1f)
    }
}

/** Frame ticks rather than Animator duration scaling keep the journey visible with animations off. */
@Composable
fun rememberDroidballPose(state: DroidballJourneyState): DroidballPose {
    val duration = DroidballChoreography.durationMillis(state.phase)
    val current = remember(state.phase, state.generation, state.startedAtNanos) {
        val elapsed = if (duration == 0L) 0L else
            (System.nanoTime() - state.startedAtNanos).coerceAtLeast(0) / 1_000_000L
        mutableStateOf(DroidballChoreography.pose(state.phase, elapsed))
    }
    LaunchedEffect(state.phase, state.generation, state.startedAtNanos) {
        if (duration == 0L) {
            current.value = DroidballChoreography.pose(state.phase, 0)
            return@LaunchedEffect
        }
        var elapsed: Long
        do {
            // withFrameNanos paces drawing; the journey and this elapsed time share nanoTime.
            withFrameNanos { }
            elapsed = ((System.nanoTime() - state.startedAtNanos).coerceAtLeast(0) / 1_000_000L)
            current.value = DroidballChoreography.pose(state.phase, elapsed)
        } while (elapsed < duration)
    }
    return current.value
}
