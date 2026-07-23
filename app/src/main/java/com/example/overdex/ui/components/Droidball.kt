package com.example.overdex.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.overdex.presentation.*

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
) {
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
            modifier = Modifier.size(54.dp),
            isInteractive = false
        )
        
        if (presentationState.instrument != InstrumentLifecycle.IDLE) {
            BreathingLED(
                color = ledColor,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center).size(8.dp)
            )
        }
    }
}
