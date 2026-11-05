package com.example.overdex.ui.ODXFi

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class KeyboardPanelDimensions(
    val panelHeight: Dp,
    val controlWidth: Dp,
    val controlHeight: Dp,
)

/**
 * The console keeps its original physical proportions in every input mode.
 * Viewport size, keyboard visibility, and binder rotation change its content,
 * not the fixed LCD frame or its two instrument-button columns.
 */
@Suppress("UNUSED_PARAMETER")
internal fun keyboardPanelDimensions(
    availableHeight: Dp,
    keyboardVisible: Boolean,
    binderMode: Boolean,
): KeyboardPanelDimensions = KeyboardPanelDimensions(170.dp, 56.dp, 36.dp)
