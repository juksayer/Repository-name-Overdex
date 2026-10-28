package com.example.overdex.ui.ODXFi

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.overdex.model.observation.InstrumentDeploymentState

/** Default shell control: its label and action travel together across module navigation. */
data class DroidballSessionControl(
    val deploymentState: InstrumentDeploymentState,
    val toggle: () -> Unit,
)

val LocalDroidballSessionControl = staticCompositionLocalOf<DroidballSessionControl?> { null }
