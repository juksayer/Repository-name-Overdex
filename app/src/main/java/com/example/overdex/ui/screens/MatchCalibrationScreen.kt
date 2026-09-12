package com.example.overdex.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.example.overdex.CalibrationManager
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.CalibrationMode
import com.example.overdex.ui.components.CalibrationRegion
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import androidx.compose.ui.platform.LocalContext

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun MatchCalibrationPreview() {
    MatchCalibrationScreen(
        calibrationManager = CalibrationManager(LocalContext.current)
    )
}

@Composable
fun MatchCalibrationScreen(
    calibrationManager: CalibrationManager,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onSelect: (() -> Unit) -> Unit = {},
    onSelectLong: (() -> Unit) -> Unit = {},
    onStart: (() -> Unit) -> Unit = {},
    onLcdDrag: ((Offset) -> Unit) -> Unit = {},
    onLcdTap: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String?, String?) -> Unit = { _, _ -> }
) {
    var calibration by remember { mutableStateOf(calibrationManager.load()) }
    var selectedRegion by remember { mutableStateOf(CalibrationRegion.ENEMY_NAME) }
    var mode by remember { mutableStateOf(CalibrationMode.POSITION) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val context = LocalContext.current
    val samples = remember {
        context.assets.list("battle_samples")?.toList() ?: listOf("celluloid-shot0001.jpg")
    }
    var currentImageIndex by remember { mutableIntStateOf(0) }

    val matchRegions = remember {
        listOf(
            CalibrationRegion.ENEMY_NAME,
            CalibrationRegion.HP_BAR,
            CalibrationRegion.TEAM_ICONS,
            CalibrationRegion.MOVE_BANNER,
            CalibrationRegion.OPPONENT_SHIELDS,
            CalibrationRegion.YOU_WIN,
            CalibrationRegion.GOOD_EFFORT,
            CalibrationRegion.COUNTDOWN,
            CalibrationRegion.ANNOUNCEMENT,
            CalibrationRegion.TRAINER_TEAM_INFO,
            CalibrationRegion.OPPONENT_TEAM_INFO,
            CalibrationRegion.TRAINER_ACTIVE_TYPE,
            CalibrationRegion.OPPONENT_ACTIVE_TYPE,
            CalibrationRegion.TRAINER_HP,
            CalibrationRegion.OPPONENT_HP,
            CalibrationRegion.CHARGE_MOVE_EXECUTION,
            CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS,
            CalibrationRegion.TRAINER_INACTIVE_POKEMON,
            CalibrationRegion.MATCH_OUTCOME
        )
    }

    fun getReadableName(region: CalibrationRegion): String {
        return when (region) {
            CalibrationRegion.ENEMY_NAME -> "Enemy Name"
            CalibrationRegion.HP_BAR -> "HP Bar"
            CalibrationRegion.TEAM_ICONS -> "Team Icons"
            CalibrationRegion.MOVE_BANNER -> "Move Banner"
            CalibrationRegion.OPPONENT_SHIELDS -> "Opponent Shields"
            CalibrationRegion.YOU_WIN -> "You Win"
            CalibrationRegion.GOOD_EFFORT -> "Good Effort"
            CalibrationRegion.COUNTDOWN -> "Countdown"
            CalibrationRegion.ANNOUNCEMENT -> "Announcement"
            CalibrationRegion.TRAINER_TEAM_INFO -> "Trainer Team Info"
            CalibrationRegion.OPPONENT_TEAM_INFO -> "Opponent Team Info"
            CalibrationRegion.TRAINER_ACTIVE_TYPE -> "Trainer Active Type"
            CalibrationRegion.OPPONENT_ACTIVE_TYPE -> "Opponent Active Type"
            CalibrationRegion.TRAINER_HP -> "Trainer HP"
            CalibrationRegion.OPPONENT_HP -> "Opponent HP"
            CalibrationRegion.CHARGE_MOVE_EXECUTION -> "Charge-Move Execution"
            CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS -> "Trainer Charge-Move Controls"
            CalibrationRegion.TRAINER_INACTIVE_POKEMON -> "Trainer Inactive Pokémon"
            CalibrationRegion.MATCH_OUTCOME -> "Match Outcome"
            else -> region.name
        }
    }

    val activeRegion = when (selectedRegion) {
        CalibrationRegion.ENEMY_NAME -> calibration.enemyNameRegion
        CalibrationRegion.HP_BAR -> calibration.hpBarRegion
        CalibrationRegion.TEAM_ICONS -> calibration.teamIconsRegion
        CalibrationRegion.MOVE_BANNER -> calibration.moveBannerRegion
        CalibrationRegion.OPPONENT_SHIELDS -> calibration.opponentShieldsRegion
        CalibrationRegion.YOU_WIN -> calibration.youWinRegion
        CalibrationRegion.GOOD_EFFORT -> calibration.goodEffortRegion
        CalibrationRegion.COUNTDOWN -> calibration.countdownRegion
        CalibrationRegion.ANNOUNCEMENT -> calibration.announcementRegion
        CalibrationRegion.TRAINER_TEAM_INFO -> calibration.playerTeamInfoRegion
        CalibrationRegion.OPPONENT_TEAM_INFO -> calibration.opponentTeamInfoRegion
        CalibrationRegion.TRAINER_ACTIVE_TYPE -> calibration.trainerActiveTypeRegion
        CalibrationRegion.OPPONENT_ACTIVE_TYPE -> calibration.opponentActiveTypeRegion
        CalibrationRegion.TRAINER_HP -> calibration.trainerHpRegion
        CalibrationRegion.OPPONENT_HP -> calibration.opponentHpRegion
        CalibrationRegion.CHARGE_MOVE_EXECUTION -> calibration.chargeMoveExecutionRegion
        CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS -> calibration.trainerChargeMoveControlsRegion
        CalibrationRegion.TRAINER_INACTIVE_POKEMON -> calibration.trainerInactivePokemonRegion
        CalibrationRegion.MATCH_OUTCOME -> calibration.matchOutcomeRegion
        else -> calibration.enemyNameRegion
    }

    fun updateCalibration(updated: AnchorRegion) {
        calibration = when (selectedRegion) {
            CalibrationRegion.ENEMY_NAME -> calibration.copy(enemyNameRegion = updated)
            CalibrationRegion.HP_BAR -> calibration.copy(hpBarRegion = updated)
            CalibrationRegion.TEAM_ICONS -> calibration.copy(teamIconsRegion = updated)
            CalibrationRegion.MOVE_BANNER -> calibration.copy(moveBannerRegion = updated)
            CalibrationRegion.OPPONENT_SHIELDS -> calibration.copy(opponentShieldsRegion = updated)
            CalibrationRegion.YOU_WIN -> calibration.copy(youWinRegion = updated)
            CalibrationRegion.GOOD_EFFORT -> calibration.copy(goodEffortRegion = updated)
            CalibrationRegion.COUNTDOWN -> calibration.copy(countdownRegion = updated)
            CalibrationRegion.ANNOUNCEMENT -> calibration.copy(announcementRegion = updated)
            CalibrationRegion.TRAINER_TEAM_INFO -> calibration.copy(playerTeamInfoRegion = updated)
            CalibrationRegion.OPPONENT_TEAM_INFO -> calibration.copy(opponentTeamInfoRegion = updated)
            CalibrationRegion.TRAINER_ACTIVE_TYPE -> calibration.copy(trainerActiveTypeRegion = updated)
            CalibrationRegion.OPPONENT_ACTIVE_TYPE -> calibration.copy(opponentActiveTypeRegion = updated)
            CalibrationRegion.TRAINER_HP -> calibration.copy(trainerHpRegion = updated)
            CalibrationRegion.OPPONENT_HP -> calibration.copy(opponentHpRegion = updated)
            CalibrationRegion.CHARGE_MOVE_EXECUTION -> calibration.copy(chargeMoveExecutionRegion = updated)
            CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS -> calibration.copy(trainerChargeMoveControlsRegion = updated)
            CalibrationRegion.TRAINER_INACTIVE_POKEMON -> calibration.copy(trainerInactivePokemonRegion = updated)
            CalibrationRegion.MATCH_OUTCOME -> calibration.copy(matchOutcomeRegion = updated)
            else -> calibration
        }
        calibrationManager.save(calibration)
    }

    val step = 0.005f // Small increment for normalized coordinates

    fun move(dx: Float, dy: Float) {
        val maxX = (1f - activeRegion.width).coerceAtLeast(0f)
        val maxY = (1f - activeRegion.height).coerceAtLeast(0f)
        updateCalibration(
            activeRegion.copy(
                x = (activeRegion.x + dx).coerceIn(0f, maxX),
                y = (activeRegion.y + dy).coerceIn(0f, maxY)
            )
        )
    }

    fun resize(dw: Float, dh: Float) {
        val maxWidth = (1f - activeRegion.x).coerceAtLeast(0.01f)
        val maxHeight = (1f - activeRegion.y).coerceAtLeast(0.01f)
        updateCalibration(
            activeRegion.copy(
                width = (activeRegion.width + dw).coerceIn(0.01f, maxWidth),
                height = (activeRegion.height + dh).coerceIn(0.01f, maxHeight)
            )
        )
    }

    // Input Handling
    SideEffect {
        onUp { if (mode == CalibrationMode.POSITION) move(0f, -step) else resize(0f, -step) }
        onDown { if (mode == CalibrationMode.POSITION) move(0f, step) else resize(0f, step) }
        onLeft { if (mode == CalibrationMode.POSITION) move(-step, 0f) else resize(-step, 0f) }
        onRight { if (mode == CalibrationMode.POSITION) move(step, 0f) else resize(step, 0f) }
        onA {
            val currentIndex = matchRegions.indexOf(selectedRegion)
            selectedRegion = matchRegions[(currentIndex + 1) % matchRegions.size]
        }
        onSelect {
            mode = if (mode == CalibrationMode.POSITION) CalibrationMode.SIZE else CalibrationMode.POSITION
        }
        onSelectLong {
            currentImageIndex = (currentImageIndex + 1) % samples.size
        }
        onStart { /* No-op as per Work Order */ }
        onLcdDrag { delta ->
            val dx = delta.x / 1000f 
            val dy = delta.y / 1000f
            if (mode == CalibrationMode.POSITION) move(dx, dy) else resize(dx, dy)
        }
        onLcdTap {
            val currentIndex = matchRegions.indexOf(selectedRegion)
            selectedRegion = matchRegions[(currentIndex + 1) % matchRegions.size]
        }
    }

    // LCD Update
    LaunchedEffect(selectedRegion, mode) {
        val indexText = "${matchRegions.indexOf(selectedRegion) + 1}/${matchRegions.size}"
        onLcdUpdate("${getReadableName(selectedRegion)} ($indexText)", "MODE: ${mode.name}")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size.toSize() }
    ) {
        // Reference Image
        AsyncImage(
            model = "file:///android_asset/battle_samples/${samples[currentImageIndex]}",
            contentDescription = "Calibration Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Selected Region Overlay (Draw ONLY the selected region's rectangle)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val color = TerminalPurple
            val stroke = 3.dp.toPx()

            drawRect(
                color = color,
                topLeft = Offset(activeRegion.x * size.width, activeRegion.y * size.height),
                size = Size(activeRegion.width * size.width, activeRegion.height * size.height),
                style = Stroke(width = stroke)
            )
        }
        
        // HUD feedback in CRT
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            val indexNum = matchRegions.indexOf(selectedRegion) + 1
            TerminalText("CALIBRATION WORKSPACE", color = TerminalPurple, fontSize = 10.sp)
            TerminalText("TARGET: ${getReadableName(selectedRegion)} ($indexNum/${matchRegions.size})", fontSize = 10.sp)
            TerminalText("MODE: ${mode.name}", fontSize = 10.sp)
            if (selectedRegion == CalibrationRegion.MATCH_OUTCOME) {
                TerminalText("STATUS: Needs calibration", color = Color.Yellow, fontSize = 9.sp)
            }
        }
    }
}
