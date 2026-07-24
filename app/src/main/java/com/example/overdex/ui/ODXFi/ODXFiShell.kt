package com.example.overdex.ui.ODXFi

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.ResearcherManager
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.presentation.InstrumentLifecycle
import com.example.overdex.presentation.ObservationIndicator
import com.example.overdex.presentation.PresentationState
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.BreathingLED
import com.example.overdex.ui.components.Droidball
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.OverlayState
import com.example.overdex.ui.components.StatusIndicator
import com.example.overdex.ui.theme.PokedexGreen
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Duration.Companion.milliseconds


/**
 * Glass Shield: Restricts all touch input to the CRT area.
 * The CRT is an observation-only display and never accepts operator input.
 */
fun Modifier.glassShield(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        }
    }
}

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
fun StatusIndicator(
    label: String,
    color: Color,
    cycleDurationMillis: Int = 4000
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))
        BreathingLED(color = color, cycleDurationMillis = cycleDurationMillis)
    }
}

@Composable
fun BreathingLED(
    color: Color,
    modifier: Modifier = Modifier,
    cycleDurationMillis: Int = 4000
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing_led")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(cycleDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "led_progress"
    )

    // Smooth sinusoidal curve (0.3 to 1.0 range) for natural physics.
    val alpha = 0.3f + 0.7f * ((1f - cos(progress * 2 * PI).toFloat()) / 2f)

    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(12.dp)
            .drawWithContent {
                // Restrained bloom - supports the illusion of an illuminated lens.
                drawCircle(
                    color = color.copy(alpha = alpha * 0.1f),
                    radius = size.minDimension / 2 * 1.2f,
                    center = center
                )
                // LED Surface
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = size.minDimension / 2,
                    center = center
                )
                // Hardware bezel
                drawCircle(
                    color = Color.Black,
                    radius = size.minDimension / 2,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    )
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

    onStart: () -> Unit = {},


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

    // Permanent Front Panel doesn't use rail animations
    val crtPadding by animateDpAsState(
        targetValue = if (serviceMode) 0.dp else 32.dp,
        label = "crtPadding"
    )

    val context = LocalContext.current
    val researcherManager = remember { ResearcherManager(context) }
    var isResearcherUnlocked by remember { mutableStateOf(researcherManager.isUnlocked()) }

    // Konami Code Detection
    val konamiCode = remember { listOf("UP", "UP", "DOWN", "DOWN", "LEFT", "RIGHT", "LEFT", "RIGHT", "B", "A") }
    var currentSequence by remember { mutableStateOf(emptyList<String>()) }
    var unlockMessage by remember { mutableStateOf<String?>(null) }

    // BattleMemory - Restore local lifecycle
    val battleMemory = remember { com.example.overdex.BattleMemory() }
    var currentDecision by remember { mutableStateOf<com.example.overdex.model.DecisionAnalysis?>(null) }

    val handleInput = { input: String ->
        val nextSequence = currentSequence + input
        if (konamiCode.take(nextSequence.size) == nextSequence) {
            currentSequence = nextSequence
            if (currentSequence.size == konamiCode.size) {
                researcherManager.setUnlocked(true)
                isResearcherUnlocked = true
                showSettings = true
                unlockMessage = "ACCESS LEVEL UPDATED\nRESEARCHER MODE ENABLED"
                currentSequence = emptyList()
            }
        } else {
            // Reset if sequence broken, but allow starting new sequence with 'UP'
            currentSequence = if (input == "UP") listOf("UP") else emptyList()
        }
    }

    LaunchedEffect(unlockMessage) {
        if (unlockMessage != null) {
            delay(30.milliseconds)
            unlockMessage = null
        }
    }

    // Simulation is now managed by the ViewModel/Service lifecycle
    LaunchedEffect(serviceMode) {
        if (serviceMode) {
            battleMemory.runPrototypeSimulation()
        }
    }

    // Matchup Intelligence Foundation Verification
    var currentMatchup by remember { mutableStateOf<com.example.overdex.model.MatchupAnalysis?>(null) }

    val presentationState = remember(currentState, pipelineStatus, battleMemory, currentMatchup, currentDecision) {
        com.example.overdex.presentation.PresentationMapper.map(
            instrumentState = currentState,
            pipelineStatus = pipelineStatus,
            battleMemory = battleMemory,
            matchup = currentMatchup,
            decision = currentDecision
        )
    }

    LaunchedEffect(battleMemory.enemyTeam.find { it.isActive }) {
        val activeEnemy = battleMemory.enemyTeam.find { it.isActive }
        if (viewModel != null && activeEnemy != null) {
            val enemyData = viewModel.getPokemonByName(activeEnemy.species)
            val playerData = viewModel.getPokemonByName(battleMemory.playerActivePokemon ?: "Charizard")

            if (enemyData != null && playerData != null) {
                val matchupAnalysis = com.example.overdex.data.matchup.MatchupEngine.analyze(
                    player = playerData,
                    enemy = enemyData,
                    enemyMemory = activeEnemy
                )
                currentMatchup = matchupAnalysis

                val decision = com.example.overdex.data.matchup.DecisionEngine.analyze(matchupAnalysis)
                currentDecision = decision

                android.util.Log.d("MATCHUP_ENGINE", "Analysis: ${matchupAnalysis.playerSpecies} vs ${matchupAnalysis.enemySpecies}")
                android.util.Log.d("MATCHUP_ENGINE", "Advantage: ${matchupAnalysis.playerAdvantage} | Threat: ${matchupAnalysis.enemyThreatLevel}")

                android.util.Log.d("DECISION_ENGINE", "Recommendation: ${decision.recommendedAction} (Priority: ${decision.actionPriority})")
                android.util.Log.d("DECISION_ENGINE", "Reasoning: ${decision.reasoning}")
                android.util.Log.d("DECISION_ENGINE", "Shield Recommended: ${decision.shieldRecommended}")
            }
        } else {
            currentMatchup = null
            currentDecision = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PokedexGreen)
            .padding(8.dp) // Tighter bezel aesthetic
    ) {
        // Top Lights (PWR/Red, OBS/Amber, LINK/Green)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Device Emblem (Permanent branding)
            Droidball(
                modifier = Modifier.size(54.dp),
                isInteractive = isLogoInteractive
            )

            Spacer(modifier = Modifier.width(24.dp))

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusIndicator(label = "PWR", color = Color.Red, cycleDurationMillis = 5000)
                StatusIndicator(label = "OBS", color = Color(0xFFFFA500), cycleDurationMillis = 3000)
                StatusIndicator(label = "LINK", color = Color.Green, cycleDurationMillis = 4000)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PokedexGreen)
                .padding(8.dp) // Tighter bezel aesthetic
        ) {
            // Top Lights (PWR/Red, OBS/Amber, LINK/Green)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Device Emblem (Permanent branding)
                Droidball(
                    modifier = Modifier.size(54.dp),
                    isInteractive = isLogoInteractive
                )

                Spacer(modifier = Modifier.width(24.dp))

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatusIndicator(label = "PWR", color = Color.Red, cycleDurationMillis = 5000)
                    StatusIndicator(
                        label = "OBS",
                        color = Color(0xFFFFA500),
                        cycleDurationMillis = 3000
                    )
                    StatusIndicator(label = "LINK", color = Color.Green, cycleDurationMillis = 4000)
                }
            }
        }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left")
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
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right")
                }
            }

            // Down Button
            IconButton(onClick = onDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Down")}
            }
        }
    }
}
