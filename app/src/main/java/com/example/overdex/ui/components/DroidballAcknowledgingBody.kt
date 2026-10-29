package com.example.overdex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.example.overdex.presentation.DroidballPose
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** A docked tap acknowledgement layered onto the same body used during travel. */
@Composable
fun DroidballAcknowledgingBody(
    modifier: Modifier = Modifier,
    pose: DroidballPose = DroidballPose(),
    interactive: Boolean = false,
    resetKey: Any? = null,
) {
    var trigger by remember(interactive, resetKey) { mutableIntStateOf(0) }
    var acknowledgement by remember(interactive, resetKey) { mutableStateOf(DroidballPose()) }

    LaunchedEffect(interactive, resetKey, trigger) {
        if (!interactive || trigger == 0) return@LaunchedEffect
        val activeTrigger = trigger
        var firstFrameNanos: Long? = null
        var elapsedMillis: Long
        do {
            val frameNanos = withFrameNanos { it }
            // A new tap can arrive before recomposition cancels this effect.
            if (trigger != activeTrigger) return@LaunchedEffect
            val start = firstFrameNanos ?: frameNanos.also { firstFrameNanos = it }
            elapsedMillis = ((frameNanos - start).coerceAtLeast(0L) / 1_000_000L)
            acknowledgement = DroidballAcknowledgementMotion.pose(elapsedMillis)
        } while (elapsedMillis < DroidballAcknowledgementMotion.DURATION_MILLIS)
    }

    val interaction = if (interactive) {
        Modifier.clickable(role = Role.Button) {
            acknowledgement = DroidballPose()
            trigger++
        }
    } else Modifier

    DroidballBody(
        modifier = modifier.then(interaction),
        pose = pose.copy(
            rollDegrees = pose.rollDegrees + acknowledgement.rollDegrees,
            blink = maxOf(pose.blink, acknowledgement.blink),
        ),
    )
}

/** Finite frame-clock motion; system Animator duration scaling does not skip the response. */
internal object DroidballAcknowledgementMotion {
    const val DURATION_MILLIS = 2320L

    fun pose(elapsedMillis: Long): DroidballPose {
        val elapsed = elapsedMillis.coerceAtLeast(0L)
        if (elapsed >= DURATION_MILLIS) return DroidballPose()
        val tilt = when {
            elapsed < 900L -> {
                // The original acknowledgement used a 15 degree spring with
                // damping ratio 0.6 and Compose's low stiffness of 200.
                val dampingRatio = .6
                val naturalFrequency = sqrt(200.0)
                val dampingFactor = sqrt(1.0 - dampingRatio * dampingRatio)
                val seconds = elapsed / 1000.0
                val angle = naturalFrequency * dampingFactor * seconds
                val envelope = exp(-dampingRatio * naturalFrequency * seconds)
                (15.0 * (1.0 - envelope *
                    (cos(angle) + dampingRatio / dampingFactor * sin(angle)))).toFloat()
            }
            elapsed < 1720L -> 15f
            else -> {
                val progress = (elapsed - 1720L) / 600f
                val eased = progress * progress * (3f - 2f * progress)
                15f * (1f - eased)
            }
        }
        val blink = if (elapsed in 1000L until 1060L || elapsed in 1160L until 1220L) 1f else 0f
        return DroidballPose(rollDegrees = tilt, blink = blink)
    }
}
