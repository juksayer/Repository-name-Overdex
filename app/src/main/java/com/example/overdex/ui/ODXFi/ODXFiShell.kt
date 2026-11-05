package com.example.overdex.ui.ODXFi

import android.content.res.Configuration
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.overdex.presentation.DroidballJourney
import com.example.overdex.presentation.DroidballJourneyPhase
import com.example.overdex.presentation.DroidballChoreography
import com.example.overdex.presentation.rememberDroidballPose
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.DroidballAcknowledgingBody
import com.example.overdex.ui.components.DROIDBALL_BODY_DIAMETER_FRACTION
import com.example.overdex.ui.components.BreathingLED
import com.example.overdex.ui.components.EnemyTeamMemoryOverlay
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.InstrumentLCD
import com.example.overdex.ui.components.LiveMoveAnalysisPanel
import com.example.overdex.ui.components.OverlayState
import com.example.overdex.ui.components.StatusIndicator
import com.example.overdex.ui.components.TerminalKeyboardController
import com.example.overdex.ui.components.TypeIconText
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
    onStart: (() -> Unit)? = null,
    startLabel: String? = null,
    onLaunchProbe: () -> Unit = {},
    onLaunchObservatory: () -> Unit = {},
    onLaunchMatchCalibration: () -> Unit = {},
    deploymentState: InstrumentDeploymentState? = null,
    frameCount: Long = 0,
    lcdLine1: String? = null,
    lcdLine2: String? = null,
    lcdLines: List<String> = emptyList(),
    lcdContent: (@Composable () -> Unit)? = null,
    lcdEdgeToEdgeContent: Boolean = false,
    /** Optional CRT input preview for screens whose main content does not show the query/draft. */
    keyboardPreviewOnCrt: String? = null,
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
    val sessionControl = LocalDroidballSessionControl.current
    val observedDeploymentState by viewModel?.deploymentState?.collectAsState()
        ?: remember { mutableStateOf(InstrumentDeploymentState.IDLE) }
    val currentDeploymentState = deploymentState ?: sessionControl?.deploymentState ?: observedDeploymentState
    val startAction: () -> Unit = onStart ?: sessionControl?.toggle ?: { viewModel?.toggleObservation() }

    var overlayState by remember { mutableStateOf(OverlayState.EXPANDED) }

    val currentRoute = com.example.overdex.diagnostics.DiagnosticLogger.LocalCurrentRoute.current
    var owningRoute by remember { mutableStateOf(currentRoute) }
    SideEffect {
        // NavHost can compose its first screen before the back-stack route is
        // available. Latch that first real route while retaining the stale-input guard.
        if (owningRoute == null && currentRoute != null) owningRoute = currentRoute
    }
    val instanceId = remember { UUID.randomUUID().toString().take(8) }
    val remoteFocusRequester = remember { FocusRequester() }
    val remoteBackPressTracker = remember {
        RemoteBackPressTracker(ViewConfiguration.getLongPressTimeout().toLong())
    }
    val isTelevision = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION

    LaunchedEffect(instanceId, currentRoute) {
        remoteFocusRequester.requestFocus()
    }

    BackHandler(enabled = isTelevision && currentRoute == owningRoute) {
        onB()
    }

    DisposableEffect(instanceId) {
        com.example.overdex.diagnostics.DiagnosticLogger.logLifecycle(instanceId, "INIT", currentRoute)
        onDispose {
            com.example.overdex.diagnostics.DiagnosticLogger.logLifecycle(instanceId, "DISPOSE", currentRoute)
        }
    }

    val currentState = instrumentState ?: ObservationSessionState.IDLE

    val serviceMode = currentState == ObservationSessionState.SERVICE_ACTIVE
    val journey by DroidballJourney.state.collectAsState()
    val droidballPose = rememberDroidballPose(journey, driveDeparture = true)
    LaunchedEffect(journey.generation, journey.phase) {
        if (journey.phase == DroidballJourneyPhase.DOCKING) {
            val elapsed = (System.nanoTime() - journey.startedAtNanos).coerceAtLeast(0) / 1_000_000
            kotlinx.coroutines.delay((DroidballChoreography.DOCKING_MILLIS - elapsed).coerceAtLeast(0))
            DroidballJourney.dockingFinished(journey.generation)
        }
    }

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

    val remoteKeyModifier = Modifier
        .onPreviewKeyEvent { composeEvent ->
            val event = composeEvent.nativeKeyEvent
            val keyDown = event.action == KeyEvent.ACTION_DOWN
            val firstPress = keyDown && event.repeatCount == 0
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (keyDown) onUp()
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (keyDown) onDown()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (keyDown) onLeft()
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (keyDown) onRight()
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (firstPress) onA()
                    true
                }
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_BUTTON_B -> {
                    val action = when (event.action) {
                        KeyEvent.ACTION_DOWN -> remoteBackPressTracker.onDown(
                            eventTimeMillis = event.eventTime,
                            downTimeMillis = event.downTime,
                            repeatCount = event.repeatCount,
                            isLongPress = event.isLongPress,
                        )
                        KeyEvent.ACTION_UP -> remoteBackPressTracker.onUp(
                            eventTimeMillis = event.eventTime,
                            downTimeMillis = event.downTime,
                        )
                        else -> {
                            remoteBackPressTracker.cancel()
                            RemoteBackAction.CONSUME
                        }
                    }
                    if (currentRoute == owningRoute) when (action) {
                        RemoteBackAction.RETURN -> onB()
                        RemoteBackAction.RESTORE_NAVIGATION -> remoteFocusRequester.requestFocus()
                        RemoteBackAction.CONSUME -> Unit
                    }
                    true
                }
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    if (firstPress) onSelect()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_BUTTON_START -> {
                    if (firstPress) startAction()
                    true
                }
                else -> false
            }
        }
        .focusRequester(remoteFocusRequester)
        .focusable()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val keyboardVisible = keyboardController?.isVisible == true
        val panel = keyboardPanelDimensions(maxHeight, keyboardVisible, isBinderMode)
        val controlModifier = Modifier.size(panel.controlWidth, panel.controlHeight)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PokedexGreen)
                .padding(8.dp)
                .then(remoteKeyModifier)
        ) {
            // Top Lights (PWR/Red, OBS/Amber, LINK/Green)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp, start = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                // The empty dock stays in place while the single body is in the field.
                Box(Modifier.size(54.dp)) {
                    if (journey.shellOwnsBody) DroidballAcknowledgingBody(
                        // Travel remains screen-left even when the binder rotates the body.
                        modifier = Modifier.fillMaxSize()
                            .absoluteOffset(x = 54.dp * DROIDBALL_BODY_DIAMETER_FRACTION * droidballPose.xBodies)
                            .rotate(if (isBinderMode) 90f else 0f),
                        pose = droidballPose.copy(xBodies = 0f),
                        interactive = isLogoInteractive && journey.phase == DroidballJourneyPhase.DOCKED &&
                            currentRoute == owningRoute,
                        resetKey = journey.generation to currentRoute,
                    )
                }

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
                            // Keep the content call site stable when typing starts/stops:
                            // binder paging and directory state belong to this composition.
                            Column(Modifier.fillMaxSize()) {
                                if (keyboardController?.isVisible == true && keyboardPreviewOnCrt != null) {
                                    TypeIconText(
                                        text = keyboardPreviewOnCrt,
                                        color = TerminalGreen,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Box(Modifier.weight(1f).fillMaxWidth()) { content(battleMemory) }
                            }
                        }

                    // HUD Overlay Layer (Kept clean and sharp)
                    if (showBattleOverlay && serviceMode) {
                        Column {
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

            @Composable fun NavigationButton(direction: Int) {
                InstrumentButton(
                    modifier = controlModifier,
                    icon = when (direction) {
                        0 -> Icons.Default.ArrowDropUp
                        1 -> Icons.Default.ArrowDropDown
                        2 -> Icons.AutoMirrored.Filled.ArrowLeft
                        else -> Icons.AutoMirrored.Filled.ArrowRight
                    },
                    contentDescription = listOf("Up", "Down", "Left", "Right")[direction],
                    iconRotationDegrees = if (isBinderMode) 90f else 0f,
                    onClick = when (direction) {
                        0 -> onUp
                        1 -> onDown
                        2 -> onLeft
                        else -> onRight
                    },
                    onLongClick = when (direction) {
                        0 -> onUpLong
                        1 -> onDownLong
                        2 -> onLeftLong
                        else -> onRightLong
                    },
                    onPressChanged = when (direction) {
                        0 -> onUpPressChanged
                        1 -> onDownPressChanged
                        2 -> if (isBinderMode) onLeftPressChanged else null
                        else -> if (isBinderMode) onRightPressChanged else null
                    },
                )
            }

            @Composable fun NavigationButtons() {
                repeat(4) { NavigationButton(it) }
            }

            @Composable fun ActionButton(action: Int) {
                when (action) {
                    0 -> InstrumentButton(
                        modifier = controlModifier,
                        label = "A",
                        labelRotationDegrees = if (isBinderMode) 90f else 0f,
                        onClick = onA,
                        onLongClick = onALong,
                    )
                    1 -> InstrumentButton(
                        modifier = controlModifier,
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
                        },
                    )
                    2 -> InstrumentButton(
                        modifier = controlModifier,
                        label = if (isBinderMode) null else "SELECT",
                        icon = if (isBinderMode) Icons.Default.Search else null,
                        iconRotationDegrees = if (isBinderMode) 90f else 0f,
                        contentDescription = if (isBinderMode) "Search Pokédex" else "Select",
                        onClick = onSelect,
                        onLongClick = onSelectLong,
                    )
                    else -> InstrumentButton(
                        modifier = controlModifier,
                        // Contextual actions retain their own key rather than
                        // claiming to Stop Droidball while editing a card.
                        label = startLabel ?: if (onStart != null && deploymentState == null) "START" else when (currentDeploymentState) {
                            InstrumentDeploymentState.OBSERVING,
                            InstrumentDeploymentState.DEPLOYING,
                            InstrumentDeploymentState.READY,
                            InstrumentDeploymentState.RETURNING -> "STOP"
                            else -> "START"
                        },
                        onClick = startAction,
                        enabled = onStart != null || currentDeploymentState != InstrumentDeploymentState.RETURNING,
                    )
                }
            }

            @Composable fun ActionButtons() {
                repeat(4) { ActionButton(it) }
            }

            @Composable fun Lcd(modifier: Modifier) {
                InstrumentLCD(
                    presentationState = presentationState,
                    deploymentState = currentDeploymentState,
                    frameCount = frameCount,
                    lcdLine1 = lcdLine1,
                    lcdLine2 = lcdLine2,
                    lcdLines = lcdLines,
                    lcdContent = lcdContent,
                    keyboardController = keyboardController,
                    onKeyActivated = onKeyActivated,
                    onDrag = onLcdDrag,
                    onTap = onLcdTap,
                    contentRotationDegrees = if (isBinderMode) 90f else 0f,
                    edgeToEdgeContent = isBinderMode || lcdEdgeToEdgeContent || keyboardVisible,
                    modifier = modifier
                )
            }

            val panelModifier = Modifier
                .fillMaxWidth()
                .height(panel.panelHeight)
                .background(instrumentBezelColor, RoundedCornerShape(4.dp))
                .border(1.dp, instrumentBezelBorderColor, RoundedCornerShape(4.dp))
                .padding(vertical = 8.dp)
            if (panel.controlsBelowLcd) {
                // Wide letter keys stay above the controls. Directions form a
                // familiar cross on the left; action keys form a second cross
                // on the right, with Start above Select/B and A below.
                Column(panelModifier) {
                    Lcd(Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().height(panel.controlHeight * 3),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.width(panel.controlWidth * 3),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            NavigationButton(0)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                NavigationButton(2)
                                NavigationButton(3)
                            }
                            NavigationButton(1)
                        }
                        Column(
                            Modifier.width(panel.controlWidth * 3),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ActionButton(3)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                ActionButton(2)
                                ActionButton(1)
                            }
                            ActionButton(0)
                        }
                    }
                }
            } else {
                // Browsing keeps the original console. Rotated binder typing
                // retains side controls and grows the rotated LCD's width.
                Row(panelModifier,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(panel.controlWidth).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        NavigationButtons()
                    }
                    Lcd(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(panel.controlWidth).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        ActionButtons()
                    }
                }
            }
        }
    }
}
