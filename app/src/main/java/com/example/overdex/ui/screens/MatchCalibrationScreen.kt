package com.example.overdex.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.overdex.data.MatchCalibrationProfileStore
import com.example.overdex.data.MatchCalibrationProfileSummary
import com.example.overdex.battle.observation.TeamSelectCalibration
import com.example.overdex.battle.observation.TeamSelectCalibrationStore
import com.example.overdex.ui.theme.TerminalBlack
import android.os.Build

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
    onUpLong: (() -> Unit) -> Unit = {},
    onDown: (() -> Unit) -> Unit = {},
    onDownLong: (() -> Unit) -> Unit = {},
    onLeft: (() -> Unit) -> Unit = {},
    onLeftLong: (() -> Unit) -> Unit = {},
    onRight: (() -> Unit) -> Unit = {},
    onRightLong: (() -> Unit) -> Unit = {},
    onA: (() -> Unit) -> Unit = {},
    onALong: (() -> Unit) -> Unit = {},
    onSelect: (() -> Unit) -> Unit = {},
    onSelectLong: (() -> Unit) -> Unit = {},
    onStart: (() -> Unit) -> Unit = {},
    onLcdDrag: ((Offset) -> Unit) -> Unit = {},
    onLcdTap: (() -> Unit) -> Unit = {},
    onLcdUpdate: (String?, String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val deviceScreenSize = remember(context) {
        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.let { IntSize(it.width(), it.height()) }
        } else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics().also(windowManager.defaultDisplay::getRealMetrics)
            IntSize(metrics.widthPixels, metrics.heightPixels)
        }
    }
    val detectedDeviceModel = remember {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { "Android device" }
    }
    val detectedScreenWidth = deviceScreenSize.width.takeIf { it > 0 } ?: 1080
    val detectedScreenHeight = deviceScreenSize.height.takeIf { it > 0 } ?: 2400
    val teamSelectCalibrationStore = remember(context) { TeamSelectCalibrationStore(context) }
    val profileStore = remember(context) { MatchCalibrationProfileStore(context) }
    var calibration by remember { mutableStateOf(calibrationManager.load()) }
    var teamSelectCalibration by remember { mutableStateOf(teamSelectCalibrationStore.load()) }
    var activeProfile by remember { mutableStateOf<MatchCalibrationProfileSummary?>(profileStore.activeSummary()) }
    val activeProfileMatchesDetectedDevice = activeProfile?.let {
        it.deviceModel.equals(detectedDeviceModel, ignoreCase = true) &&
            it.screenWidth == detectedScreenWidth && it.screenHeight == detectedScreenHeight
    } == true
    var deviceGuessOffered by rememberSaveable { mutableStateOf(false) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var profileModel by remember { mutableStateOf("") }
    var profileWidth by remember { mutableStateOf("") }
    var profileHeight by remember { mutableStateOf("") }
    var profileSaveError by remember { mutableStateOf<String?>(null) }
    var selectedRegion by remember { mutableStateOf(CalibrationRegion.COUNTDOWN) }
    var mode by remember { mutableStateOf(CalibrationMode.POSITION) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var showLcdTouchHint by rememberSaveable { mutableStateOf(true) }
    var battleProfileSaved by remember { mutableStateOf(calibrationManager.hasSavedProfile()) }
    var teamSelectProfileSaved by remember { mutableStateOf(teamSelectCalibrationStore.hasSavedProfile()) }
    var battleSaveFailed by remember { mutableStateOf(false) }
    var teamSelectSaveFailed by remember { mutableStateOf(false) }
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
            CalibrationRegion.TEAM_SELECT_LEAGUE_BADGE,
            CalibrationRegion.TEAM_SELECT_LEAGUE_TEXT,
            CalibrationRegion.TEAM_SELECT_RESTRICTIONS,
            CalibrationRegion.TEAM_SELECT_USE_THIS_PARTY,
            CalibrationRegion.TEAM_SELECT_ROSTER_1,
            CalibrationRegion.TEAM_SELECT_ROSTER_2,
            CalibrationRegion.TEAM_SELECT_ROSTER_3,
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_1,
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_2,
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_3,
            CalibrationRegion.VS_SCREEN,
            CalibrationRegion.ANNOUNCEMENT,
            CalibrationRegion.TRAINER_TEAM_INFO,
            CalibrationRegion.OPPONENT_TEAM_INFO,
            CalibrationRegion.PLAYER_SPECIES_NAME,
            CalibrationRegion.OPPONENT_SPECIES_NAME,
            CalibrationRegion.OPPONENT_SHIELDS,
            CalibrationRegion.PLAYER_POKE_BALLS,
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
            CalibrationRegion.PLAYER_SPECIES_NAME -> "Player Species Name"
            CalibrationRegion.TRAINER_ACTIVE_TYPE -> "Trainer Active Type"
            CalibrationRegion.OPPONENT_ACTIVE_TYPE -> "Opponent Active Type"
            CalibrationRegion.TRAINER_HP -> "Trainer HP"
            CalibrationRegion.OPPONENT_HP -> "Opponent HP"
            CalibrationRegion.CHARGE_MOVE_EXECUTION -> "Charge-Move Execution"
            CalibrationRegion.TRAINER_CHARGE_MOVE_CONTROLS -> "Trainer Charge-Move Controls"
            CalibrationRegion.TRAINER_INACTIVE_POKEMON -> "Inactive Cards: Upper / Lower"
            CalibrationRegion.MATCH_OUTCOME -> "Match Outcome"
            // Pokémon GO shows these two labels together only on the
            // post-match screen. This is not a generic game-menu crop.
            CalibrationRegion.BATTLE_PARTY_TABS -> "Post-Match Battle / Party Selector"
            CalibrationRegion.OUT_OF_BATTLE_MENU -> "Post-Match Menu Support"
            CalibrationRegion.OPPONENT_SPECIES_NAME -> "Opponent Species Name"
            CalibrationRegion.PLAYER_POKE_BALLS -> "Player Poké Balls"
            CalibrationRegion.OPPONENT_POKE_BALLS -> "Opponent Poké Balls"
            CalibrationRegion.TEAM_SELECT_LEAGUE_BADGE -> "Team Select League Badge"
            CalibrationRegion.TEAM_SELECT_LEAGUE_TEXT -> "Team Select League Text"
            CalibrationRegion.TEAM_SELECT_RESTRICTIONS -> "Team Select Restrictions"
            CalibrationRegion.TEAM_SELECT_USE_THIS_PARTY -> "Team Select Use This Party"
            CalibrationRegion.TEAM_SELECT_ROSTER_1 -> "Team Select Player Card 1"
            CalibrationRegion.TEAM_SELECT_ROSTER_2 -> "Team Select Player Card 2"
            CalibrationRegion.TEAM_SELECT_ROSTER_3 -> "Team Select Player Card 3"
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_1 -> "Team Select Player Name 1"
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_2 -> "Team Select Player Name 2"
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_3 -> "Team Select Player Name 3"
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
        CalibrationRegion.PLAYER_SPECIES_NAME -> calibration.playerSpeciesNameRegion
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
        CalibrationRegion.PLAYER_POKE_BALLS -> calibration.playerPokeBallsRegion
        CalibrationRegion.OPPONENT_POKE_BALLS -> calibration.opponentPokeBallsRegion
        CalibrationRegion.TEAM_SELECT_LEAGUE_BADGE -> teamSelectCalibration.leagueBadge
        CalibrationRegion.TEAM_SELECT_LEAGUE_TEXT -> teamSelectCalibration.leagueText
        CalibrationRegion.TEAM_SELECT_RESTRICTIONS -> teamSelectCalibration.restrictions
        CalibrationRegion.TEAM_SELECT_USE_THIS_PARTY -> teamSelectCalibration.useThisParty
        CalibrationRegion.TEAM_SELECT_ROSTER_1 -> teamSelectCalibration.rosterSlot1
        CalibrationRegion.TEAM_SELECT_ROSTER_2 -> teamSelectCalibration.rosterSlot2
        CalibrationRegion.TEAM_SELECT_ROSTER_3 -> teamSelectCalibration.rosterSlot3
        CalibrationRegion.TEAM_SELECT_ROSTER_NAME_1 -> teamSelectCalibration.rosterName1
        CalibrationRegion.TEAM_SELECT_ROSTER_NAME_2 -> teamSelectCalibration.rosterName2
        CalibrationRegion.TEAM_SELECT_ROSTER_NAME_3 -> teamSelectCalibration.rosterName3
        else -> calibration.enemyNameRegion
    }

    fun updateCalibration(updated: AnchorRegion) {
        val teamSelectUpdate = when (selectedRegion) {
            CalibrationRegion.TEAM_SELECT_LEAGUE_BADGE -> teamSelectCalibration.copy(leagueBadge = updated)
            CalibrationRegion.TEAM_SELECT_LEAGUE_TEXT -> teamSelectCalibration.copy(leagueText = updated)
            CalibrationRegion.TEAM_SELECT_RESTRICTIONS -> teamSelectCalibration.copy(restrictions = updated)
            CalibrationRegion.TEAM_SELECT_USE_THIS_PARTY -> teamSelectCalibration.copy(useThisParty = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_1 -> teamSelectCalibration.copy(rosterSlot1 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_2 -> teamSelectCalibration.copy(rosterSlot2 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_3 -> teamSelectCalibration.copy(rosterSlot3 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_1 -> teamSelectCalibration.copy(rosterName1 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_2 -> teamSelectCalibration.copy(rosterName2 = updated)
            CalibrationRegion.TEAM_SELECT_ROSTER_NAME_3 -> teamSelectCalibration.copy(rosterName3 = updated)
            else -> null
        }
        if (teamSelectUpdate != null) {
            teamSelectCalibration = teamSelectUpdate
            teamSelectProfileSaved = teamSelectCalibrationStore.save(
                teamSelectUpdate,
                publishedWidth = sourceFrameSize.width.takeIf { it > 0 } ?: 1080,
                publishedHeight = sourceFrameSize.height.takeIf { it > 0 } ?: 2400
            )
            teamSelectSaveFailed = !teamSelectProfileSaved
            activeProfile = profileStore.activeSummary()
            return
        }
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
            CalibrationRegion.PLAYER_SPECIES_NAME -> calibration.copy(playerSpeciesNameRegion = updated)
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
            CalibrationRegion.PLAYER_POKE_BALLS -> calibration.copy(playerPokeBallsRegion = updated)
            CalibrationRegion.OPPONENT_POKE_BALLS -> calibration.copy(opponentPokeBallsRegion = updated)
            else -> calibration
        }
        battleProfileSaved = calibrationManager.save(calibration)
        battleSaveFailed = !battleProfileSaved
        activeProfile = profileStore.activeSummary()
        if (selectedRegion == CalibrationRegion.MATCH_OUTCOME) {
            calibrationManager.recordMatchOutcomeCalibration()
        }
    }

    val step = 0.005f // Fine adjustment: 0.5% of the published frame.
    val acceleratedStep = step * 4f

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

    fun openSaveProfileDialog() {
        val matchingActiveProfile = activeProfile?.takeIf {
            it.deviceModel.equals(detectedDeviceModel, ignoreCase = true) &&
                it.screenWidth == detectedScreenWidth && it.screenHeight == detectedScreenHeight
        }
        // An active profile can arrive from another phone through restored app
        // data. On a new device, offer Android's current full-display bounds
        // instead of silently carrying the former phone's dimensions forward.
        profileModel = matchingActiveProfile?.deviceModel ?: detectedDeviceModel
        profileWidth = (matchingActiveProfile?.screenWidth ?: detectedScreenWidth).toString()
        profileHeight = (matchingActiveProfile?.screenHeight ?: detectedScreenHeight).toString()
        profileName = matchingActiveProfile?.name
            ?: "$detectedDeviceModel ${detectedScreenWidth}x$detectedScreenHeight"
        profileSaveError = null
        showSaveProfileDialog = true
    }

    fun useDetectedDeviceGuess() {
        profileModel = detectedDeviceModel
        profileWidth = detectedScreenWidth.toString()
        profileHeight = detectedScreenHeight.toString()
        profileName = "$detectedDeviceModel ${detectedScreenWidth}x$detectedScreenHeight"
        profileSaveError = null
    }

    fun saveNamedProfile() {
        val width = profileWidth.toIntOrNull()
        val height = profileHeight.toIntOrNull()
        if (profileName.isBlank() || profileModel.isBlank() || width == null || height == null ||
            width <= 0 || height <= 0
        ) {
            profileSaveError = "Enter a name, model, width, and height."
            return
        }
        val saved = profileStore.saveAs(
            name = profileName,
            deviceModel = profileModel,
            screenWidth = width,
            screenHeight = height,
            battleCalibration = calibration,
            teamSelectCalibration = teamSelectCalibration
        )
        if (saved == null) {
            profileSaveError = "The profile could not be saved."
            return
        }
        activeProfile = MatchCalibrationProfileSummary(
            saved.id, saved.name, saved.deviceModel, saved.screenWidth, saved.screenHeight, saved.savedAtMillis
        )
        battleProfileSaved = calibrationManager.save(calibration)
        teamSelectProfileSaved = teamSelectCalibrationStore.save(teamSelectCalibration, width, height)
        battleSaveFailed = !battleProfileSaved
        teamSelectSaveFailed = !teamSelectProfileSaved
        if (battleSaveFailed || teamSelectSaveFailed) {
            profileSaveError = "The profile was saved, but its device settings could not be updated. Please retry."
            return
        }
        showSaveProfileDialog = false
    }

    LaunchedEffect(
        activeProfile?.id,
        detectedDeviceModel,
        detectedScreenWidth,
        detectedScreenHeight,
    ) {
        if (!deviceGuessOffered && !activeProfileMatchesDetectedDevice) {
            deviceGuessOffered = true
            openSaveProfileDialog()
        }
    }

    // Input Handling
    SideEffect {
        onUp { if (mode == CalibrationMode.POSITION) move(0f, -step) else resize(0f, -step) }
        onUpLong { if (mode == CalibrationMode.POSITION) move(0f, -acceleratedStep) else resize(0f, -acceleratedStep) }
        onDown { if (mode == CalibrationMode.POSITION) move(0f, step) else resize(0f, step) }
        onDownLong { if (mode == CalibrationMode.POSITION) move(0f, acceleratedStep) else resize(0f, acceleratedStep) }
        onLeft { if (mode == CalibrationMode.POSITION) move(-step, 0f) else resize(-step, 0f) }
        onLeftLong { if (mode == CalibrationMode.POSITION) move(-acceleratedStep, 0f) else resize(-acceleratedStep, 0f) }
        onRight { if (mode == CalibrationMode.POSITION) move(step, 0f) else resize(step, 0f) }
        onRightLong { if (mode == CalibrationMode.POSITION) move(acceleratedStep, 0f) else resize(acceleratedStep, 0f) }
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
        onStart { openSaveProfileDialog() }
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
        battleProfileSaved, teamSelectProfileSaved, battleSaveFailed, teamSelectSaveFailed, activeProfile,
        activeRegion.x, activeRegion.y, activeRegion.width, activeRegion.height
    ) {
        val indexText = "${matchRegions.indexOf(selectedRegion) + 1}/${matchRegions.size}"
        val sourceWidth = sourceFrameSize.width
        val sourceHeight = sourceFrameSize.height
        val x = (activeRegion.x * sourceWidth).toInt()
        val y = (activeRegion.y * sourceHeight).toInt()
        val width = (activeRegion.width * sourceWidth).toInt()
        val height = (activeRegion.height * sourceHeight).toInt()
        val selectedProfileSaved = if (selectedRegion.name.startsWith("TEAM_SELECT_")) {
            teamSelectProfileSaved
        } else {
            battleProfileSaved
        }
        val selectedSaveFailed = if (selectedRegion.name.startsWith("TEAM_SELECT_")) teamSelectSaveFailed else battleSaveFailed
        val saveStatus = when {
            selectedSaveFailed -> "SAVE FAILED"
            selectedProfileSaved -> "SAVED"
            else -> "DEFAULT"
        }
        onLcdUpdate(
            "${getReadableName(selectedRegion)} $indexText  X:$x Y:$y  $saveStatus",
            "W:$width H:$height / ${sourceWidth}×${sourceHeight}  ${mode.name}  [START] SAVE AS${activeProfile?.let { "  ${it.name}" } ?: ""}"
        )
    }

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text("SAVE CALIBRATION AS", color = TerminalGreen) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "DETECTED GUESS: $detectedDeviceModel  ${detectedScreenWidth}×${detectedScreenHeight}",
                        color = TerminalGreen,
                        fontSize = 12.sp,
                    )
                    activeProfile?.takeIf {
                        !it.deviceModel.equals(detectedDeviceModel, ignoreCase = true) ||
                            it.screenWidth != detectedScreenWidth ||
                            it.screenHeight != detectedScreenHeight
                    }?.let { current ->
                        Text(
                            "ACTIVE PROFILE: ${current.deviceModel}  ${current.screenWidth}×${current.screenHeight}",
                            color = Color(0xFFFFC857),
                            fontSize = 11.sp,
                        )
                    }
                    TextButton(onClick = ::useDetectedDeviceGuess) {
                        Text("USE DETECTED DEVICE", color = TerminalGreen)
                    }
                    TextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile name") },
                        colors = calibrationTextFieldColors()
                    )
                    TextField(
                        value = profileModel,
                        onValueChange = { profileModel = it },
                        label = { Text("Phone model") },
                        colors = calibrationTextFieldColors()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = profileWidth,
                            onValueChange = { profileWidth = it.filter(Char::isDigit) },
                            label = { Text("Width") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = calibrationTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = profileHeight,
                            onValueChange = { profileHeight = it.filter(Char::isDigit) },
                            label = { Text("Height") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = calibrationTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    profileSaveError?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = ::saveNamedProfile) { Text("SAVE", color = TerminalGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) { Text("CANCEL", color = TerminalGreen) }
            },
            containerColor = TerminalBlack
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
            val stroke = 1.dp.toPx()

            drawRect(
                color = color,
                topLeft = Offset(activeRegion.x * size.width, activeRegion.y * size.height),
                size = Size(activeRegion.width * size.width, activeRegion.height * size.height),
                style = Stroke(width = stroke)
            )
            if (selectedRegion == CalibrationRegion.TRAINER_INACTIVE_POKEMON) {
                val middleY = (activeRegion.y + activeRegion.height / 2f) * size.height
                drawLine(
                    color = color,
                    start = Offset(activeRegion.x * size.width, middleY),
                    end = Offset((activeRegion.x + activeRegion.width) * size.width, middleY),
                    strokeWidth = stroke,
                )
            }
        }
        
    }
}

@Composable
private fun calibrationTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = TerminalBlack,
    unfocusedContainerColor = TerminalBlack,
    focusedTextColor = TerminalGreen,
    unfocusedTextColor = TerminalGreen,
    focusedLabelColor = TerminalGreen,
    unfocusedLabelColor = TerminalGreen.copy(alpha = 0.75f),
    cursorColor = TerminalGreen
)
