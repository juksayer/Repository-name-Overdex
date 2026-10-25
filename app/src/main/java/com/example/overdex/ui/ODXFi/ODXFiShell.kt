package com.example.overdex.ui.ODXFi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.example.overdex.ui.components.AndroidPokeballLogo
import com.example.overdex.ui.components.BreathingLED
import com.example.overdex.ui.components.Droidball
import com.example.overdex.ui.components.EnemyTeamMemoryOverlay
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.InstrumentLCD
import com.example.overdex.ui.components.LiveMoveAnalysisPanel
import com.example.overdex.ui.components.OverlayState
import com.example.overdex.ui.components.StatusIndicator
import com.example.overdex.ui.components.TerminalKeyboardController
import com.example.overdex.ui.components.glassShield
import com.example.overdex.ui.lcdDisplayEffect
import com.example.overdex.ui.theme.PokedexGreen
import com.example.overdex.ui.theme.PokedexScreen
import com.example.overdex.ui.theme.TerminalGreen
import java.util.UUID


@Composable
fun InstrumentButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPressChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    color: Color = Color.DarkGray,
    labelColor: Color = Color.White.copy(alpha = 0.6f),
    enabled: Boolean = true,
    iconRotationDegrees: Float = 0f,
    labelRotationDegrees: Float = 0f,
    contentDescription: String? = label
) {
    val click = rememberUpdatedState(onClick)
    val longClick = rememberUpdatedState(onLongClick)
    val pressChanged = rememberUpdatedState(onPressChanged)
    val interactionModifier = if (onPressChanged != null && enabled) {
        // A repeated action recomposes its screen. Callback identity must not
        // cancel the still-held pointer or synthesize an early release.
        Modifier.pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    pressChanged.value?.invoke(true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressChanged.value?.invoke(false)
                    }
                },
                onTap = { click.value() },
                onLongPress = { longClick.value?.invoke() }
            )
        }
    } else {
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }

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
            .then(interactionModifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = labelColor,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(iconRotationDegrees)
            )
        } else if (label != null) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.rotate(labelRotationDegrees)
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

/**
 * Presents a landscape instrument viewport inside the fixed portrait hardware.
 * The phone remains portrait; only the active instrument surface turns.
 */
@Composable
private fun RotatedInstrumentViewport(
    active: Boolean,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = -90f,
    content: @Composable () -> Unit
) {
    if (!active) {
        Box(modifier = modifier) { content() }
        return
    }

    BoxWithConstraints(modifier = modifier) {
        Box(
            modifier = Modifier
                // Rotation swaps the available axes. required* is intentional:
                // ordinary width/height are clamped by the portrait parent and
                // collapse this landscape surface into a square.
                .requiredWidth(maxHeight)
                .requiredHeight(maxWidth)
                .align(Alignment.Center)
                .rotate(rotationDegrees)
        ) {
            content()
        }
    }
}

@Composable
fun ODXFiShell(
    onUp: () -> Unit = {},
    onUpLong: () -> Unit = {},
    onUpPressChanged: ((Boolean) -> Unit)? = null,
    onDown: () -> Unit = {},
    onDownLong: () -> Unit = {},
    onDownPressChanged: ((Boolean) -> Unit)? = null,
    onLeft: () -> Unit = {},
    onLeftLong: () -> Unit = {},
    onLeftPressChanged: (Boolean) -> Unit = {},
    onRight: () -> Unit = {},
    onRightLong: () -> Unit = {},
    onRightPressChanged: (Boolean) -> Unit = {},
    onA: () -> Unit = {},
    onALong: () -> Unit = {},
    onB: () -> Unit = {},
    filterSettings: FilterSettings = FilterSettings(),
    onFilterSettingsChange: (FilterSettings) -> Unit = {},
    onSelect: () -> Unit = {},
    onSelectLong: () -> Unit = {},
    onStart: () -> Unit = {},
    startLabel: String? = null,
    onLaunchProbe: () -> Unit = {},
    onLaunchObservatory: () -> Unit = {},
    onLaunchMatchCalibration: () -> Unit = {},
    deploymentState: InstrumentDeploymentState = InstrumentDeploymentState.IDLE,
    frameCount: Long = 0,
    lcdLine1: String? = null,
    lcdLine2: String? = null,
    lcdLines: List<String> = emptyList(),
    lcdContent: (@Composable () -> Unit)? = null,
    keyboardPrompt: String? = null,
    title: String? = null,
    breadcrumb: String? = null,
    keyboardController: TerminalKeyboardController? = null,
    onKeyActivated: ((String) -> Unit)? = null,
    onLcdDrag: ((Offset) -> Unit)? = null,
    onLcdTap: (() -> Unit)? = null,

    showBattleOverlay: Boolean = true,
    viewModel: PokedexViewModel? = null,
    instrumentState: ObservationSessionState? = null,
    isLogoInteractive: Boolean = false,
    isBinderMode: Boolean = false,
    content: @Composable (com.example.overdex.BattleMemory?) -> Unit,
) {
    val activeMatch by viewModel?.activeMatch?.collectAsState() ?: remember { mutableStateOf(null) }
    val battleMemory = activeMatch?.battleMemory

    var overlayState by remember { mutableStateOf(OverlayState.EXPANDED) }

    val currentRoute = com.example.overdex.diagnostics.DiagnosticLogger.LocalCurrentRoute.current
    var owningRoute by remember { mutableStateOf(currentRoute) }
    SideEffect {
        // NavHost can compose its first screen before the back-stack route is
        // available. Latch that first real route while retaining the stale-input guard.
        if (owningRoute == null && currentRoute != null) owningRoute = currentRoute
    }
    val instanceId = remember { UUID.randomUUID().toString().take(8) }

    DisposableEffect(instanceId) {
        com.example.overdex.diagnostics.DiagnosticLogger.logLifecycle(instanceId, "INIT", currentRoute)
        onDispose {
            com.example.overdex.diagnostics.DiagnosticLogger.logLifecycle(instanceId, "DISPOSE", currentRoute)
        }
    }

    val currentState = instrumentState ?: ObservationSessionState.IDLE

    val serviceMode = currentState == ObservationSessionState.SERVICE_ACTIVE

    // Shared physical material for the CRT and lower service assembly. The top
    // CRT rail is deeper only to retain the etched model badge.
    val instrumentBezelColor = Color.Black.copy(alpha = 0.08f)
    val instrumentBezelBorderColor = Color.Black.copy(alpha = 0.15f)
    val crtTopBezel = 28.dp

    val context = LocalContext.current
    var currentDecision by remember { mutableStateOf<com.example.overdex.model.DecisionAnalysis?>(null) }

    // Matchup Intelligence Foundation Verification
    var currentMatchup by remember { mutableStateOf<com.example.overdex.model.MatchupAnalysis?>(null) }

    val timelineEventCount = battleMemory?.timeline?.events?.size ?: 0

    val presentationState = remember(
        battleMemory,
        timelineEventCount
    ) {
        com.example.overdex.presentation.PresentationState(
            timeline = com.example.overdex.presentation.TimelinePresentation(
                eventCount = timelineEventCount
            )
        )
    }

    LaunchedEffect(
        battleMemory?.enemyTeam?.find { it.isActive },
        battleMemory?.playerActivePokemon
    ) {
        if (battleMemory == null) return@LaunchedEffect

        val activeEnemy = battleMemory.enemyTeam.find { it.isActive }

        if (viewModel != null && activeEnemy != null) {
            val enemyData = activeEnemy.speciesId?.let {
                viewModel.getPokemonById(it)
            } ?: viewModel.getPokemonByName(activeEnemy.species)
            val playerData = battleMemory.playerActivePokemonId?.let {
                viewModel.getPokemonById(it)
            } ?: battleMemory.playerActivePokemon?.let {
                viewModel.getPokemonByName(it)
            }

            if (enemyData != null && playerData != null) {
                val matchupAnalysis = com.example.overdex.data.matchup.MatchupEngine.analyze(
                    player = playerData,
                    enemy = enemyData,
                    enemyMemory = activeEnemy
                )
                currentMatchup = matchupAnalysis

                val decision = com.example.overdex.data.matchup.DecisionEngine.analyze(matchupAnalysis)
                currentDecision = decision

                android.util.Log.d(
                    "MATCHUP_ENGINE",
                    "Analysis: ${matchupAnalysis.playerSpecies} vs ${matchupAnalysis.enemySpecies}"
                )
                android.util.Log.d(
                    "MATCHUP_ENGINE",
                    "Advantage: ${matchupAnalysis.playerAdvantage} | Threat: ${matchupAnalysis.enemyThreatLevel}"
                )

                android.util.Log.d(
                    "DECISION_ENGINE",
                    "Recommendation: ${decision.recommendedAction} (Priority: ${decision.actionPriority})"
                )
                android.util.Log.d(
                    "DECISION_ENGINE",
                    "Reasoning: ${decision.reasoning}"
                )
                android.util.Log.d(
                    "DECISION_ENGINE",
                    "Shield Recommended: ${decision.shieldRecommended}"
                )
            } else {
                currentMatchup = null
                currentDecision = null
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
            .padding(8.dp)
    ) {
        // Top Lights (PWR/Red, OBS/Amber, LINK/Green)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp, start = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Device Emblem (Permanent branding)
            AndroidPokeballLogo(
                modifier = Modifier
                    .size(54.dp)
                    .rotate(if (isBinderMode) 90f else 0f),
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

        // Main Screen Area (Dominant Portrait CRT)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f) // Increase dominance of CRT
                .background(instrumentBezelColor, RoundedCornerShape(4.dp))
                .border(1.dp, instrumentBezelBorderColor, RoundedCornerShape(4.dp))
                .padding(
                    bottom = 8.dp,
                    start = 8.dp,
                    end = 8.dp
                )
                .glassShield() // The Glass Shield enforcement point
        ) {
            Text(
                text = "oDX-Fi",
                color = Color.Black.copy(alpha = 0.4f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 5.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = crtTopBezel)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isBinderMode) Color(0xFF241A12) else PokedexScreen)
                    .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            ) {
                RotatedInstrumentViewport(
                    active = isBinderMode,
                    modifier = Modifier.fillMaxSize(),
                    rotationDegrees = 90f
                ) {
                    // Application Layer (Shader applied here)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // A binder is viewed as a physical object on a tabletop. Keep
                            // the CRT shader for instrument screens, but do not bend,
                            // vignette, or scan-line the binder pages.
                            .then(
                                if (filterSettings.isEnabled && !isBinderMode) {
                                    Modifier.lcdDisplayEffect(filterSettings)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        content(battleMemory)
                    }

                // HUD Overlay Layer (Kept clean and sharp)
                if (showBattleOverlay && serviceMode) {
                    Column {
                        // DroidBall (Service indicator and control)
                        // Architecture: Droidball is a specialized observation-aware view of the logo.
                        // When presentation state is present, we use Droidball to reflect the state.
                        Droidball(
                            presentationState = presentationState,
                            modifier = Modifier.size(40.dp)
                        )

                        if (overlayState == OverlayState.EXPANDED) {
                            EnemyTeamMemoryOverlay(
                                opponent = presentationState.team.opponent,
                                tactical = presentationState.tactical,
                                spriteProvider = viewModel?.spriteProvider
                                    ?: com.example.overdex.data.LocalSpriteProvider(context.assets)
                            )

                            // Live Move Analysis Panel - Displays moves for the active enemy
                            LiveMoveAnalysisPanel(
                                opponent = presentationState.team.opponent,
                                tactical = presentationState.tactical
                            )
                        }
                    }
                }

                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Permanent Front Panel Assembly (Compressed Lower Console)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp) // Compressed from 220dp
                .background(instrumentBezelColor, RoundedCornerShape(4.dp))
                .border(1.dp, instrumentBezelBorderColor, RoundedCornerShape(4.dp))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation Column (Left)
            Column(
                modifier = Modifier.width(56.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InstrumentButton(
                    icon = Icons.Default.ArrowDropUp,
                    contentDescription = "Up",
                    iconRotationDegrees = if (isBinderMode) 90f else 0f,
                    onClick = onUp,
                    onLongClick = onUpLong,
                    onPressChanged = onUpPressChanged,
                )
                InstrumentButton(
                    icon = Icons.Default.ArrowDropDown,
                    contentDescription = "Down",
                    iconRotationDegrees = if (isBinderMode) 90f else 0f,
                    onClick = onDown,
                    onLongClick = onDownLong,
                    onPressChanged = onDownPressChanged,
                )
                InstrumentButton(
                    icon = Icons.AutoMirrored.Filled.ArrowLeft,
                    contentDescription = "Left",
                    iconRotationDegrees = if (isBinderMode) 90f else 0f,
                    onClick = onLeft,
                    onLongClick = onLeftLong,
                    onPressChanged = if (isBinderMode) onLeftPressChanged else null
                )
                InstrumentButton(
                    icon = Icons.AutoMirrored.Filled.ArrowRight,
                    contentDescription = "Right",
                    iconRotationDegrees = if (isBinderMode) 90f else 0f,
                    onClick = onRight,
                    onLongClick = onRightLong,
                    onPressChanged = if (isBinderMode) onRightPressChanged else null
                )
            }

            // Instrumentation Display (Center). The LCD is physical hardware;
            // Binder mode rotates only the pixels shown inside its fixed frame.
            InstrumentLCD(
                presentationState = presentationState,
                deploymentState = deploymentState,
                frameCount = frameCount,
                lcdLine1 = lcdLine1,
                lcdLine2 = lcdLine2,
                lcdLines = lcdLines,
                lcdContent = lcdContent,
                keyboardController = keyboardController,
                keyboardPrompt = keyboardPrompt,
                onKeyActivated = onKeyActivated,
                onDrag = onLcdDrag,
                onTap = onLcdTap,
                contentRotationDegrees = if (isBinderMode) 90f else 0f,
                edgeToEdgeContent = isBinderMode,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            // Action Column (Right)
            Column(
                modifier = Modifier.width(56.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InstrumentButton(
                    label = "A",
                    labelRotationDegrees = if (isBinderMode) 90f else 0f,
                    onClick = onA,
                    onLongClick = onALong
                )
                InstrumentButton(
                    label = "B",
                    labelRotationDegrees = if (isBinderMode) 90f else 0f,
                    onClick = {
                        val isOwner = currentRoute == owningRoute
                        com.example.overdex.diagnostics.DiagnosticLogger.logInput(instanceId, "B", currentRoute, isOwner)
                        if (isOwner) {
                            onB()
                        } else {
                            android.util.Log.d("NavDebug", "STALE DISPATCH: inst=$instanceId route=$currentRoute expected=$owningRoute")
                        }
                    }
                )
                InstrumentButton(
                    label = if (isBinderMode) null else "SELECT",
                    icon = if (isBinderMode) Icons.Default.Search else null,
                    iconRotationDegrees = if (isBinderMode) 90f else 0f,
                    contentDescription = if (isBinderMode) "Search Pokédex" else "Select",
                    onClick = onSelect,
                    onLongClick = onSelectLong
                )
                InstrumentButton(
                    label = startLabel ?: when (deploymentState) {
                        InstrumentDeploymentState.OBSERVING, 
                        InstrumentDeploymentState.DEPLOYING,
                        InstrumentDeploymentState.READY -> "STOP"
                        else -> "START"
                    }, 
                    // START/STOP belongs to this screen. Settings are reached
                    // explicitly through Signal Observatory, never by session state.
                    onClick = onStart
                )
            }
        }
    }
}
