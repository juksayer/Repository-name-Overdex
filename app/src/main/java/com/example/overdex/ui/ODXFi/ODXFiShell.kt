package com.example.overdex.ui.ODXFi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.presentation.InstrumentLifecycle
import com.example.overdex.presentation.ObservationIndicator
import com.example.overdex.presentation.PresentationState
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.BreathingLED
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.OverlayState
import com.example.overdex.ui.theme.TerminalGreen


@Composable
fun InstrumentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    color: Color = Color.DarkGray,
    labelColor: Color = Color.White.copy(alpha = 0.6f),
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 36.dp)
            .background(color)
            .drawBehind {
                // Precision chamfers and depth
                val strokeWidth = 1.dp.toPx()
                // Top Highlight
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = strokeWidth
                )
                // Right Shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.5f),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth
                )
                // Bottom Shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.8f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth * 2
                )
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(20.dp)
            )
        } else if (label != null) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Droidball: The physical embodiment of the ODX-Fi
 *
 * This component visualizes the observation state by mapping [PresentationState]
 * to its physical representation.
 */
@Composable
fun Droidball(
    presentationState: PresentationState,
    modifier: Modifier = Modifier
){
    // Presentation Layer: Mapping semantic indicators to physical colors
    val ledColor = when (presentationState.observation.status) {
        ObservationIndicator.SEARCHING -> Color(0xFFFFB300) // Amber
        ObservationIndicator.CONFIRMED -> Color(0xFF00E676) // Green
        ObservationIndicator.ALIGNING -> Color(0xFFD1C4E9) // Purple
        ObservationIndicator.SYNCING -> Color(0xFF2979FF)  // Blue
        ObservationIndicator.ERROR -> Color(0xFFFF1744)    // Red
        ObservationIndicator.OFF -> Color.Transparent
    }

    Box(modifier = modifier.size(40.dp)) {
        Droidball(
            presentationState = presentationState,
            modifier = Modifier.size(54.dp)
        )

        if (presentationState.instrument != InstrumentLifecycle.IDLE) {
            BreathingLED(
                color = ledColor,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center).size(8.dp)
            )
        }
    }
}

@Composable
fun InstrumentLCD(
    deploymentState: InstrumentDeploymentState = InstrumentDeploymentState.IDLE,
    frameCount: Long = 0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        // LCD Surface
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121510)) // Dim greenish-black LCD
                .border(1.dp, Color.Black, RoundedCornerShape(1.dp))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (deploymentState) {
                    InstrumentDeploymentState.IDLE -> "READY"
                    InstrumentDeploymentState.REQUESTING_PERMISSIONS -> "PERMISSIONS"
                    InstrumentDeploymentState.READY -> "PRIMED"
                    InstrumentDeploymentState.DEPLOYING -> "DEPLOYING"
                    InstrumentDeploymentState.OBSERVING -> "OBSERVING"
                    InstrumentDeploymentState.RETURNING -> "RETURNING"
                },
                color = TerminalGreen.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "OBSERVATION ENGINE",
                color = TerminalGreen.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = if (deploymentState == InstrumentDeploymentState.OBSERVING) "ACTIVE ($frameCount)" else "OFFLINE",
                color = if (deploymentState == InstrumentDeploymentState.OBSERVING) TerminalGreen else TerminalGreen.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ODXFiShell(
    onUp: () -> Unit = {},
    onDown: () -> Unit = {},
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    onA: () -> Unit = {},
    onB: () -> Unit = {},
    filterSettings: FilterSettings = FilterSettings(),
    onFilterSettingsChange: (FilterSettings) -> Unit = {},
    onSelect: () -> Unit = {},
    onStart: () -> Unit = {},
    onLaunchProbe: () -> Unit = {},
    onLaunchObservatory: () -> Unit = {},
    deploymentState: InstrumentDeploymentState = InstrumentDeploymentState.IDLE,
    frameCount: Long = 0,

    showBattleOverlay: Boolean = true,
    viewModel: PokedexViewModel? = null,
    instrumentState: ObservationSessionState? = null,
    pipelineStatus: com.example.overdex.data.observation.PipelineStatus? = null,
    isLogoInteractive: Boolean = false,
    content: @Composable (com.example.overdex.BattleMemory) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showResearcherSettings by remember { mutableStateOf(false) }
    var overlayState by remember { mutableStateOf(OverlayState.EXPANDED) }

    // Overlay Input Registration
    var settingsUp by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settingsDown by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settingsLeft by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settingsRight by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settingsA by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settingsB by remember { mutableStateOf<(() -> Unit)?>(null) }

    var researcherUp by remember { mutableStateOf<(() -> Unit)?>(null) }
    var researcherDown by remember { mutableStateOf<(() -> Unit)?>(null) }
    var researcherA by remember { mutableStateOf<(() -> Unit)?>(null) }
    var researcherB by remember { mutableStateOf<(() -> Unit)?>(null) }


    val currentState = instrumentState ?: ObservationSessionState.IDLE
    val currentDeploymentState = deploymentState
    val currentFrameCount = frameCount

    val serviceMode = currentState == ObservationSessionState.SERVICE_ACTIVE


    @Composable
    fun DPad(
        onUp: () -> Unit,
        onDown: () -> Unit,
        onLeft: () -> Unit,
        onRight: () -> Unit,
        modifier: Modifier = Modifier,
        centerContent: @Composable (() -> Unit)? = null
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Up Button
            IconButton(onClick = onUp) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Up")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Left Button
                IconButton(onClick = onLeft) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Left")
                }

                // Center element (optional)
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    centerContent?.invoke()
                }

                // Right Button
                IconButton(onClick = onRight) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Right")
                }
            }

            // Down Button
            IconButton(onClick = onDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Down")
            }
        }
    }
}