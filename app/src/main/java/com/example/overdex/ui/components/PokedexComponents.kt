package com.example.overdex.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.example.overdex.ResearcherManager
import com.example.overdex.model.PokemonType
import com.example.overdex.model.observation.InstrumentDeploymentState
import com.example.overdex.model.observation.ObservationSessionState
import com.example.overdex.presentation.ObservationRequirement
import com.example.overdex.presentation.PresentationState
import com.example.overdex.presentation.SemanticTimelineEventType
import com.example.overdex.presentation.TacticalEvidence
import com.example.overdex.ui.ODXFi.InstrumentButton
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.lcdDisplayEffect
import com.example.overdex.ui.screens.ResearcherModeOverlay
import com.example.overdex.ui.theme.TerminalBlack
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
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
    val scanlineIntensity: Float = 1.0f,
    val scanlineSpeed: Float = 0.1f,
    val crtCurvature: Float = 0.1f,
    val noiseIntensity: Float = 0.05f,
    val isEnabled: Boolean = true
)

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

@Composable
fun InstrumentLCD(
    presentationState: PresentationState,
    deploymentState: InstrumentDeploymentState = InstrumentDeploymentState.IDLE,
    frameCount: Long = 0,
    lcdLine1: String? = null,
    lcdLine2: String? = null,
    keyboardController: TerminalKeyboardController? = null,
    onKeyActivated: ((String) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val latestIdentifiedPokemon = presentationState.timeline.events
        .lastOrNull {
            it.type == SemanticTimelineEventType.POKEMON_IDENTIFIED
        }
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .then(
                if (onDrag != null || onTap != null) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag?.invoke(dragAmount)
                            }
                        )
                    }.pointerInput(Unit) {
                        detectTapGestures { onTap?.invoke() }
                    }
                } else Modifier
            )
            .padding(8.dp)
    ) {
        // LCD Surface
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121510)) // Dim greenish-black LCD
                .border(1.dp, Color.Black, RoundedCornerShape(1.dp))
                .padding(horizontal = 8.dp, vertical = if (keyboardController?.isVisible == true) 4.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (keyboardController?.isVisible == true) {
                TerminalKeyboard(
                    layout = keyboardController.layout,
                    currentRow = keyboardController.currentRow,
                    currentColumn = keyboardController.currentCol,
                    onKeyActivated = onKeyActivated,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (lcdLine1 != null || lcdLine2 != null) {
                lcdLine1?.let {
                    Text(
                        text = it,
                        color = TerminalGreen.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                lcdLine2?.let {
                    Text(
                        text = it,
                        color = TerminalGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                // Semantic Presentation Rendering
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header / Identified Pokemon
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        latestIdentifiedPokemon?.let { event ->
                            Text(
                                text = event.actor.name,
                                color = TerminalGreen.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Text(
                                text = event.message ?: "UNKNOWN",
                                color = TerminalGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        } ?: run {
                            Text(
                                text = presentationState.instrument.name,
                                color = TerminalGreen.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Tactical & Observation Graphical Layer
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Tactical Evidence (Moves, Advantages)
                        if (presentationState.tactical.evidence.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DecisionIcon(presentationState.tactical)
                                
                                presentationState.tactical.evidence.forEach { evidence ->
                                    when (evidence) {
                                        is TacticalEvidence.ObservedMove -> {
                                            // Find move type from opponent known moves if available to keep it direct
                                            val moveType = presentationState.team.opponent.knownMoves
                                                .find { it.name == evidence.moveName }?.type ?: PokemonType.NORMAL
                                            
                                            TypeBadge(
                                                type = moveType,
                                                style = TypeIconStyle.OVERDEX,
                                                modifier = Modifier.scale(0.7f)
                                            )
                                        }
                                        is TacticalEvidence.TypeAdvantage -> {
                                            // Represented by DecisionIcon tint primarily, but could add small indicator
                                        }
                                        is TacticalEvidence.EnergyLead -> {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = Color.Yellow,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Observation Requirements (Missing icons)
                        if (presentationState.observation.missingRequirements.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ObservationRequirement.entries.forEach { req ->
                                    val isMissing = presentationState.observation.missingRequirements.contains(req)
                                    val icon = when (req) {
                                        ObservationRequirement.SPECIES -> androidx.compose.material.icons.Icons.Default.Search
                                        ObservationRequirement.COMBAT_POWER -> androidx.compose.material.icons.Icons.Default.Info
                                        ObservationRequirement.FAST_MOVE -> androidx.compose.material.icons.Icons.Default.Bolt
                                        ObservationRequirement.CHARGED_MOVE_A -> androidx.compose.material.icons.Icons.Default.Star
                                        ObservationRequirement.CHARGED_MOVE_B -> androidx.compose.material.icons.Icons.Default.Star
                                    }
                                    
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isMissing) TerminalGreen.copy(alpha = 0.2f) else TerminalGreen,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom: Player Status / Lifecycle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = presentationState.team.player.activeSpecies?.uppercase() ?: "---",
                            color = TerminalGreen.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Text(
                            text = "SHIELDS: ${presentationState.team.player.shieldsUsed}/2",
                            color = TerminalGreen.copy(alpha = 0.6f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
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
                    FilterFocus.SCANLINES -> onSettingsChange(settings.copy(scanlineIntensity = (settings.scanlineIntensity + 0.05f).coerceIn(0f, 0.5f)))
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
                    max = 1.0f,
                    selected = focusManager.currentItem == FilterFocus.CURVATURE
                ) { onSettingsChange(settings.copy(crtCurvature = it)) }
                
                SettingSlider(
                    label = "Noise", 
                    value = settings.noiseIntensity, 
                    min = 0f, 
                    max = 1.0f,
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
    onSearchClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) TerminalGreen else TerminalBlack)
            .border(1.dp, TerminalGreen, RoundedCornerShape(24.dp))
            .then(if (onSearchClick != null) Modifier.clickable { onSearchClick() } else Modifier)
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
