package com.example.overdex.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
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
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.overdex.data.ScreenshotDirectoryManager

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
    onALong: (() -> Unit) -> Unit = {},
    onSelect: (() -> Unit) -> Unit = {},
    onSelectLong: (() -> Unit) -> Unit = {},
    onLcdDrag: ((Offset) -> Unit) -> Unit = {},
    onLcdTap: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String?, String?) -> Unit = { _, _ -> }
) {
    var calibration by remember { mutableStateOf(calibrationManager.load()) }
    var selectedRegion by remember { mutableStateOf(CalibrationRegion.COUNTDOWN) }
    var mode by remember { mutableStateOf(CalibrationMode.POSITION) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var showLcdTouchHint by rememberSaveable { mutableStateOf(true) }

    val context = LocalContext.current
    val screenshotDirectory = remember(context) { ScreenshotDirectoryManager(context) }
    var userSamples by remember { mutableStateOf(screenshotDirectory.imageUris()) }
    val bundledSamples = remember {
        context.assets.list("battle_samples")?.toList() ?: listOf("celluloid-shot0001.jpg")
    }
    val samples: List<Any> = if (userSamples.isNotEmpty()) userSamples else bundledSamples
    val screenshotSourceName = remember(userSamples) { screenshotDirectory.displayName() }
    var currentImageIndex by remember { mutableIntStateOf(0) }
    var sourceFrameSize by remember { mutableStateOf(IntSize.Zero) }
    val screenshotFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                screenshotDirectory.saveFolder(uri)
                userSamples = screenshotDirectory.imageUris()
                currentImageIndex = 0
            }
        }
    }

    LaunchedEffect(samples.size) {
        currentImageIndex = currentImageIndex.coerceIn(0, (samples.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(samples.getOrNull(currentImageIndex)) {
        val sample = samples.getOrNull(currentImageIndex)
        sourceFrameSize = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                when (sample) {
                    is String -> context.assets.open("battle_samples/$sample").use { BitmapFactory.decodeStream(it, null, options) }
                    is Uri -> context.contentResolver.openInputStream(sample)?.use { BitmapFactory.decodeStream(it, null, options) }
                }
                IntSize(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
            }.getOrDefault(IntSize.Zero)
        }
    }

    val matchRegions = remember {
        listOf(
            CalibrationRegion.COUNTDOWN,
            CalibrationRegion.VS_SCREEN,
            CalibrationRegion.ANNOUNCEMENT,
            CalibrationRegion.TRAINER_TEAM_INFO,
            CalibrationRegion.OPPONENT_TEAM_INFO,
            CalibrationRegion.OPPONENT_SPECIES_NAME,
            CalibrationRegion.OPPONENT_SHIELDS,
            CalibrationRegion.OPPONENT_POKE_BALLS,
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
            CalibrationRegion.VS_SCREEN -> "VS Screen"
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
            // Pokémon GO shows these two labels together only on the
            // post-match screen. This is not a generic game-menu crop.
            CalibrationRegion.BATTLE_PARTY_TABS -> "Post-Match Battle / Party Selector"
            CalibrationRegion.OUT_OF_BATTLE_MENU -> "Post-Match Menu Support"
            CalibrationRegion.OPPONENT_SPECIES_NAME -> "Opponent Species Name"
            CalibrationRegion.OPPONENT_POKE_BALLS -> "Opponent Poké Balls"
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
        CalibrationRegion.VS_SCREEN -> calibration.vsScreenRegion
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
        CalibrationRegion.BATTLE_PARTY_TABS -> calibration.battlePartyTabsRegion
        CalibrationRegion.OUT_OF_BATTLE_MENU -> calibration.outOfBattleMenuRegion
        CalibrationRegion.OPPONENT_SPECIES_NAME -> calibration.opponentSpeciesNameRegion
        CalibrationRegion.OPPONENT_POKE_BALLS -> calibration.opponentPokeBallsRegion
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
            CalibrationRegion.VS_SCREEN -> calibration.copy(vsScreenRegion = updated)
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
            CalibrationRegion.BATTLE_PARTY_TABS -> calibration.copy(battlePartyTabsRegion = updated)
            CalibrationRegion.OUT_OF_BATTLE_MENU -> calibration.copy(outOfBattleMenuRegion = updated)
            CalibrationRegion.OPPONENT_SPECIES_NAME -> calibration.copy(opponentSpeciesNameRegion = updated)
            CalibrationRegion.OPPONENT_POKE_BALLS -> calibration.copy(opponentPokeBallsRegion = updated)
            else -> calibration
        }
        calibrationManager.save(calibration)
        if (selectedRegion == CalibrationRegion.MATCH_OUTCOME) {
            calibrationManager.recordMatchOutcomeCalibration()
        }
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
        onALong {
            screenshotFolderPicker.launch(screenshotDirectory.folderUri())
        }
        onSelect {
            mode = if (mode == CalibrationMode.POSITION) CalibrationMode.SIZE else CalibrationMode.POSITION
        }
        onSelectLong {
            currentImageIndex = (currentImageIndex + 1) % samples.size
        }
        onLcdDrag { delta ->
            showLcdTouchHint = false
            val dx = if (containerSize.width > 0f) delta.x / containerSize.width else 0f
            val dy = if (containerSize.height > 0f) delta.y / containerSize.height else 0f
            if (mode == CalibrationMode.POSITION) move(dx, dy) else resize(dx, dy)
        }
        onLcdTap {
            val currentIndex = matchRegions.indexOf(selectedRegion)
            selectedRegion = matchRegions[(currentIndex + 1) % matchRegions.size]
        }
    }

    // LCD Update
    LaunchedEffect(
        selectedRegion, mode, showLcdTouchHint, screenshotSourceName, sourceFrameSize,
        activeRegion.x, activeRegion.y, activeRegion.width, activeRegion.height
    ) {
        val indexText = "${matchRegions.indexOf(selectedRegion) + 1}/${matchRegions.size}"
        val sourceWidth = sourceFrameSize.width
        val sourceHeight = sourceFrameSize.height
        val x = (activeRegion.x * sourceWidth).toInt()
        val y = (activeRegion.y * sourceHeight).toInt()
        val width = (activeRegion.width * sourceWidth).toInt()
        val height = (activeRegion.height * sourceHeight).toInt()
        onLcdUpdate(
            "${getReadableName(selectedRegion)} $indexText  X:$x Y:$y",
            "W:$width H:$height / ${sourceWidth}×${sourceHeight}  ${mode.name}"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size.toSize() }
    ) {
        // Reference Image
        AsyncImage(
            model = samples.getOrNull(currentImageIndex)?.let { sample ->
                if (sample is String) "file:///android_asset/battle_samples/$sample" else sample
            },
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
        
    }
}
