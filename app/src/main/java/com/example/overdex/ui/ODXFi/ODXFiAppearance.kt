package com.example.overdex.ui.ODXFi

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.overdex.data.ODXFiAppearance

/** Shared paint for every shell, with a local draft while the color picker is open. */
val LocalODXFiAppearance = staticCompositionLocalOf { ODXFiAppearance() }

/** Keep etched labels and button legends readable on either light or dark paint. */
internal fun odxFiLabelColor(background: Color): Color =
    if (background.luminance() > .179f) Color.Black else Color.White
