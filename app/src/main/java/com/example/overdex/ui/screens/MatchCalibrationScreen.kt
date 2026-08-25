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
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.example.overdex.CalibrationManager
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.CalibrationMode
import com.example.overdex.ui.components.CalibrationRegion
import com.example.overdex.ui.components.TerminalText
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple

@Composable
fun MatchCalibrationScreen(
    calibrationManager: CalibrationManager,
    onUp: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onSelect: (() -> Unit) -> Unit = {},
    onStart: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String?, String?) -> Unit = { _, _ -> }
) {
    var calibration by remember { mutableStateOf(calibrationManager.load()) }
    var selectedRegion by remember { mutableStateOf(CalibrationRegion.ENEMY_NAME) }
    var mode by remember { mutableStateOf(CalibrationMode.POSITION) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val matchRegions = remember {
        listOf(
            CalibrationRegion.ENEMY_NAME,
            CalibrationRegion.MOVE_BANNER,
            CalibrationRegion.HP_BAR,
            CalibrationRegion.TEAM_ICONS
        )
    }

    val activeRegion = when (selectedRegion) {
        CalibrationRegion.ENEMY_NAME -> calibration.enemyNameRegion
        CalibrationRegion.MOVE_BANNER -> calibration.moveBannerRegion
        CalibrationRegion.HP_BAR -> calibration.hpBarRegion
        CalibrationRegion.TEAM_ICONS -> calibration.teamIconsRegion
        else -> calibration.enemyNameRegion
    }

    fun updateCalibration(updated: AnchorRegion) {
        calibration = when (selectedRegion) {
            CalibrationRegion.ENEMY_NAME -> calibration.copy(enemyNameRegion = updated)
            CalibrationRegion.MOVE_BANNER -> calibration.copy(moveBannerRegion = updated)
            CalibrationRegion.HP_BAR -> calibration.copy(hpBarRegion = updated)
            CalibrationRegion.TEAM_ICONS -> calibration.copy(teamIconsRegion = updated)
            else -> calibration
        }
        calibrationManager.save(calibration)
    }

    val step = 0.005f // Small increment for normalized coordinates

    fun move(dx: Float, dy: Float) {
        updateCalibration(
            activeRegion.copy(
                x = (activeRegion.x + dx).coerceIn(0f, 1f - activeRegion.width),
                y = (activeRegion.y + dy).coerceIn(0f, 1f - activeRegion.height)
            )
        )
    }

    fun resize(dw: Float, dh: Float) {
        updateCalibration(
            activeRegion.copy(
                width = (activeRegion.width + dw).coerceIn(0.01f, 1f - activeRegion.x),
                height = (activeRegion.height + dh).coerceIn(0.01f, 1f - activeRegion.y)
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
        onStart { /* No-op as per Work Order */ }
    }

    // LCD Update
    LaunchedEffect(selectedRegion, mode) {
        onLcdUpdate("REGION: ${selectedRegion.name}", "MODE: ${mode.name}")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size.toSize() }
    ) {
        // Reference Image
        AsyncImage(
            model = "file:///android_asset/battle_samples/celluloid-shot0001.jpg",
            contentDescription = "Calibration Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Region Overlays
        Canvas(modifier = Modifier.fillMaxSize()) {
            matchRegions.forEach { regionType ->
                val region = when (regionType) {
                    CalibrationRegion.ENEMY_NAME -> calibration.enemyNameRegion
                    CalibrationRegion.MOVE_BANNER -> calibration.moveBannerRegion
                    CalibrationRegion.HP_BAR -> calibration.hpBarRegion
                    CalibrationRegion.TEAM_ICONS -> calibration.teamIconsRegion
                    else -> null
                }

                region?.let {
                    val isSelected = regionType == selectedRegion
                    val color = if (isSelected) TerminalPurple else TerminalGreen
                    val stroke = if (isSelected) 3.dp.toPx() else 1.dp.toPx()

                    drawRect(
                        color = color,
                        topLeft = Offset(it.x * size.width, it.y * size.height),
                        size = Size(it.width * size.width, it.height * size.height),
                        style = Stroke(width = stroke)
                    )
                }
            }
        }
        
        // HUD feedback in CRT
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            TerminalText("CALIBRATION WORKSPACE", color = TerminalPurple, fontSize = 10.sp)
            TerminalText("TARGET: ${selectedRegion.name}", fontSize = 10.sp)
            TerminalText("MODE: ${mode.name}", fontSize = 10.sp)
        }
    }
}
