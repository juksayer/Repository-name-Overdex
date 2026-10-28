package com.example.overdex.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.R
import com.example.overdex.battle.observation.CaptureDiagnostics
import com.example.overdex.battle.observation.BattleHudLayout
import com.example.overdex.battle.observation.DroidballOverlayMode
import com.example.overdex.battle.observation.DroidballOverlayPresentation
import com.example.overdex.battle.observation.OpponentMovePossibilities
import com.example.overdex.battle.observation.ObservedOpponentSpecies
import com.example.overdex.data.LocalSpriteProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.overdex.battle.observation.OverlayMovePossibility
import com.example.overdex.battle.observation.DroidballService
import kotlin.math.roundToInt

/** Geometry shared by the floating window and its Compose content. */
internal object BattleHudOverlayGeometry {
    private const val REFERENCE_WIDTH = 1080f
    private const val REFERENCE_HEIGHT = 2400f
    private const val OPPONENT_BADGE_LEFT = 645f
    private const val OPPONENT_BADGE_BOTTOM = 350f
    private const val OPPONENT_BADGE_WIDTH = 415f

    // The vector's equatorial band begins at 10.5/24 of its 48dp field body.
    // Splitting here keeps the band and center button entirely on the lower jaw.
    const val DROIDBALL_TOP_HALF_DP = 21f
    const val DROIDBALL_BOTTOM_BODY_DP = 27f
    const val DROIDBALL_BUTTON_UNDERBITE_DP = 3f
    const val DROIDBALL_BOTTOM_HALF_DP = DROIDBALL_BOTTOM_BODY_DP + DROIDBALL_BUTTON_UNDERBITE_DP

    fun panelLeftPx(displayWidth: Int): Int =
        (displayWidth * OPPONENT_BADGE_LEFT / REFERENCE_WIDTH).roundToInt()

    fun panelTopPx(displayHeight: Int): Int =
        (displayHeight * OPPONENT_BADGE_BOTTOM / REFERENCE_HEIGHT).roundToInt()

    /** Window Y that places the panel's top edge directly against the GO badge. */
    fun attachedPanelWindowTopPx(displayHeight: Int, windowScreenOffsetY: Int): Int =
        (panelTopPx(displayHeight) - windowScreenOffsetY).coerceAtLeast(0)

    /**
     * The editor may move the HUD above its default badge seam. Only the actual
     * screen edge is a hard stop; the badge anchor is a starting position, not
     * a movement restriction.
     */
    fun minimumEditableWindowTopPx(windowScreenOffsetY: Int): Int =
        (-windowScreenOffsetY).coerceAtLeast(0)

    fun panelWidthPx(displayWidth: Int): Int =
        (displayWidth * OPPONENT_BADGE_WIDTH / REFERENCE_WIDTH).roundToInt()

    fun jointDepthPx(displayWidth: Int): Int =
        BattleHudBadgeStyle.cornerPx(panelWidthPx(displayWidth).toFloat()).roundToInt()

    /** Existing saved Y is the flat seam; only the new corner tips extend above it. */
    fun windowTopForSavedSeam(savedSeamY: Int, displayWidth: Int): Int = savedSeamY - jointDepthPx(displayWidth)
    fun savedSeamForWindowTop(windowTopY: Int, displayWidth: Int): Int = windowTopY + jointDepthPx(displayWidth)
}

/**
 * Droidball's field body. Before battle it is a movable control; opening it
 * exposes the instrument panel between its two physical halves. Live battle
 * state opens that panel automatically because it now has decision-time work.
 */
@Composable
fun BattleOverlay(
    panelWidthPx: Int = 415,
    initialLayout: BattleHudLayout = BattleHudLayout(),
    onDrag: (Float, Float) -> Unit = { _, _ -> },
    onDragFinished: () -> Unit = {},
    onHalfPositionsChanged: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    onLayoutStateChanged: (anchoredToBattleHud: Boolean, panelVisible: Boolean) -> Unit = { _, _ -> },
    onExitIconCalibration: () -> Unit = {},
) {
    val requestedMode by DroidballOverlayPresentation.mode.collectAsState()
    val expanded by DroidballOverlayPresentation.expanded.collectAsState()
    val diagnostics by DroidballService.captureDiagnostics.collectAsState()
    val opponentSpecies by DroidballOverlayPresentation.opponentSpecies.collectAsState()
    val opponentMoves by DroidballOverlayPresentation.activeOpponentMovePossibilities.collectAsState()
    val inferredPlayerTeam by DroidballOverlayPresentation.inferredPlayerTeam.collectAsState()
    val playerTeamConfirmed by DroidballOverlayPresentation.playerTeamConfirmed.collectAsState()
    val configuredPlayerTeam by DroidballOverlayPresentation.configuredPlayerTeam.collectAsState()
    val layoutEditing = false
    val badgeSeen by DroidballOverlayPresentation.battleBadgeSeen.collectAsState()
    val mode = com.example.overdex.battle.observation.battleOverlayDisplayMode(requestedMode, badgeSeen)
    var sessionControlsOpen by remember(mode) { mutableStateOf(false) }
    var topHalfOffsetX by remember(initialLayout) { mutableFloatStateOf(initialLayout.topHalfOffsetX) }
    var topHalfOffsetY by remember(initialLayout) { mutableFloatStateOf(initialLayout.topHalfOffsetY) }
    var bottomHalfOffsetX by remember(initialLayout) { mutableFloatStateOf(initialLayout.bottomHalfOffsetX) }
    var bottomHalfOffsetY by remember(initialLayout) { mutableFloatStateOf(initialLayout.bottomHalfOffsetY) }
    var arrived by remember { mutableStateOf(false) }
    val arrivalOffset by animateDpAsState(
        targetValue = if (arrived) 0.dp else 58.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "droidball field arrival"
    )
    LaunchedEffect(Unit) { arrived = true }

    val anchoredToBattleHud = mode == DroidballOverlayMode.BATTLE_HUD || mode == DroidballOverlayMode.BATTLE_LIVE
    val panelIsVisible = expanded || anchoredToBattleHud
    val mayMove = !anchoredToBattleHud && mode != DroidballOverlayMode.RESULT
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val overlayView = LocalView.current
    LaunchedEffect(anchoredToBattleHud, panelIsVisible) {
        onLayoutStateChanged(anchoredToBattleHud, panelIsVisible)
    }
    val interactionModifier = if (anchoredToBattleHud && badgeSeen) {
        // The whole panel is the handle. Require a deliberate hold so a battle
        // tap or short swipe cannot shift it, and keep the gesture alive while
        // species, moves and energy update underneath the user's finger.
        Modifier.pointerInput(anchoredToBattleHud, badgeSeen) {
            val location = IntArray(2)
            fun screenPosition(position: Offset): Offset {
                overlayView.getLocationOnScreen(location)
                return position + Offset(location[0].toFloat(), location[1].toFloat())
            }
            var lastScreenPosition = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = { lastScreenPosition = screenPosition(it) },
                onDragEnd = { currentOnDragFinished() },
                onDragCancel = { currentOnDragFinished() },
                onDrag = { change, _ ->
                    change.consume()
                    // Moving the WindowManager window changes local pointer
                    // coordinates. Screen deltas avoid subtracting that move
                    // again and making the HUD follow at half finger speed.
                    val position = screenPosition(change.position)
                    val amount = position - lastScreenPosition
                    lastScreenPosition = position
                    currentOnDrag(amount.x, amount.y)
                },
            )
        }
    } else if (mayMove) {
        Modifier.pointerInput(anchoredToBattleHud) {
            detectDragGestures(
                onDragEnd = { currentOnDragFinished() },
                onDrag = { change, amount ->
                    change.consume()
                    currentOnDrag(amount.x, amount.y)
                }
            )
        }
    } else Modifier
    Column(
        modifier = interactionModifier
            .offset(x = if (anchoredToBattleHud) 0.dp else arrivalOffset)
            .alpha(if (anchoredToBattleHud && !badgeSeen) 0f else 1f)
            .then(
                if (!anchoredToBattleHud) Modifier.clickable { DroidballOverlayPresentation.toggleExpanded() }
                else Modifier.clickable(onClickLabel = "Open session controls") {
                    sessionControlsOpen = !sessionControlsOpen
                }.semantics { contentDescription = "Battle HUD" }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (panelIsVisible) {
            if (!anchoredToBattleHud) {
                DroidballHalf(
                    top = true,
                    layoutEditing = layoutEditing,
                    offsetX = topHalfOffsetX,
                    offsetY = topHalfOffsetY,
                    onOffsetChanged = { x, y -> topHalfOffsetX = x; topHalfOffsetY = y },
                    onDragFinished = {
                        onHalfPositionsChanged(
                            topHalfOffsetX,
                            topHalfOffsetY,
                            bottomHalfOffsetX,
                            bottomHalfOffsetY,
                        )
                    },
                )
            }
            OverlayPanel(
                mode,
                diagnostics,
                opponentSpecies,
                opponentMoves,
                inferredPlayerTeam,
                playerTeamConfirmed,
                configuredPlayerTeam,
                panelWidthPx,
                sessionControlsOpen = sessionControlsOpen,
                onCloseSessionControls = { sessionControlsOpen = false },
                onExitIconCalibration = {
                    sessionControlsOpen = false
                    onExitIconCalibration()
                },
            )
            if (!anchoredToBattleHud) {
                DroidballHalf(
                    top = false,
                    layoutEditing = layoutEditing,
                    offsetX = bottomHalfOffsetX,
                    offsetY = bottomHalfOffsetY,
                    onOffsetChanged = { x, y -> bottomHalfOffsetX = x; bottomHalfOffsetY = y },
                    onDragFinished = {
                        onHalfPositionsChanged(
                            topHalfOffsetX,
                            topHalfOffsetY,
                            bottomHalfOffsetX,
                            bottomHalfOffsetY,
                        )
                    },
                )
            }
        } else {
            Image(
                painter = painterResource(R.drawable.droidball),
                contentDescription = "Droidball. Tap to open assistance.",
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun DroidballHalf(
    top: Boolean,
    layoutEditing: Boolean,
    offsetX: Float,
    offsetY: Float,
    onOffsetChanged: (Float, Float) -> Unit,
    onDragFinished: () -> Unit,
) {
    val splitHeight = if (top) {
        BattleHudOverlayGeometry.DROIDBALL_TOP_HALF_DP.dp
    } else {
        BattleHudOverlayGeometry.DROIDBALL_BOTTOM_HALF_DP.dp
    }
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentOnOffsetChanged by rememberUpdatedState(onOffsetChanged)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)
    val dragModifier = if (layoutEditing) {
        Modifier.pointerInput(top, layoutEditing) {
            detectDragGestures(
                onDragEnd = { currentOnDragFinished() },
                onDragCancel = { currentOnDragFinished() },
                onDrag = { change, amount ->
                    change.consume()
                    currentOnOffsetChanged(
                        currentOffsetX + amount.x,
                        currentOffsetY + amount.y,
                    )
                },
            )
        }
    } else Modifier
    Box(
        modifier = dragModifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(48.dp)
            .height(splitHeight)
            .then(if (layoutEditing) Modifier.border(1.dp, Color(0xFFB000FF)) else Modifier)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (top) {
            Image(
                painter = painterResource(R.drawable.droidball_top_shell),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        } else {
            // The lower shell begins at the upper edge of the equatorial band.
            Box(
                modifier = Modifier
                    .offset(y = BattleHudOverlayGeometry.DROIDBALL_BUTTON_UNDERBITE_DP.dp)
                    .width(48.dp)
                    .height(BattleHudOverlayGeometry.DROIDBALL_BOTTOM_BODY_DP.dp)
                    .clipToBounds()
            ) {
                Image(
                    painter = painterResource(R.drawable.droidball),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .offset(y = (-BattleHudOverlayGeometry.DROIDBALL_TOP_HALF_DP).dp)
                )
            }
            // The button starts above the band, so render it independently as
            // part of the lower shell instead of leaving its crown on the top.
            Image(
                painter = painterResource(R.drawable.droidball_center_button),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .offset(
                        y = -(
                            BattleHudOverlayGeometry.DROIDBALL_TOP_HALF_DP -
                                BattleHudOverlayGeometry.DROIDBALL_BUTTON_UNDERBITE_DP
                            ).dp
                    )
            )
        }
    }
}

@Composable
private fun OverlayPanel(
    mode: DroidballOverlayMode,
    diagnostics: CaptureDiagnostics,
    opponentSpecies: List<ObservedOpponentSpecies>,
    opponentMoves: OpponentMovePossibilities?,
    inferredPlayerTeam: List<String?>,
    playerTeamConfirmed: Boolean,
    configuredPlayerTeam: List<String>,
    panelWidthPx: Int,
    sessionControlsOpen: Boolean,
    onCloseSessionControls: () -> Unit,
    onExitIconCalibration: () -> Unit,
) {
    val teamEditingAllowed by DroidballOverlayPresentation.teamEditingAllowed.collectAsState()
    val isBattleHud = mode == DroidballOverlayMode.BATTLE_HUD || mode == DroidballOverlayMode.BATTLE_LIVE
    val heading = when (mode) {
        DroidballOverlayMode.PRE_BATTLE -> "NAVIGATION IDLE"
        DroidballOverlayMode.SEEKING_TEAM_SELECT -> "SEEKING TEAM SELECT"
        DroidballOverlayMode.TEAM_SELECT -> "TEAM SELECT ACTIVE"
        DroidballOverlayMode.CALIBRATING -> "CALIBRATION"
        DroidballOverlayMode.RESULT -> "RESULT OBSERVED"
        else -> "BATTLE HUD"
    }
    // Match the calibrated opponent badge exactly at every display width.
    val teamInfoWidth = with(LocalDensity.current) { panelWidthPx.toDp() }
    val jointDepth = with(LocalDensity.current) { BattleHudBadgeStyle.cornerPx(panelWidthPx.toFloat()).toDp() }
    val panelShape = if (isBattleHud) BattleHudBadgeShape else RoundedCornerShape(9.dp)
    val background = if (isBattleHud) BattleHudBadgeStyle.background else Color(0xE810231F)
    val foreground = if (isBattleHud) Color(0xFF275F6D) else Color(0xFFD7FFF4)
    val muted = if (isBattleHud) Color(0xFF397786) else Color(0xFFD7FFF4).copy(alpha = 0.82f)

    Column(
        modifier = Modifier.width(teamInfoWidth)
            .then(if (isBattleHud) Modifier.battleHudBadgeBackground() else Modifier.background(background, panelShape))
            .padding(start = 8.dp, end = 8.dp, bottom = if (isBattleHud) 2.dp else 6.dp,
                top = (if (isBattleHud) 2.dp else 6.dp) + if (isBattleHud) jointDepth else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (isBattleHud) 0.dp else 5.dp),
        horizontalAlignment = if (isBattleHud) Alignment.End else Alignment.Start,
    ) {
        if (teamEditingAllowed && !isBattleHud) {
            Text("TEAM / MOVES", color = foreground, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.EditTeamRequested)
                }.padding(vertical = 5.dp))
            Text("SCAN TEAM SELECT", color = foreground, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.ScanTeamSelectRequested)
                }.padding(vertical = 5.dp))
        }
        if (!isBattleHud) {
            Text(heading, color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        if (isBattleHud && sessionControlsOpen) {
            // A short tap exposes these controls only while requested. The
            // permanent HUD remains free of calibration labels and handles.
            Text("EXIT ICON BOX", color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onExitIconCalibration).padding(vertical = 10.dp))
            Text("CLOSE", color = foreground, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onCloseSessionControls).padding(vertical = 10.dp))
        } else if (isBattleHud) {
            val forecast by DroidballOverlayPresentation.opponentEnergyEstimate.collectAsState()
            val estimate = forecast?.takeIf { it.speciesName.equals(opponentMoves?.speciesName, ignoreCase = true) }
            // Preserve only the panel's outside padding. The three stock-like
            // inactive cards share their edges so every available pixel can
            // belong to the sprites.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                repeat(3) { index -> OpponentSpeciesCell(opponentSpecies.getOrNull(index)) }
            }
            if (opponentMoves != null) {
                if (opponentMoves.fastMoves.isNotEmpty()) {
                    MovePossibilityLine(opponentMoves.fastMoves, Color(0xFF276F76),
                        assumedMove = estimate?.takeUnless { it.moveIdentified }?.assumedMoveName)
                }
                if (opponentMoves.chargedMoves.isNotEmpty()) {
                    MovePossibilityLine(opponentMoves.chargedMoves, BattleHudBadgeStyle.moveInk,
                        energy = estimate?.energy,
                        fastGain = opponentMoves.fastMoves.firstOrNull {
                            it.name.equals(estimate?.assumedMoveName, ignoreCase = true)
                        }?.energy)
                }
            }
            OpponentHudStatus(estimate)

        } else {
            val configuredTeamReady = configuredPlayerTeam.size == 3
            val message = when (mode) {
                DroidballOverlayMode.PRE_BATTLE -> if (configuredTeamReady) {
                    "Team and moves ready. You can change them or rescan before battle."
                } else {
                    "Moves not ready. Open TEAM / MOVES, or scan when Team Select is visible."
                }
                DroidballOverlayMode.SEEKING_TEAM_SELECT -> "Looking for agreeing league, party-card, restriction, and use-party signals."
                DroidballOverlayMode.TEAM_SELECT -> "Team Select accepted. Player roster observation is active."
                DroidballOverlayMode.CALIBRATING -> "Adjust crop boxes in Overdex."
                DroidballOverlayMode.RESULT -> "Outcome evidence is preserved. Tap to arm the next match."
                else -> captureLine(diagnostics)
            }
            Text(
                text = message,
                modifier = if (mode == DroidballOverlayMode.RESULT) Modifier.clickable {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.BeginNextMatch)
                } else Modifier,
                color = foreground, fontSize = 9.sp, fontFamily = FontFamily.Monospace
            )
            if (mode == DroidballOverlayMode.PRE_BATTLE) {
                OverlayControl("START BATTLE HUD") {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.OpenBattleHudRequested)
                }
            }
            if (mode == DroidballOverlayMode.SEEKING_TEAM_SELECT) {
                OverlayControl("IGNORE CURRENT SCREEN") {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.IgnoreCurrentScreenRequested)
                }
                OverlayControl("RESTART OBSERVATION") {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.RestartObservationRequested)
                }
            }
            if (mode == DroidballOverlayMode.TEAM_SELECT) {
                if (inferredPlayerTeam.any { it != null }) {
                    Text(
                        inferredPlayerTeam.mapIndexed { index, species -> "${index + 1}. ${species ?: "?"}" }.joinToString("  "),
                        color = foreground,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (inferredPlayerTeam.all { it != null } && !playerTeamConfirmed) {
                    Text("IS THIS YOUR TEAM?", color = foreground, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OverlayControl("YES") { DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.ConfirmInferredPlayerTeamRequested) }
                        OverlayControl("NO / RESCAN") { DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.RejectInferredPlayerTeamRequested) }
                    }
                } else if (playerTeamConfirmed) {
                    Text(if (configuredTeamReady) "TEAM & MOVES READY" else "ROSTER CONFIRMED · MOVES NEEDED",
                        color = foreground, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    if (!configuredTeamReady) {
                        Text("Choose TEAM / MOVES to save moves, or continue with observation only.",
                            color = foreground, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Text(
                    text = "OPEN BATTLE HUD",
                    modifier = Modifier.clickable {
                        DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.OpenBattleHudRequested)
                    },
                    color = foreground,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                OverlayControl("RESTART OBSERVATION") {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.RestartObservationRequested)
                }
            }
            if (mode in setOf(
                    DroidballOverlayMode.PRE_BATTLE,
                    DroidballOverlayMode.SEEKING_TEAM_SELECT,
                    DroidballOverlayMode.TEAM_SELECT,
                    DroidballOverlayMode.CALIBRATING
                )
            ) {
                OverlayControl("EXIT ICON BOX", onExitIconCalibration)
                OverlayControl("STOP SESSION") {
                    DroidballService.emitSignal(com.example.overdex.battle.observation.DroidballSignal.StopSessionRequested)
                }
            }
        }
    }
}

@Composable
private fun OverlayControl(label: String, action: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.clickable(onClick = action),
        color = Color(0xFFD7FFF4),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun RowScope.OpponentSpeciesCell(species: ObservedOpponentSpecies?) {
    val context = LocalContext.current
    val assets = context.assets
    val spriteUrl = remember(assets, species?.speciesId) {
        species?.speciesId?.let { LocalSpriteProvider(assets).getSpriteUrl(it) }
            ?.takeIf(String::isNotBlank)
    }
    val spriteRequest = remember(context, spriteUrl) {
        spriteUrl?.let {
            ImageRequest.Builder(context).data(it).allowHardware(false)
                .transformations(HudSpriteTrimTransformation).build()
        }
    }
    Box(
        modifier = Modifier.weight(1f).height(44.dp)
            .background(Color(0x18005E5B), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x55005E5B), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (species == null || spriteUrl == null) {
            Text("?", color = Color(0xFF397D77), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } else {
            // GO's badge already names the active Pokémon. Use the entire card
            // for its picture, retaining the name for accessibility only.
            AsyncImage(
                model = spriteRequest,
                contentDescription = "${species.speciesName} sprite",
                modifier = Modifier.size(42.dp).alpha(if (species.isFainted) 0.35f else 1f)
                    .semantics { stateDescription = if (species.isFainted) "Fainted" else "Not fainted" },
                contentScale = ContentScale.Fit,
                colorFilter = if (species.isFainted) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else null
            )
        }
        if (species?.isFainted == true) {
            Text(
                "×",
                color = Color(0xFFD32F2F),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun MovePossibilityLine(
    moves: List<OverlayMovePossibility>, color: Color, assumedMove: String? = null,
    energy: Int? = null, fastGain: Int? = null,
) {
    moves.forEach { move ->
        val readiness = com.example.overdex.battle.observation.hudChargeReadiness(move.energy, energy, fastGain)
        val readinessColor = when (readiness) {
            com.example.overdex.battle.observation.HudChargeReadiness.READY -> Color(0xFFFF0000)
            com.example.overdex.battle.observation.HudChargeReadiness.ONE_FAST_MOVE_AWAY -> Color(0xFFFFA000)
            else -> color
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildAnnotatedString {
                    if (!move.isConfirmed && !move.isAudioFavored && move.name.equals(assumedMove, ignoreCase = true)) append("≈ ")
                    if (move.isConfirmed) append("✓ ")
                    if (move.hazardous) withStyle(SpanStyle(color = Color(0xFFC62828))) {
                        append("⚠ +${move.damageIncreasePercent}% ")
                    }
                    append(move.name)
                    if (move.isAudioFavored && !move.isConfirmed) append("\nFavored · low confidence")
                },
                modifier = Modifier.weight(1f).semantics {
                    stateDescription = buildString {
                        append(when {
                            move.isConfirmed -> "Confirmed move"
                            move.isAudioFavored -> "Audio favors this move; low confidence, not confirmed"
                            else -> "Possible move"
                        })
                        when (readiness) {
                            com.example.overdex.battle.observation.HudChargeReadiness.READY -> append("; estimated ready")
                            com.example.overdex.battle.observation.HudChargeReadiness.ONE_FAST_MOVE_AWAY -> append("; estimated one Fast Move away")
                            else -> Unit
                        }
                    }
                },
                color = readinessColor,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                textAlign = TextAlign.End,
                fontWeight = if (move.hazardous || move.isConfirmed || energy != null && readiness != com.example.overdex.battle.observation.HudChargeReadiness.BUILDING) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun captureLine(diagnostics: CaptureDiagnostics): String {
    val dimensions = diagnostics.width?.let { width ->
        diagnostics.height?.let { height -> "${width}x${height}" }
    } ?: "awaiting frame"
    return "Capture: ${diagnostics.state.lowercase()} ($dimensions)"
}
