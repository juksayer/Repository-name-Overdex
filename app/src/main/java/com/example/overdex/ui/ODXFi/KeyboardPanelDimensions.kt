package com.example.overdex.ui.ODXFi

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class KeyboardPanelDimensions(
    val panelHeight: Dp,
    val controlWidth: Dp,
    val controlHeight: Dp,
    val controlsBelowLcd: Boolean = false,
)

/** Sizes the existing console from its actual viewport, without changing browsing geometry. */
internal fun keyboardPanelDimensions(
    availableHeight: Dp,
    keyboardVisible: Boolean,
    binderMode: Boolean,
): KeyboardPanelDimensions {
    if (!keyboardVisible) return KeyboardPanelDimensions(170.dp, 56.dp, 36.dp)

    val controlsBelowLcd = !binderMode
    val desiredHeight = if (binderMode) 420.dp else 430.dp
    // Shell padding (16), the dock/status rail (56), and CRT/console gap (4).
    val heightBelowRail = (availableHeight - 76.dp).coerceAtLeast(0.dp)
    // Side controls need 192 dp plus the console's 16 dp vertical padding.
    // Portrait typing keeps a full-width LCD above a three-row directional
    // pad and a separate action-key cluster. Small viewports scroll the key grid.
    // Keep 160 dp for CRT content when possible, and never take its last 64 dp
    // just to enlarge the keyboard: the input preview must remain readable.
    // Very short viewports use the keyboard's scrolling fallback.
    val panelHeight = (heightBelowRail - 160.dp)
        .coerceAtLeast(if (controlsBelowLcd) 280.dp else 208.dp)
        .coerceAtMost(desiredHeight)
        .coerceAtMost((heightBelowRail - 64.dp).coerceAtLeast(0.dp))
    val controlHeight = if (controlsBelowLcd) {
        // Split exceptionally short viewports rather than reserving so much
        // for the LCD that the control crosses disappear entirely.
        val available = (panelHeight - 24.dp).coerceAtLeast(0.dp)
        val lcdReserve = minOf(112.dp, available / 2)
        ((available - lcdReserve) / 3).coerceIn(0.dp, 48.dp)
    } else {
        ((panelHeight - 16.dp) / 4).coerceIn(0.dp, 48.dp)
    }
    return KeyboardPanelDimensions(panelHeight, 48.dp, controlHeight, controlsBelowLcd)
}
