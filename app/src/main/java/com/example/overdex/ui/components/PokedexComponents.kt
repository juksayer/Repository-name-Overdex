package com.example.overdex.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.theme.*
import com.example.overdex.ResearcherManager
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.screens.ResearcherModeOverlay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.font.FontFamily
import com.example.overdex.ui.ODXFi.InstrumentButton
import com.example.overdex.ui.lcdDisplayEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
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

enum class OverlayState {
    COLLAPSED,
    EXPANDED
}

data class FilterSettings(
    val scanlineIntensity: Float = 0.2f,
    val scanlineSpeed: Float = 0.0f,
    val crtCurvature: Float = 0.1f,
    val noiseIntensity: Float = 0.05f,
    val isEnabled: Boolean = true
)

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

@Composable
fun InstrumentLCD(
    presentationState: com.example.overdex.presentation.PresentationState,
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

    // Matchup Intelligence Foundation Verification
    var currentMatchup by remember { mutableStateOf<com.example.overdex.model.MatchupAnalysis?>(null) }

    val timelineEventCount = battleMemory.timeline.events.size

    val presentationState = remember(currentState, pipelineStatus, battleMemory, currentMatchup, currentDecision, timelineEventCount) {
        com.example.overdex.presentation.PresentationMapper.map(
            instrumentState = currentState,
            pipelineStatus = pipelineStatus,
            battleMemory = battleMemory,
            matchup = currentMatchup,
            decision = currentDecision
        )
    }

    LaunchedEffect(
        battleMemory.enemyTeam.find { it.isActive },
        battleMemory.playerActivePokemon
    ) {
        val activeEnemy = battleMemory.enemyTeam.find { it.isActive }

        if (viewModel != null && activeEnemy != null) {
            val enemyData = viewModel.getPokemonByName(activeEnemy.species)
            val playerData = battleMemory.playerActivePokemon?.let {
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
                InstrumentButton(label = "A", onClick = { 
                    handleInput("A")
                    if (showResearcherSettings) researcherA?.invoke()
                    else if (showSettings) settingsA?.invoke()
                    else onA()
                })
                InstrumentButton(label = "B", onClick = { 
                    handleInput("B")
                    if (showResearcherSettings) researcherB?.invoke()
                    else if (showSettings) settingsB?.invoke()
                    else onB()
                })
                InstrumentButton(label = "SELECT", onClick = { handleInput("SELECT"); onSelect() })
                InstrumentButton(label = "START", onClick = { 
                    handleInput("START")
                    if (serviceMode) showSettings = true
                    onStart()
                })
            }
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
fun AndroidPokeballLogo(
    modifier: Modifier = Modifier,
    isInteractive: Boolean = false
) {
    var trigger by remember { mutableIntStateOf(0) }
    val rotation = remember { Animatable(0f) }
    val wakeIntensity = remember { Animatable(0f) }
    val eyesAlpha = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            // Phase 1: Wake
            launch {
                wakeIntensity.animateTo(1f, tween(400))
            }

            // Phase 2: Acknowledge
            rotation.animateTo(
                targetValue = 15f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessLow
                )
            )

            // Phase 3: Blink
            delay(100)
            eyesAlpha.snapTo(0f)
            delay(60)
            eyesAlpha.snapTo(1f)
            delay(100)
            eyesAlpha.snapTo(0f)
            delay(60)
            eyesAlpha.snapTo(1f)

            delay(500)

            // Phase 4: Rest
            launch {
                wakeIntensity.animateTo(0f, tween(600))
            }
            rotation.animateTo(0f, tween(600))
        }
    }

    Canvas(
        modifier = modifier
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                android.util.Log.d("DROIDBALL", "TAP RECEIVED")
                                trigger++
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                rotationZ = rotation.value
            }
    ) {
        val w = size.width
        val h = size.height

        // Droidball body
        val path = Path().apply {
            addArc(
                oval = Rect(0f, 0f, w, h),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f
            )
        }

        val animatedRed = androidx.compose.ui.graphics.lerp(
            Color.Red,
            Color(0xFFFF4444),
            wakeIntensity.value
        )

        drawContext.canvas.save()

        // Lower half
        drawPath(
            path = path,
            color = Color.White
        )

        // Upper half
        drawPath(
            path = path,
            color = animatedRed
        )

        // Middle black line
        drawRect(
            color = Color.Black,
            topLeft = Offset(
                0f,
                h / 2 - 2.dp.toPx()
            ),
            size = size.copy(
                height = 4.dp.toPx()
            )
        )

        // Center circle
        drawCircle(
            color = Color.Black,
            radius = 10.dp.toPx(),
            center = center
        )

        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = center
        )

        // Eyes
        drawCircle(
            color = Color.White.copy(alpha = eyesAlpha.value),
            radius = 4.dp.toPx(),
            center = Offset(
                w * 0.3f,
                h * 0.3f
            )
        )

        drawCircle(
            color = Color.White.copy(alpha = eyesAlpha.value),
            radius = 4.dp.toPx(),
            center = Offset(
                w * 0.7f,
                h * 0.3f
            )
        )

        drawContext.canvas.restore()

        // Border
        val strokeWidth = androidx.compose.ui.unit.lerp(
            2.dp,
            3.dp,
            wakeIntensity.value
        ).toPx()

        drawPath(
            path = path,
            color = Color.Black,
            style = Stroke(width = strokeWidth)
        )

        // Antennas
        drawLine(
            color = animatedRed,
            start = Offset(
                w * 0.3f,
                h * 0.1f
            ),
            end = Offset(
                w * 0.2f,
                -h * 0.1f
            ),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawLine(
            color = animatedRed,
            start = Offset(
                w * 0.7f,
                h * 0.1f
            ),
            end = Offset(
                w * 0.8f,
                -h * 0.1f
            ),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

enum class FilterFocus {
    ENABLED,
    SCANLINES,
    CURVATURE,
    NOISE,
    RESEARCHER,
    CLOSE
}

@Composable
fun FilterSettingsOverlay(
    settings: FilterSettings,
    onSettingsChange: (FilterSettings) -> Unit,
    isResearcherUnlocked: Boolean = false,
    onOpenResearcher: () -> Unit = {},
    onClose: () -> Unit,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onB: (() -> Unit) -> Unit = {}
) {
    val focusManager = rememberHandheldFocusManager(FilterFocus.ENABLED)
    
    val visibleItems = remember(settings.isEnabled, isResearcherUnlocked) {
        buildList {
            add(FilterFocus.ENABLED)
            if (settings.isEnabled) {
                add(FilterFocus.SCANLINES)
                add(FilterFocus.CURVATURE)
                add(FilterFocus.NOISE)
            }
            if (isResearcherUnlocked) {
                add(FilterFocus.RESEARCHER)
            }
            add(FilterFocus.CLOSE)
        }
    }

    LaunchedEffect(visibleItems) {
        focusManager.updateItems(visibleItems)
    }

    SideEffect {

        onUp { focusManager.moveUp() }
        onDown { focusManager.moveDown() }
        onLeft {
            if (settings.isEnabled) {
                when (focusManager.currentItem) {
                    FilterFocus.SCANLINES -> onSettingsChange(settings.copy(scanlineIntensity = (settings.scanlineIntensity - 0.05f).coerceIn(0f, 1f)))
                    FilterFocus.CURVATURE -> onSettingsChange(settings.copy(crtCurvature = (settings.crtCurvature - 0.05f).coerceIn(0f, 0.5f)))
                    FilterFocus.NOISE -> onSettingsChange(settings.copy(noiseIntensity = (settings.noiseIntensity - 0.05f).coerceIn(0f, 0.5f)))
                    else -> {}
                }
            }
        }
        onRight {
            if (settings.isEnabled) {
                when (focusManager.currentItem) {
                    FilterFocus.SCANLINES -> onSettingsChange(settings.copy(scanlineIntensity = (settings.scanlineIntensity + 0.05f).coerceIn(0f, 1f)))
                    FilterFocus.CURVATURE -> onSettingsChange(settings.copy(crtCurvature = (settings.crtCurvature + 0.05f).coerceIn(0f, 0.5f)))
                    FilterFocus.NOISE -> onSettingsChange(settings.copy(noiseIntensity = (settings.noiseIntensity + 0.05f).coerceIn(0f, 0.5f)))
                    else -> {}
                }
            }
        }
        onA {
            when (focusManager.currentItem) {
                FilterFocus.ENABLED -> onSettingsChange(settings.copy(isEnabled = !settings.isEnabled))
                FilterFocus.RESEARCHER -> onOpenResearcher()
                FilterFocus.CLOSE -> onClose()
                else -> {}
            }
        }
        onB { onClose() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TerminalBlack.copy(alpha = 0.95f),
        contentColor = TerminalGreen
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val closeSelected = focusManager.currentItem == FilterFocus.CLOSE
                Text("FILTER SETTINGS", fontWeight = FontWeight.Bold, color = TerminalPurple)
                Text(
                    text = if (closeSelected) "[ CLOSE ]" else "  CLOSE  ",
                    color = if (closeSelected) TerminalGreen else TerminalDimGreen,
                    modifier = Modifier.padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingToggle(
                label = "Enabled", 
                value = settings.isEnabled, 
                selected = focusManager.currentItem == FilterFocus.ENABLED
            ) { onSettingsChange(settings.copy(isEnabled = it)) }

            if (settings.isEnabled) {
                SettingSlider(
                    label = "Scanlines", 
                    value = settings.scanlineIntensity, 
                    min = 0f, 
                    max = 1f, 
                    selected = focusManager.currentItem == FilterFocus.SCANLINES
                ) { onSettingsChange(settings.copy(scanlineIntensity = it)) }
                
                SettingSlider(
                    label = "Curvature", 
                    value = settings.crtCurvature, 
                    min = 0f, 
                    max = 0.5f, 
                    selected = focusManager.currentItem == FilterFocus.CURVATURE
                ) { onSettingsChange(settings.copy(crtCurvature = it)) }
                
                SettingSlider(
                    label = "Noise", 
                    value = settings.noiseIntensity, 
                    min = 0f, 
                    max = 0.5f, 
                    selected = focusManager.currentItem == FilterFocus.NOISE
                ) { onSettingsChange(settings.copy(noiseIntensity = it)) }
            } else {
                Text("FILTERS DISABLED", color = TerminalDimGreen, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
            }

            if (isResearcherUnlocked) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = TerminalPurple.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (focusManager.currentItem == FilterFocus.RESEARCHER) TerminalPurple.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, if (focusManager.currentItem == FilterFocus.RESEARCHER) TerminalPurple else Color.Transparent)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("OPEN RESEARCHER MODE", fontWeight = FontWeight.Bold, color = TerminalPurple)
                }
            }
        }
    }
}

@Composable
fun SettingToggle(label: String, value: Boolean, selected: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (selected) TerminalGreen else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (selected) TerminalGreen else TerminalDimGreen)
        Text(
            text = if (value) "[ ON ]" else "[ OFF ]",
            color = if (selected) TerminalGreen else TerminalDimGreen,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
fun SettingSlider(label: String, value: Float, min: Float, max: Float, selected: Boolean, onValueChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) TerminalGreen.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (selected) TerminalGreen else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = if (selected) TerminalGreen else TerminalDimGreen)
            Text(
                text = String.format(Locale.ROOT, "%.2f", value),
                color = if (selected) TerminalGreen else TerminalDimGreen,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(TerminalBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value - min) / (max - min))
                    .fillMaxHeight()
                    .background(if (selected) TerminalGreen else TerminalDimGreen)
            )
        }
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
fun SearchBar(
    query: String,
    onSearchClick: () -> Unit = {},
    selected: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) TerminalGreen else TerminalBlack)
            .border(1.dp, TerminalGreen, RoundedCornerShape(24.dp))
            .clickable { onSearchClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (selected) TerminalBlack else TerminalGreen
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = query.ifEmpty { "Search OverDex..." },
                color = if (selected) TerminalBlack else if (query.isEmpty()) TerminalDimGreen else TerminalGreen,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun TypeBadge(
    type: PokemonType,
    modifier: Modifier = Modifier,
    style: TypeIconStyle = TypeIconStyle.GBA,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        color = TerminalBlack,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .padding(2.dp)
            .border(1.dp, type.color, RoundedCornerShape(4.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        PokemonTypeIcon(
            type = type,
            style = style,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
