package com.example.overdex.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

/** A docked tap acknowledgement layered onto the same body used during travel. */
@Composable
fun DroidballAcknowledgingBody(
    modifier: Modifier = Modifier,
    pose: DroidballPose = DroidballPose(),
    interactive: Boolean = false,
    resetKey: Any? = null,
) {
    var tapCount by remember(interactive, resetKey) { mutableIntStateOf(0) }
    var acknowledgement by remember(interactive, resetKey) { mutableStateOf(DroidballPose()) }

    LaunchedEffect(interactive, resetKey, tapCount) {
        if (!interactive || tapCount == 0) return@LaunchedEffect
        val activeCount = tapCount
        val duration = DroidballAcknowledgementMotion.durationMillis(activeCount)
        var firstFrameNanos: Long? = null
        var elapsedMillis: Long
        do {
            val frameNanos = withFrameNanos { it }
            // A new tap can arrive before recomposition cancels this effect.
            if (tapCount != activeCount) return@LaunchedEffect
            val start = firstFrameNanos ?: frameNanos.also { firstFrameNanos = it }
            elapsedMillis = ((frameNanos - start).coerceAtLeast(0L) / 1_000_000L)
            acknowledgement = DroidballAcknowledgementMotion.pose(elapsedMillis, activeCount)
        } while (elapsedMillis < duration)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val interaction = if (interactive && tapCount < 5) {
        // His face and tilt provide the feedback; a rectangular ripple would
        // darken the surrounding shell and compete with the short blinks.
        Modifier.clickable(interactionSource = interactionSource, indication = null, role = Role.Button) {
            // Recheck in the callback as well as the modifier: a sixth tap may
            // already be queued before Compose removes the fifth tap's action.
            if (tapCount < 5) {
                val next = tapCount + 1
                acknowledgement = DroidballAcknowledgementMotion.pose(0L, next)
                tapCount = next
            }
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
