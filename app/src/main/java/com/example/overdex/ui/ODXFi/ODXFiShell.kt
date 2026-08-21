package com.example.overdex.ui.ODXFi

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.example.overdex.ui.components.AndroidPokeballLogo
import com.example.overdex.ui.components.BreathingLED
import com.example.overdex.ui.components.Droidball
import com.example.overdex.ui.components.EnemyTeamMemoryOverlay
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.FilterSettingsOverlay
import com.example.overdex.ui.components.InstrumentLCD
import com.example.overdex.ui.components.LiveMoveAnalysisPanel
import com.example.overdex.ui.components.OverlayState
import com.example.overdex.ui.components.StatusIndicator
import com.example.overdex.ui.components.TerminalKeyboardController
import com.example.overdex.ui.components.glassShield
import com.example.overdex.ui.lcdDisplayEffect
import com.example.overdex.ui.screens.ResearcherModeOverlay
import com.example.overdex.ui.theme.PokedexGreen
import com.example.overdex.ui.theme.PokedexScreen
import com.example.overdex.ui.theme.PokedexScreenBorder
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun InstrumentButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
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
    onALong: () -> Unit = {},
    onB: () -> Unit = {},
    filterSettings: FilterSettings = FilterSettings(),
    onFilterSettingsChange: (FilterSettings) -> Unit = {},
    onSelect: () -> Unit = {},
    onStart: () -> Unit = {},
    onLaunchProbe: () -> Unit = {},
    onLaunchObservatory: () -> Unit = {},
    onLaunchMatchSight: () -> Unit = {},
    deploymentState: InstrumentDeploymentState = InstrumentDeploymentState.IDLE,
    frameCount: Long = 0,
    lcdLine1: String? = null,
    lcdLine2: String? = null,
    keyboardController: TerminalKeyboardController? = null,
    onKeyActivated: ((String) -> Unit)? = null,

    showBattleOverlay: Boolean = true,
    viewModel: PokedexViewModel? = null,
    instrumentState: ObservationSessionState? = null,
    isLogoInteractive: Boolean = false,
    content: @Composable (com.example.overdex.BattleMemory?) -> Unit,
) {
    val activeMatch by viewModel?.activeMatch?.collectAsState() ?: remember { mutableStateOf(null) }
    val battleMemory = activeMatch?.battleMemory

    var showSettings by remember { mutableStateOf(false) }
    var showResearcherSettings by remember { mutableStateOf(false) }
    var overlayState by remember { mutableStateOf(OverlayState.EXPANDED) }

    val currentRoute = com.example.overdex.diagnostics.DiagnosticLogger.LocalCurrentRoute.current
    val owningRoute = remember { currentRoute }
    val instanceId = remember { UUID.randomUUID().toString().take(8) }

    DisposableEffect(instanceId) {
        com.example.overdex.diagnostics.DiagnosticLogger.logLifecycle(instanceId, "INIT", currentRoute)
        onDispose {
            com.example.overdex.diagnostics.DiagnosticLogger.logLifecycle(instanceId, "DISPOSE", currentRoute)
        }
    }

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
            AndroidPokeballLogo(
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

        // Main Screen Area (Dominant Portrait CRT)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f) // Increase dominance of CRT
                .background(Color.DarkGray, RoundedCornerShape(4.dp))
                .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                .padding(top = crtPadding)
                .glassShield() // The Glass Shield enforcement point
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(PokedexScreen)
                    .border(4.dp, PokedexScreenBorder, RoundedCornerShape(2.dp))
                    .padding(4.dp)
            ) {
                // Application Layer (Shader applied here)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (filterSettings.isEnabled) Modifier.lcdDisplayEffect() else Modifier)
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
                                spriteProvider = viewModel?.spriteProvider ?: com.example.overdex.data.GithubSpriteProvider()
                            )

                            // Live Move Analysis Panel - Displays moves for the active enemy
                            LiveMoveAnalysisPanel(
                                opponent = presentationState.team.opponent,
                                tactical = presentationState.tactical
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn() + expandIn(),
                    exit = fadeOut() + shrinkOut()
                ) {
                    FilterSettingsOverlay(
                        settings = filterSettings,
                        onSettingsChange = onFilterSettingsChange,
                        isResearcherUnlocked = isResearcherUnlocked,
                        onOpenResearcher = {
                            showSettings = false
                            showResearcherSettings = true
                        },
                        onClose = { showSettings = false },
                        onUp = { settingsUp = it },
                        onDown = { settingsDown = it },
                        onLeft = { settingsLeft = it },
                        onRight = { settingsRight = it },
                        onA = { settingsA = it },
                        onB = { settingsB = it }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showResearcherSettings,
                    enter = fadeIn() + expandIn(),
                    exit = fadeOut() + shrinkOut()
                ) {
                    ResearcherModeOverlay(
                        onLaunchProbe = {
                            showResearcherSettings = false
                            onLaunchProbe()
                        },
                        onLaunchObservatory = {
                            showResearcherSettings = false
                            onLaunchObservatory()
                        },
                        onLaunchMatchSight = {
                            showResearcherSettings = false
                            onLaunchMatchSight()
                        },
                        onClose = { showResearcherSettings = false },
                        onUp = { researcherUp = it },
                        onDown = { researcherDown = it },
                        onA = { researcherA = it },
                        onB = { researcherB = it }
                    )
                }

                // Unlock Message Overlay
                if (unlockMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = unlockMessage!!,
                            color = TerminalGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Permanent Front Panel Assembly (Compressed Lower Console)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp) // Compressed from 220dp
                .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation Column (Left)
            Column(
                modifier = Modifier.width(64.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InstrumentButton(icon = Icons.Default.ArrowDropUp, onClick = {
                    handleInput("UP")
                    if (showResearcherSettings) researcherUp?.invoke()
                    else if (showSettings) settingsUp?.invoke()
                    else onUp()
                })
                InstrumentButton(icon = Icons.Default.ArrowDropDown, onClick = {
                    handleInput("DOWN")
                    if (showResearcherSettings) researcherDown?.invoke()
                    else if (showSettings) settingsDown?.invoke()
                    else onDown()
                })
                InstrumentButton(icon = Icons.AutoMirrored.Filled.ArrowLeft, onClick = {
                    handleInput("LEFT")
                    if (showSettings) settingsLeft?.invoke()
                    else onLeft()
                })
                InstrumentButton(icon = Icons.AutoMirrored.Filled.ArrowRight, onClick = {
                    handleInput("RIGHT")
                    if (showSettings) settingsRight?.invoke()
                    else onRight()
                })
            }

            // Instrumentation Display (Center)
            InstrumentLCD(
                presentationState = presentationState,
                deploymentState = deploymentState,
                frameCount = frameCount,
                lcdLine1 = lcdLine1,
                lcdLine2 = lcdLine2,
                keyboardController = keyboardController,
                onKeyActivated = onKeyActivated,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            )

            // Action Column (Right)
            Column(
                modifier = Modifier.width(64.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InstrumentButton(
                    label = "A",
                    onClick = {
                        handleInput("A")
                        if (showResearcherSettings) researcherA?.invoke()
                        else if (showSettings) settingsA?.invoke()
                        else onA()
                    },
                    onLongClick = {
                        if (!showResearcherSettings && !showSettings) {
                            onALong()
                        }
                    }
                )
                InstrumentButton(label = "B", onClick = {
                    handleInput("B")
                    if (showResearcherSettings) researcherB?.invoke()
                    else if (showSettings) settingsB?.invoke()
                    else {
                        val isOwner = currentRoute == owningRoute
                        com.example.overdex.diagnostics.DiagnosticLogger.logInput(instanceId, "B", currentRoute, isOwner)
                        if (isOwner) {
                            onB()
                        } else {
                            android.util.Log.d("NavDebug", "STALE DISPATCH: inst=$instanceId route=$currentRoute expected=$owningRoute")
                        }
                    }
                })
                InstrumentButton(label = "SELECT", onClick = { handleInput("SELECT"); onSelect() })
                InstrumentButton(label = "START", onClick = {
                    handleInput("START")
                    if (keyboardController?.isVisible != true && serviceMode) showSettings = true
                    onStart()
                })
            }
        }
    }
}