package com.example.overdex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.PokemonType
import com.example.overdex.ui.theme.*
import com.example.overdex.ResearcherManager
import com.example.overdex.ui.screens.ResearcherModeOverlay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.overdex.ui.lcdDisplayEffect
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

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
fun PokedexFrame(
    onUp: () -> Unit = {},
    onDown: () -> Unit = {},
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    onA: () -> Unit = {},
    onB: () -> Unit = {},
    filterSettings: FilterSettings = FilterSettings(),
    onFilterSettingsChange: (FilterSettings) -> Unit = {},
    onSelect: () -> Unit = {},
    viewModel: com.example.overdex.ui.PokedexViewModel? = null,
    showBattleOverlay: Boolean = true,
    isServiceRunning: Boolean = false,
    content: @Composable (com.example.overdex.BattleMemory) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showResearcherSettings by remember { mutableStateOf(false) }
    var overlayState by remember { mutableStateOf(OverlayState.EXPANDED) }
    
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
            delay(3000)
            unlockMessage = null
        }
    }

    // Simulation is now managed by the ViewModel/Service lifecycle
    LaunchedEffect(isServiceRunning) {
        if (isServiceRunning) {
            battleMemory.runPrototypeSimulation()
        }
    }

    // Matchup Intelligence Foundation Verification
    var currentMatchup by remember { mutableStateOf<com.example.overdex.model.MatchupAnalysis?>(null) }
    
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
            .padding(16.dp)
    ) {
        // Top Lights
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Device Emblem (Permanent branding)
            AndroidPokeballLogo(modifier = Modifier.size(60.dp))
            
            Spacer(modifier = Modifier.width(16.dp))
            
            LightDot(Color.Red)
            LightDot(Color.Yellow)
            LightDot(Color.Green)
        }

        // Main Screen Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.DarkGray, RoundedCornerShape(8.dp))
                .padding(bottom = 24.dp, start = 12.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(PokedexScreen)
                    .border(8.dp, PokedexScreenBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp)
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
                if (showBattleOverlay && isServiceRunning) {
                    Column {
                        // DroidBall (Service indicator and control)
                        AndroidPokeballLogo(
                            modifier = Modifier
                                .size(40.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            overlayState = if (overlayState == OverlayState.EXPANDED) {
                                                OverlayState.COLLAPSED
                                            } else {
                                                OverlayState.EXPANDED
                                            }
                                        }
                                    )
                                }
                        )

                        if (overlayState == OverlayState.EXPANDED) {
                            EnemyTeamMemoryOverlay(
                                enemyTeam = battleMemory.enemyTeam,
                                decision = currentDecision
                            )
                            
                            // Live Move Panel - Displays moves for the active enemy
                            LiveMovePanel(
                                activePokemon = battleMemory.enemyTeam.find { it.isActive },
                                viewModel = viewModel,
                                matchup = currentMatchup
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
                        onClose = { showSettings = false }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showResearcherSettings,
                    enter = fadeIn() + expandIn(),
                    exit = fadeOut() + shrinkOut()
                ) {
                    ResearcherModeOverlay(onClose = { showResearcherSettings = false })
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

        // Control Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // D-Pad
            DPad(
                onUp = { handleInput("UP"); onUp() },
                onDown = { handleInput("DOWN"); onDown() },
                onLeft = { handleInput("LEFT"); onLeft() },
                onRight = { handleInput("RIGHT"); onRight() }
            )

            // Buttons Area
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    ActionButton("B", Color.Black, onClick = { handleInput("B"); onB() })
                    Spacer(modifier = Modifier.width(16.dp))
                    ActionButton("A", Color.Black, onClick = { handleInput("A"); onA() })
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Select/Start
                Row {
                    PillButton("SELECT", onClick = onSelect)
                    Spacer(modifier = Modifier.width(8.dp))
                    PillButton("START", onClick = { showSettings = !showSettings })
                }
            }
        }
    }
}

@Composable
fun AndroidPokeballLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Android Head Shape
        val path = Path().apply {
            addArc(
                oval = Rect(0f, 0f, w, h),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f
            )
        }
        
        drawContext.canvas.save()
        drawPath(path, color = Color.White)
        
        // Top half Red (clip to head shape)
        drawPath(path, color = Color.Red)
        
        // Middle black line
        drawRect(
            color = Color.Black,
            topLeft = Offset(0f, h / 2 - 2.dp.toPx()),
            size = size.copy(height = 4.dp.toPx())
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
            color = Color.White,
            radius = 4.dp.toPx(),
            center = Offset(w * 0.3f, h * 0.3f)
        )
        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = Offset(w * 0.7f, h * 0.3f)
        )
        
        drawContext.canvas.restore()
        
        // Border
        drawPath(path, color = Color.Black, style = Stroke(width = 2.dp.toPx()))
        
        // Antennas
        drawLine(
            color = Color.Red,
            start = Offset(w * 0.3f, h * 0.1f),
            end = Offset(w * 0.2f, -h * 0.1f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.Red,
            start = Offset(w * 0.7f, h * 0.1f),
            end = Offset(w * 0.8f, -h * 0.1f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun FilterSettingsOverlay(
    settings: FilterSettings,
    onSettingsChange: (FilterSettings) -> Unit,
    isResearcherUnlocked: Boolean = false,
    onOpenResearcher: () -> Unit = {},
    onClose: () -> Unit
) {
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
                Text("FILTER SETTINGS", fontWeight = FontWeight.Bold, color = TerminalPurple)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TerminalGreen)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingToggle("Enabled", settings.isEnabled) { onSettingsChange(settings.copy(isEnabled = it)) }
            
            if (settings.isEnabled) {
                SettingSlider("Scanlines", settings.scanlineIntensity, 0f, 1f) { onSettingsChange(settings.copy(scanlineIntensity = it)) }
                SettingSlider("Curvature", settings.crtCurvature, 0f, 0.5f) { onSettingsChange(settings.copy(crtCurvature = it)) }
                SettingSlider("Noise", settings.noiseIntensity, 0f, 0.5f) { onSettingsChange(settings.copy(noiseIntensity = it)) }
            }

            if (isResearcherUnlocked) {
                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = TerminalPurple.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onOpenResearcher,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TerminalPurple.copy(alpha = 0.2f),
                        contentColor = TerminalPurple
                    ),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, TerminalPurple)
                ) {
                    Text("OPEN RESEARCHER MODE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingToggle(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(
            checked = value,
            onCheckedChange = onValueChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TerminalGreen,
                checkedTrackColor = TerminalDarkGreen,
                uncheckedThumbColor = TerminalDimGreen,
                uncheckedTrackColor = TerminalBlack
            )
        )
    }
}

@Composable
fun SettingSlider(label: String, value: Float, min: Float, max: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("$label: ${String.format(Locale.ROOT, "%.2f", value)}")
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = TerminalGreen,
                activeTrackColor = TerminalGreen,
                inactiveTrackColor = TerminalDarkGreen
            )
        )
    }
}

@Composable
fun DPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Horizontal Bar
        Box(
            modifier = Modifier
                .width(112.dp)
                .height(36.dp)
                .background(Color.Black, RoundedCornerShape(4.dp))
        )
        // Vertical Bar
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(112.dp)
                .background(Color.Black, RoundedCornerShape(4.dp))
        )
        
        // Buttons
        Column(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).clickable { onUp() }) {}
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxHeight().weight(1f).clickable { onLeft() }) {}
                Spacer(Modifier.width(36.dp))
                Box(Modifier.fillMaxHeight().weight(1f).clickable { onRight() }) {}
            }
            Box(Modifier.fillMaxWidth().weight(1f).clickable { onDown() }) {}
        }
    }
}

@Composable
fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .clickable { onClick() }
                .border(2.dp, Color.DarkGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Content if needed
        }
        Text(text = label, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PillButton(label: String, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.DarkGray)
                .clickable { onClick() }
        )
        Text(text = label, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LightDot(color: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.Black, CircleShape)
    )
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp)),
        placeholder = { Text("Search OverDex...", color = TerminalDimGreen) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TerminalGreen) },
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = TerminalBlack,
            focusedContainerColor = TerminalBlack,
            unfocusedIndicatorColor = TerminalGreen,
            focusedIndicatorColor = TerminalGreen,
            focusedTextColor = TerminalGreen,
            unfocusedTextColor = TerminalDimGreen,
            cursorColor = TerminalGreen
        ),
        singleLine = true
    )
}

@Composable
fun TypeBadge(
    type: PokemonType,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        color = TerminalBlack,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
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
        Text(
            text = type.name,
            color = type.color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
