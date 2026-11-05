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
    val desiredHeight = if (binderMode) 420.dp else 390.dp
    // Shell padding (16), the dock/status rail (56), and CRT/console gap (4).
    val heightBelowRail = (availableHeight - 76.dp).coerceAtLeast(0.dp)
    // Side controls need 192 dp plus the console's 16 dp vertical padding.
    // Portrait typing places two control rows below a full-width LCD instead;
    // leave at least 160 dp for that LCD before its own frame/padding.
    // Keep 160 dp for CRT content when possible, and never take its last 64 dp
    // just to enlarge the keyboard: the input preview must remain readable.
    // Very short viewports use the keyboard's scrolling fallback.
    val panelHeight = (heightBelowRail - 160.dp)
        .coerceAtLeast(if (controlsBelowLcd) 280.dp else 208.dp)
        .coerceAtMost(desiredHeight)
        .coerceAtMost((heightBelowRail - 64.dp).coerceAtLeast(0.dp))
    val controlHeight = ((panelHeight - 16.dp - if (controlsBelowLcd) 12.dp else 0.dp) /
        if (controlsBelowLcd) 2 else 4)
        .coerceIn(0.dp, 48.dp)
    return KeyboardPanelDimensions(panelHeight, 48.dp, controlHeight, controlsBelowLcd)
}
